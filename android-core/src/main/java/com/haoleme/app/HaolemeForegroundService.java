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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HaolemeForegroundService extends Service {
    private static final String PREFS = "haoleme";
    private static final String RUN_CHANNEL_ID = "runs";
    private static final String MONITOR_CHANNEL_ID = "monitor";
    private static final String DEFAULT_SERVER_URL = "https://your-haoleme-server.example.com";
    private static final String[] LEGACY_SERVER_URLS = new String[]{
            "http://your-haoleme-server.example.com"
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, String> knownStatuses = new HashMap<>();
    private final Map<String, Long> deviceLastReportTime = new HashMap<>();
    private final Map<String, Long> lastNonTerminalForRun = new HashMap<>();
    private final Map<String, Boolean> previousDeviceOnline = new HashMap<>();
    private final Set<String> devicesWithRecentActiveRuns = new HashSet<>();
    private SharedPreferences prefs;
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
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
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
        executor.submit(() -> {
            try {
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
                    String did = run.optString("deviceId", "").trim();
                    if (!did.isEmpty()) {
                        long now = System.currentTimeMillis();
                        long prev = deviceLastReportTime.getOrDefault(did, 0L);
                        long gap = now - prev;
                        deviceLastReportTime.put(did, now);
                        if (gap > 5 * 60 * 1000) {
                            String dname = run.optString("deviceName", did);
                            sendDeviceReconnectNotification(did, dname, gap);
                        }
                    }
                    String rid = run.optString("id", "");
                    String rstatus = run.optString("status", "");
                    boolean rterm = "succeeded".equals(rstatus) || "failed".equals(rstatus) || "cancelled".equals(rstatus);
                    if (!rterm) {
                        lastNonTerminalForRun.put(rid, System.currentTimeMillis());
                    } else {
                        devicesWithRecentActiveRuns.remove(did);  // clean if terminal
                    }
                    if ("created".equals(rstatus) || "running".equals(rstatus)) {
                        devicesWithRecentActiveRuns.add(did);
                    }
                    maybeNotify(run);
                    String updated = run.optString("updatedAt", "");
                    if (updated.compareTo(latest) > 0) {
                        latest = updated;
                    }
                }
                if (!devicesWithRecentActiveRuns.isEmpty() || firstLoad) {
                    checkAndNotifyDeviceChanges();
                }
                latestEvent = latest == null ? "" : latest;
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

        executor.submit(() -> {
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

    private void maybeNotify(JSONObject run) {
        String id = run.optString("id", "");
        String status = run.optString("status", "");
        if (id.isEmpty()) {
            return;
        }

        String previous = knownStatuses.put(id, status);
        if (previous != null && previous.equals(status)) {
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
        if ("succeeded".equals(status) && !prefs.getBoolean(PREF_NOTIFY_SUCCESS, true)) {
            return;
        }
        if (("failed".equals(status) || "cancelled".equals(status)) && !prefs.getBoolean(PREF_NOTIFY_FAILURE, true)) {
            return;
        }
        if (prefs.getInt(PREF_NOTIFY_MIN_SECONDS, 0) > 0 && runDurationSeconds(run) < prefs.getInt(PREF_NOTIFY_MIN_SECONDS, 0)) {
            return;
        }
        if (prefs.getBoolean(PREF_NOTIFY_QUIET_HOURS, false) && isQuietHourNow()) {
            return;
        }

        String notifyKey = "notified_terminal_" + id;
        if (status.equals(prefs.getString(notifyKey, ""))) {
            return;
        }
        sendRunNotification(run);
        prefs.edit().putString(notifyKey, status).apply();
    }

    private void sendRunNotification(JSONObject run) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, RUN_CHANNEL_ID)
                : new android.app.Notification.Builder(this);
        String command = displayText(run.optString("commandText", "Command"));
        String status = run.optString("status", "finished");
        String dev = run.optString("deviceName", run.optString("deviceId", ""));
        String proj = run.optString("project", "");
        long dur = runDurationSeconds(run);
        String durStr = dur > 0 ? " • " + dur + "s" : "";

        // compute reconnect flag again for title
        String rid = run.optString("id", "");
        long lastNon = lastNonTerminalForRun.getOrDefault(rid, notificationSessionStartedAt);
        long termTime = runTerminalAtMillis(run);
        boolean afterReconnect = (termTime - lastNon > 5 * 60 * 1000);

        String title = appDisplayName() + ": " + status;
        if (!dev.isEmpty()) title += " on " + dev;
        if (afterReconnect) title += " (reconnected)";
        String summary = notificationSummary(run, command, status, dev, proj, durStr, afterReconnect);

        builder.setContentTitle(title)
                .setContentText(summary)
                .setSmallIcon("succeeded".equals(status) ? android.R.drawable.stat_sys_upload_done : android.R.drawable.stat_notify_error)
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setCategory("succeeded".equals(status) ? Notification.CATEGORY_STATUS : Notification.CATEGORY_ERROR)
                .setGroup("haoleme-runs");

        if (Build.VERSION.SDK_INT >= 21) {
            builder.setColor("succeeded".equals(status) ? 0xFF22C55E : 0xFFEF4444);
        }

        // Rich notification for better visibility of output etc.
        Notification.BigTextStyle bigStyle = new Notification.BigTextStyle()
                .bigText(summary)
                .setBigContentTitle(title);
        builder.setStyle(bigStyle);

        manager.notify(run.optString("id", command).hashCode(), builder.build());
    }

    private void sendDeviceReconnectNotification(String did, String dname, long gapMs) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        long mins = gapMs / 60000;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, MONITOR_CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle(appDisplayName() + ": Device reconnected")
         .setContentText(dname + " back after ~" + mins + " min. Checking runs...")
         .setSmallIcon(android.R.drawable.stat_notify_sync)
         .setContentIntent(openAppIntent())
         .setAutoCancel(true)
         .setGroup("haoleme-devices");
        manager.notify(("reconnect-" + did).hashCode(), b.build());
    }

    private void sendDeviceOfflineNotification(String did, String dname, boolean hadActive) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String text = dname + " is now offline";
        if (hadActive) text += " (had active runs)";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, MONITOR_CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle(appDisplayName() + ": Device offline")
         .setContentText(text)
         .setSmallIcon(android.R.drawable.stat_notify_error)
         .setContentIntent(openAppIntent())
         .setAutoCancel(true)
         .setGroup("haoleme-devices");
        manager.notify(("offline-" + did).hashCode(), b.build());
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
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, MONITOR_CHANNEL_ID)
                : new android.app.Notification.Builder(this);
        return builder.setContentTitle(appDisplayName())
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(openAppIntent())
                .setOngoing(true)
                .build();
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
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel monitor = new NotificationChannel(
                MONITOR_CHANNEL_ID,
                appDisplayName() + (isEnglish() ? " monitor" : " 监控"),
                NotificationManager.IMPORTANCE_LOW
        );
        monitor.setDescription(isEnglish()
                ? "Keeps Haoleme watching command status in the background."
                : "让好了么在后台监控命令状态。");
        manager.createNotificationChannel(monitor);

        NotificationChannel runs = new NotificationChannel(
                RUN_CHANNEL_ID,
                "Command runs",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        runs.setDescription(isEnglish()
                ? "Notifications when Haoleme commands finish."
                : "好了么命令结束时发送通知。");
        manager.createNotificationChannel(runs);
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
        try {
            URL url = new URL(value);
            int port = url.getPort();
            boolean defaultPort = port == -1 || port == 80 || port == 443;
            boolean basePath = url.getPath().isEmpty() || "/".equals(url.getPath());
            boolean webScheme = "http".equalsIgnoreCase(url.getProtocol()) || "https".equalsIgnoreCase(url.getProtocol());
            if (webScheme && defaultPort && basePath && url.getQuery() == null && url.getRef() == null
                    && isPublicIpv4Host(url.getHost())) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isPublicIpv4Host(String host) {
        String[] parts = host == null ? new String[0] : host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        try {
            for (int index = 0; index < parts.length; index++) {
                octets[index] = Integer.parseInt(parts[index]);
                if (octets[index] < 0 || octets[index] > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        int first = octets[0];
        int second = octets[1];
        return first > 0 && first < 224
                && first != 10 && first != 127
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 168);
    }

    private String normalizedToken() {
        String token = prefs.getString("token", "");
        return token == null ? "" : token.trim();
    }
}
