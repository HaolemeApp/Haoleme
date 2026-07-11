package com.haoleme.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HaolemeForegroundService extends Service {
    private static final String PREFS = "haoleme";
    private static final String RUN_CHANNEL_ID = "runs";
    private static final String MONITOR_CHANNEL_ID = "monitor";
    private static final String DEFAULT_SERVER_URL = BuildConfig.HAOLEME_DEFAULT_SERVER_URL;
    private static final String[] LEGACY_SERVER_URLS = new String[]{
            "http://api.haoleme.cloud"
    };
    private static final long POLL_MS = 7000L;
    private static final int HTTP_TIMEOUT_MS = 12000;
    private static final int FOREGROUND_ID = 7001;
    private static final String PREF_NOTIFY_SUCCESS = "notify_success";
    private static final String PREF_NOTIFY_FAILURE = "notify_failure";
    private static final String PREF_NOTIFY_MIN_SECONDS = "notify_min_seconds";
    private static final String PREF_NOTIFY_QUIET_HOURS = "notify_quiet_hours";
    private static final String PREF_MASK_SENSITIVE = "mask_sensitive";
    private static final String PREF_LANGUAGE_MODE = "language_mode";
    private static final String PREF_LATEST_EVENT = "notification_latest_event";
    private static final String PREF_LAST_SUCCESSFUL_POLL = "notification_last_successful_poll";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, String> knownStatuses = new HashMap<>();
    private final Map<String, Long> deviceLastReportTime = new HashMap<>();
    private final Map<String, Long> lastNonTerminalForRun = new HashMap<>();
    private final Map<String, Boolean> previousDeviceOnline = new HashMap<>();
    private final Set<String> devicesWithRecentActiveRuns = new HashSet<>();
    private SharedPreferences prefs;
    private volatile boolean destroyed;
    private long lastDeviceCheckTime = 0;
    private static final long DEVICE_CHECK_INTERVAL_MS = 30000; // 30 seconds to save battery/network
    private final long notificationSessionStartedAt = System.currentTimeMillis();
    private boolean firstLoad = true;
    private String latestEvent = "";

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollEvents();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        latestEvent = prefs.getString(PREF_LATEST_EVENT, "");
        createNotificationChannels();
        startForeground(FOREGROUND_ID, monitorNotification(isEnglish() ? "Monitoring command status" : "正在监控命令状态"));
        handler.post(pollRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void submitBackground(Runnable task) {
        if (destroyed || executor.isShutdown()) {
            return;
        }
        try {
            executor.submit(task);
        } catch (RejectedExecutionException ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void pollEvents() {
        String token = normalizedToken();
        if (token.isEmpty()) {
            return;
        }
        String url = normalizedServerUrl() + "/api/events?limit=100";
        if (!latestEvent.isEmpty()) {
            url += "&since=" + android.net.Uri.encode(latestEvent);
        }
        String requestUrl = url;
        submitBackground(() -> {
            try {
                long now = System.currentTimeMillis();
                long previousSuccess = prefs.getLong(PREF_LAST_SUCCESSFUL_POLL, 0L);
                boolean offlineCatchUp = NotificationPolicy.shouldSummarizeOffline(previousSuccess, now, POLL_MS);
                List<JSONObject> offlineRuns = new ArrayList<>();
                String body = httpGet(requestUrl, token);
                JSONObject payload = new JSONObject(body);
                JSONArray events = payload.optJSONArray("events");
                if (events == null) {
                    return;
                }
                String latest = payload.optString("latest", latestEvent);
                for (int i = 0; i < events.length(); i++) {
                    JSONObject run = events.optJSONObject(i);
                    if (run == null) {
                        continue;
                    }
                    run = decryptRun(run);
                    long reportNow = System.currentTimeMillis();
                    String did = run.optString("deviceId", "").trim();
                    if (!did.isEmpty()) {
                        long prev = deviceLastReportTime.getOrDefault(did, 0L);
                        long gap = reportNow - prev;
                        deviceLastReportTime.put(did, reportNow);
                        if (prev > 0L && gap > 5 * 60 * 1000) {
                            String dname = run.optString("deviceName", did);
                            sendDeviceReconnectNotification(did, dname, gap);
                        }
                    }
                    String rid = run.optString("id", "");
                    String rstatus = run.optString("status", "");
                    boolean rterm = "succeeded".equals(rstatus) || "failed".equals(rstatus) || "cancelled".equals(rstatus);
                    if (!rterm) {
                        lastNonTerminalForRun.put(rid, reportNow);
                    } else {
                        devicesWithRecentActiveRuns.remove(did);  // clean if terminal
                    }
                    if ("created".equals(rstatus) || "running".equals(rstatus)) {
                        devicesWithRecentActiveRuns.add(did);
                    }
                    maybeNotify(run, offlineCatchUp, offlineRuns);
                    String updated = run.optString("updatedAt", "");
                    if (updated.compareTo(latest) > 0) {
                        latest = updated;
                    }
                }
                if (!devicesWithRecentActiveRuns.isEmpty() || firstLoad) {
                    checkAndNotifyDeviceChanges();
                }
                if (!offlineRuns.isEmpty()) {
                    HaolemeNotificationCenter.notifyOfflineSummary(this, offlineRuns, now - previousSuccess, isEnglish());
                }
                latestEvent = latest == null ? "" : latest;
                prefs.edit()
                        .putString(PREF_LATEST_EVENT, latestEvent)
                        .putLong(PREF_LAST_SUCCESSFUL_POLL, System.currentTimeMillis())
                        .apply();
                if (firstLoad) {
                    firstLoad = false;
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void checkAndNotifyDeviceChanges() {
        long now = System.currentTimeMillis();
        if (now - lastDeviceCheckTime < DEVICE_CHECK_INTERVAL_MS) {
            return; // throttle to reduce battery and network usage
        }
        lastDeviceCheckTime = now;

        submitBackground(() -> {
            try {
                String token = normalizedToken();
                if (token.isEmpty()) return;
                String url = normalizedServerUrl() + "/api/devices";
                String body = httpGet(url, token);
                JSONArray devices = new JSONObject(body).getJSONArray("devices");
                for (int i = 0; i < devices.length(); i++) {
                    JSONObject dev = devices.optJSONObject(i);
                    if (dev == null) continue;
                    String id = dev.optString("id", "");
                    boolean online = dev.optBoolean("online", false);
                    String name = dev.optString("name", id);
                    Boolean prev = previousDeviceOnline.put(id, online);
                    if (prev == null) {
                        HaolemeNotificationCenter.cancelDeviceReconnect(this, id);
                    }
                    if (prev != null && prev != online) {
                        boolean hadActive = devicesWithRecentActiveRuns.contains(id);
                        if (!online) {
                            sendDeviceOfflineNotification(id, name, hadActive);
                        } else {
                            sendDeviceReconnectNotification(id, name, 0);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    // Decrypt the end-to-end-encrypted run fields (command, output, ...) so the
    // notification shows the real command instead of the "Encrypted command"
    // placeholder. Mirrors MainActivity.decryptRun, but never generates a key:
    // if no key is paired or decryption fails, the run is left as-is.
    private JSONObject decryptRun(JSONObject run) {
        JSONObject e2ee = run.optJSONObject("e2ee");
        if (e2ee == null || e2ee.optInt("v", 0) != 1) {
            return run;
        }
        byte[] key = accountKeyBytesOrNull();
        if (key == null) {
            return run;
        }
        try {
            byte[] nonce = base64UrlDecode(e2ee.optString("nonce", ""));
            byte[] ciphertext = base64UrlDecode(e2ee.optString("ciphertext", ""));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(run.optString("id", "").getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(ciphertext);
            JSONObject fields = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
            JSONObject copy = new JSONObject(run.toString());
            copy.put("commandText", fields.optString("commandText", copy.optString("commandText", "")));
            copy.put("cwd", fields.optString("cwd", copy.optString("cwd", "")));
            copy.put("stdoutTail", fields.optString("stdoutTail", ""));
            copy.put("stderrTail", fields.optString("stderrTail", ""));
            copy.put("outputTail", fields.optString("outputTail", ""));
            if (fields.has("command")) {
                copy.put("command", fields.optJSONArray("command"));
            }
            return copy;
        } catch (Exception ignored) {
            return run;
        }
    }

    private byte[] accountKeyBytesOrNull() {
        String saved = prefs.getString("encryption_key_b64", "");
        if (saved == null || saved.isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = base64UrlDecode(saved);
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private void maybeNotify(JSONObject run, boolean offlineCatchUp, List<JSONObject> offlineRuns) {
        String id = run.optString("id", "");
        String status = run.optString("status", "");
        if (id.isEmpty()) {
            return;
        }

        String previous = knownStatuses.put(id, status);
        if (previous != null && previous.equals(status)) {
            return;
        }

        if ("created".equals(status) || "running".equals(status)) {
            if (previous != null || !firstLoad) {
                HaolemeNotificationCenter.notifyRunning(this, run, isEnglish());
            }
            return;
        }
        boolean isTerminal = "succeeded".equals(status) || "failed".equals(status) || "cancelled".equals(status);
        if (!isTerminal) {
            return;
        }
        boolean wasRunning = "created".equals(previous) || "running".equals(previous);
        boolean completedDuringSession = runTerminalAtMillis(run) >= notificationSessionStartedAt;

        // Detect reconnect: if this terminal report comes long after last non-terminal for this run
        long lastNon = lastNonTerminalForRun.getOrDefault(id, notificationSessionStartedAt);
        long termTime = runTerminalAtMillis(run);
        boolean afterReconnect = (termTime - lastNon > 5 * 60 * 1000); // >5min gap

        if ((!wasRunning && !afterReconnect && !completedDuringSession) || (firstLoad && !completedDuringSession)) {
            return;
        }
        if (offlineCatchUp) {
            offlineRuns.add(run);
        } else {
            HaolemeNotificationCenter.notifyTerminal(this, run, isEnglish());
        }
    }

    private void sendDeviceReconnectNotification(String did, String dname, long gapMs) {
        HaolemeNotificationCenter.notifyDeviceStatus(this, did, dname, true, false, gapMs, isEnglish());
    }

    private void sendDeviceOfflineNotification(String did, String dname, boolean hadActive) {
        HaolemeNotificationCenter.notifyDeviceStatus(this, did, dname, false, hadActive, 0L, isEnglish());
    }

    private String notificationSummary(JSONObject run, String command, String status, String dev, String proj, String durStr, boolean afterReconnect) {
        StringBuilder sb = new StringBuilder();
        if (!dev.isEmpty()) sb.append("[").append(dev).append("]");
        if (!proj.isEmpty()) sb.append(" (").append(proj).append(")");
        if (sb.length() > 0) sb.append(" ");
        sb.append(command).append(durStr);

        if ("failed".equals(status) || "cancelled".equals(status)) {
            String latest = latestOutputLine(run);
            if (!latest.isEmpty()) {
                sb.append("\n").append(displayText(latest));
            }
            Object ec = run.opt("exitCode");
            if (ec != null) sb.append(" [exit=").append(ec).append("]");
        } else {
            // for success, perhaps last line too if useful
            String latest = latestOutputLine(run);
            if (!latest.isEmpty()) sb.append("\n").append(displayText(latest));
        }
        if (afterReconnect) {
            sb.append("\n(reported after device reconnect)");
        }
        return trim(sb.toString());
    }

    private String latestOutputLine(JSONObject run) {
        String output = run.optString("outputTail", "");
        if (output.isEmpty()) {
            output = run.optString("stderrTail", "");
        }
        if (output.isEmpty()) {
            output = run.optString("stdoutTail", "");
        }
        String[] lines = output.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return trim(line);
            }
        }
        return "";
    }

    private String trim(String value) {
        if (value.length() <= 240) {
            return value;
        }
        return value.substring(value.length() - 240);
    }

    private String displayText(String raw) {
        String value = raw == null ? "" : raw;
        return prefs.getBoolean(PREF_MASK_SENSITIVE, true) ? maskSensitive(value) : value;
    }

    private String maskSensitive(String raw) {
        String masked = raw == null ? "" : raw;
        masked = masked.replaceAll("(?i)(password|passwd|pwd|token|api[_-]?key|secret|access[_-]?key|authorization)(\\s*[:=]\\s*)([^\\s'\"&]+)", "$1$2••••");
        masked = masked.replaceAll("(?i)(--(?:password|passwd|pwd|token|api-key|api_key|secret|access-key|access_key)\\s+)(\\S+)", "$1••••");
        masked = masked.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1••••");
        return masked;
    }

    private long runDurationSeconds(JSONObject run) {
        long started = parseTimestamp(run.optString("startedAt", ""));
        long ended = runTerminalAtMillis(run);
        if (started <= 0 || ended <= 0 || ended < started) {
            return 0L;
        }
        return Math.max(0L, (ended - started) / 1000L);
    }

    private long runTerminalAtMillis(JSONObject run) {
        String endedRaw = run.optString("endedAt", "");
        return endedRaw.isEmpty() || "null".equals(endedRaw)
                ? parseTimestamp(run.optString("updatedAt", ""))
                : parseTimestamp(endedRaw);
    }

    private long parseTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equals(raw)) {
            return 0L;
        }
        String value = raw.trim();
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = format.parse(value);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private boolean isQuietHourNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 22 || hour < 8;
    }

    private android.app.Notification monitorNotification(String text) {
        return HaolemeNotificationCenter.monitorNotification(this, text, isEnglish());
    }

    private PendingIntent openAppIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private void createNotificationChannels() {
        HaolemeNotificationCenter.ensureChannels(this, isEnglish());
    }

    private boolean isEnglish() {
        return prefs != null && "en".equals(prefs.getString(PREF_LANGUAGE_MODE, "zh"));
    }

    private String appDisplayName() {
        return isEnglish() ? "Haoleme" : "好了么";
    }

    private String httpGet(String target, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        connection.setConnectTimeout(HTTP_TIMEOUT_MS);
        connection.setReadTimeout(HTTP_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        int code = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
        return body.toString();
    }

    private String normalizedServerUrl() {
        String raw = prefs.getString("server_url", DEFAULT_SERVER_URL);
        raw = raw == null ? "" : raw.trim();
        if (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        if (isLegacyServerUrl(raw)) {
            raw = DEFAULT_SERVER_URL;
            prefs.edit().putString("server_url", raw).apply();
        }
        if (raw.isEmpty()) {
            raw = DEFAULT_SERVER_URL;
        }
        return raw;
    }

    private boolean isLegacyServerUrl(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        for (String legacy : LEGACY_SERVER_URLS) {
            if (legacy.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalizedToken() {
        String token = prefs.getString("token", "");
        return token == null ? "" : token.trim();
    }
}
