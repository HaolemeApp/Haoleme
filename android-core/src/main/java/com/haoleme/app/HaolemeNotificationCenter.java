package com.haoleme.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class HaolemeNotificationCenter {
    static final String CHANNEL_RUNNING = "runs_running_v2";
    static final String CHANNEL_SUCCESS = "runs_success_v2";
    static final String CHANNEL_FAILURE = "runs_failure_v2";
    static final String CHANNEL_DEVICES = "device_status_v2";
    static final String CHANNEL_SECURITY = "security_v2";
    static final String CHANNEL_UPDATES = "updates_v2";
    static final String CHANNEL_MONITOR = "monitor";

    static final String ACTION_OPEN_CONSOLE = "com.haoleme.app.action.OPEN_CONSOLE";
    static final String ACTION_CONFIRM_INTERRUPT = "com.haoleme.app.action.CONFIRM_INTERRUPT";
    static final String ACTION_MUTE_PROJECT = "com.haoleme.app.action.MUTE_PROJECT";
    static final String EXTRA_RUN_ID = "notification_run_id";
    static final String EXTRA_PROJECT = "notification_project";
    static final String EXTRA_COMMAND = "notification_command";

    static final String PREF_MUTED_PROJECTS = "muted_notification_projects";
    private static final String PREFS = "haoleme";
    private static final String PREF_NOTIFY_SUCCESS = "notify_success";
    private static final String PREF_NOTIFY_FAILURE = "notify_failure";
    private static final String PREF_NOTIFY_MIN_SECONDS = "notify_min_seconds";
    private static final String PREF_NOTIFY_QUIET_HOURS = "notify_quiet_hours";
    private static final String PREF_MASK_SENSITIVE = "mask_sensitive";
    private static final String PREF_DELIVERY_PREFIX = "notification_delivery_";
    private static final String PREF_BUCKET_PREFIX = "notification_bucket_";
    private static final long AGGREGATE_WINDOW_MS = 2 * 60 * 1000L;
    private static final int OFFLINE_SUMMARY_ID = 7401;
    private static final Object LOCK = new Object();

    private HaolemeNotificationCenter() {
    }

    static void ensureChannels(Context context, boolean english) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = manager(context);
        if (manager == null) {
            return;
        }
        createChannel(manager, CHANNEL_MONITOR,
                english ? "Background monitor" : "后台监控",
                english ? "Keeps Haoleme monitoring command status." : "让好了么持续监控命令状态。",
                NotificationManager.IMPORTANCE_LOW);
        createChannel(manager, CHANNEL_RUNNING,
                english ? "Running commands" : "运行中的命令",
                english ? "Ongoing commands with console and stop actions." : "正在运行的命令，可查看控制台或终止。",
                NotificationManager.IMPORTANCE_LOW);
        createChannel(manager, CHANNEL_SUCCESS,
                english ? "Succeeded commands" : "成功运行",
                english ? "Notifications for commands that finish successfully." : "命令成功结束时发送通知。",
                NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(manager, CHANNEL_FAILURE,
                english ? "Failed commands" : "失败运行",
                english ? "Notifications for failed or cancelled commands." : "命令失败或取消时发送通知。",
                NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(manager, CHANNEL_DEVICES,
                english ? "Device status" : "设备状态",
                english ? "Device offline and reconnect alerts." : "设备离线和恢复连接提醒。",
                NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(manager, CHANNEL_SECURITY,
                english ? "Security and pairing" : "安全与配对",
                english ? "Pairing, shared-space and security alerts." : "配对、共享空间和安全提醒。",
                NotificationManager.IMPORTANCE_DEFAULT);
        createChannel(manager, CHANNEL_UPDATES,
                english ? "App updates" : "应用更新",
                english ? "Update availability and installation results." : "应用更新与安装结果。",
                NotificationManager.IMPORTANCE_DEFAULT);
    }

    private static void createChannel(NotificationManager manager, String id, String name, String description, int importance) {
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description);
        manager.createNotificationChannel(channel);
    }

    static void notifyRunning(Context context, JSONObject run, boolean english) {
        String runId = run.optString("id", "").trim();
        if (runId.isEmpty()) {
            return;
        }
        String project = run.optString("project", "").trim();
        if (isProjectMuted(context, project)) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        synchronized (LOCK) {
            if (prefs.getBoolean("notified_running_" + runId, false)) {
                return;
            }
            if (!prefs.edit().putBoolean("notified_running_" + runId, true).commit()) {
                return;
            }
        }
        ensureChannels(context, english);
        String command = displayText(prefs, run.optString("commandText", english ? "Command" : "命令"));
        String device = run.optString("deviceName", run.optString("deviceId", "")).trim();
        int notificationId = runningNotificationId(runId);
        String deliveryKey = "running:" + runId;
        recordGenerated(context, deliveryKey, "running", runId, project);
        if (!canPost(context)) {
            recordBlocked(context, deliveryKey, "permission");
            return;
        }

        Notification.Builder builder = builder(context, CHANNEL_RUNNING)
                .setContentTitle(english ? "Running on " + fallback(device, "device") : fallback(device, "设备") + " 正在运行")
                .setContentText(command)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(actionIntent(context, ACTION_OPEN_CONSOLE, runId, project, command))
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setGroup("haoleme-running")
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_view,
                        english ? "Console" : "控制台",
                        actionIntent(context, ACTION_OPEN_CONSOLE, runId, project, command)).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        english ? "Stop" : "终止",
                        actionIntent(context, ACTION_CONFIRM_INTERRUPT, runId, project, command)).build());
        if (!project.isEmpty()) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_lock_silent_mode,
                    english ? "Mute project" : "静音项目",
                    actionIntent(context, ACTION_MUTE_PROJECT, runId, project, command)).build());
        }
        applyPrivateLockScreen(context, builder, english);
        post(context, notificationId, builder.build(), deliveryKey);
    }

    static void notifyTerminal(Context context, JSONObject run, boolean english) {
        String runId = run.optString("id", "").trim();
        String status = run.optString("status", "").trim();
        if (runId.isEmpty() || !NotificationPolicy.isTerminal(status)) {
            return;
        }
        cancelRunning(context, runId);
        if (!shouldNotify(context, run, status) || !claimTerminal(context, runId, status)) {
            return;
        }
        ensureChannels(context, english);
        SharedPreferences prefs = prefs(context);
        String project = run.optString("project", "").trim();
        String device = run.optString("deviceName", run.optString("deviceId", "")).trim();
        String command = displayText(prefs, run.optString("commandText", english ? "Command" : "命令"));
        String latest = displayText(prefs, latestOutputLine(run));
        String bucketKey = NotificationPolicy.aggregationKey(project, device);
        int notificationId = ("terminal-group:" + bucketKey).hashCode();
        String deliveryKey = "terminal:" + runId;
        recordGenerated(context, deliveryKey, "terminal", runId, project);
        if (!canPost(context)) {
            recordBlocked(context, deliveryKey, "permission");
            return;
        }

        JSONObject bucket = updateBucket(context, bucketKey, runId, status, command, latest);
        int total = bucket.optInt("total", 1);
        int failed = bucket.optInt("failed", 0);
        int succeeded = bucket.optInt("succeeded", 0);
        String label = project.isEmpty() ? fallback(device, english ? "Device" : "设备") : project;
        String title;
        if (total == 1) {
            title = statusTitle(english, status, device);
        } else {
            title = english
                    ? label + ": " + total + " commands finished"
                    : label + "：" + total + " 个任务已结束";
        }
        String summary = total == 1
                ? command + (latest.isEmpty() ? "" : "\n" + latest)
                : (english
                ? succeeded + " succeeded · " + failed + " failed"
                : succeeded + " 个成功 · " + failed + " 个失败");

        Notification.InboxStyle style = new Notification.InboxStyle().setBigContentTitle(title);
        JSONArray lines = bucket.optJSONArray("lines");
        if (lines != null) {
            for (int i = 0; i < lines.length(); i++) {
                style.addLine(lines.optString(i, ""));
            }
        }
        Notification.Builder builder = builder(context, failed > 0 ? CHANNEL_FAILURE : CHANNEL_SUCCESS)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(style)
                .setSmallIcon(failed > 0 ? android.R.drawable.stat_notify_error : android.R.drawable.stat_sys_upload_done)
                .setContentIntent(actionIntent(context, ACTION_OPEN_CONSOLE, runId, project, command))
                .setAutoCancel(true)
                .setOnlyAlertOnce(total > 1)
                .setCategory(failed > 0 ? Notification.CATEGORY_ERROR : Notification.CATEGORY_STATUS)
                .setGroup("haoleme-runs")
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_view,
                        english ? "Console" : "控制台",
                        actionIntent(context, ACTION_OPEN_CONSOLE, runId, project, command)).build());
        if (!project.isEmpty()) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_lock_silent_mode,
                    english ? "Mute project" : "静音项目",
                    actionIntent(context, ACTION_MUTE_PROJECT, runId, project, command)).build());
        }
        applyPrivateLockScreen(context, builder, english);
        post(context, notificationId, builder.build(), deliveryKey);
    }

    static void notifyOfflineSummary(Context context, List<JSONObject> runs, long gapMs, boolean english) {
        if (runs == null || runs.isEmpty()) {
            return;
        }
        List<JSONObject> accepted = new ArrayList<>();
        for (JSONObject run : runs) {
            String id = run.optString("id", "").trim();
            String status = run.optString("status", "").trim();
            cancelRunning(context, id);
            if (!id.isEmpty() && NotificationPolicy.isTerminal(status) && shouldNotify(context, run, status) && claimTerminal(context, id, status)) {
                accepted.add(run);
            }
        }
        if (accepted.isEmpty()) {
            return;
        }
        ensureChannels(context, english);
        int success = 0;
        int failed = 0;
        JSONObject latest = accepted.get(accepted.size() - 1);
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (JSONObject run : accepted) {
            String status = run.optString("status", "");
            if ("succeeded".equals(status)) {
                success++;
            } else {
                failed++;
            }
            String command = displayText(prefs(context), run.optString("commandText", english ? "Command" : "命令"));
            style.addLine(("succeeded".equals(status) ? "✓ " : "! ") + trim(command, 80));
            recordGenerated(context, "terminal:" + run.optString("id", ""), "offline-summary", run.optString("id", ""), run.optString("project", ""));
        }
        String title = english
                ? accepted.size() + " commands finished while offline"
                : "离线期间有 " + accepted.size() + " 个任务结束";
        String text = english
                ? success + " succeeded · " + failed + " failed · offline " + Math.max(1, gapMs / 60000) + " min"
                : success + " 个成功 · " + failed + " 个失败 · 断开约 " + Math.max(1, gapMs / 60000) + " 分钟";
        style.setBigContentTitle(title).setSummaryText(text);
        String latestId = latest.optString("id", "");
        String latestProject = latest.optString("project", "");
        String deliveryKey = "offline:" + System.currentTimeMillis();
        recordGenerated(context, deliveryKey, "offline-summary", latestId, latestProject);
        if (!canPost(context)) {
            recordBlocked(context, deliveryKey, "permission");
            return;
        }
        Notification.Builder builder = builder(context, failed > 0 ? CHANNEL_FAILURE : CHANNEL_SUCCESS)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(style)
                .setSmallIcon(failed > 0 ? android.R.drawable.stat_notify_error : android.R.drawable.stat_sys_upload_done)
                .setContentIntent(actionIntent(context, ACTION_OPEN_CONSOLE, latestId, latestProject, ""))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setGroup("haoleme-runs");
        applyPrivateLockScreen(context, builder, english);
        post(context, OFFLINE_SUMMARY_ID, builder.build(), deliveryKey);
        for (JSONObject run : accepted) {
            String runDeliveryKey = "terminal:" + run.optString("id", "");
            updateDelivery(context, runDeliveryKey, "sent", "");
            updateDelivery(context, runDeliveryKey, "received", "");
        }
    }

    static void notifyDeviceStatus(Context context, String deviceId, String deviceName, boolean online, boolean hadActive, long gapMs, boolean english) {
        ensureChannels(context, english);
        String deliveryKey = "device:" + deviceId + ":" + online;
        recordGenerated(context, deliveryKey, "device", "", "");
        if (!canPost(context)) {
            recordBlocked(context, deliveryKey, "permission");
            return;
        }
        String title = online
                ? (english ? "Device reconnected" : "设备已恢复在线")
                : (english ? "Device offline" : "设备已离线");
        String text;
        if (online) {
            text = english
                    ? deviceName + " is back online" + (gapMs > 0 ? " after about " + Math.max(1, gapMs / 60000) + " min" : "")
                    : deviceName + " 已恢复在线" + (gapMs > 0 ? "，离线约 " + Math.max(1, gapMs / 60000) + " 分钟" : "");
        } else {
            text = english
                    ? deviceName + " is offline" + (hadActive ? " with active commands" : "")
                    : deviceName + " 已离线" + (hadActive ? "，离线前有运行中的任务" : "");
        }
        Notification.Builder builder = builder(context, CHANNEL_DEVICES)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(online ? android.R.drawable.stat_notify_sync : android.R.drawable.stat_notify_error)
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setGroup("haoleme-devices");
        applyPrivateLockScreen(context, builder, english);
        post(context, ((online ? "reconnect:" : "offline:") + deviceId).hashCode(), builder.build(), deliveryKey);
    }

    static void notifySecurity(Context context, String title, String text, boolean english) {
        ensureChannels(context, english);
        String deliveryKey = "security:" + System.currentTimeMillis();
        recordGenerated(context, deliveryKey, "security", "", "");
        if (!canPost(context)) {
            recordBlocked(context, deliveryKey, "permission");
            return;
        }
        Notification.Builder builder = builder(context, CHANNEL_SECURITY)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS);
        applyPrivateLockScreen(context, builder, english);
        post(context, deliveryKey.hashCode(), builder.build(), deliveryKey);
    }

    static Notification monitorNotification(Context context, String text, boolean english) {
        ensureChannels(context, english);
        Notification.Builder builder = builder(context, CHANNEL_MONITOR)
                .setContentTitle(appName(english))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(openAppIntent(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        applyPrivateLockScreen(context, builder, english);
        return builder.build();
    }

    static void markViewed(Context context, String runId) {
        if (runId == null || runId.trim().isEmpty()) {
            return;
        }
        updateDelivery(context, "running:" + runId, "viewed", "");
        updateDelivery(context, "terminal:" + runId, "viewed", "");
    }

    static boolean isProjectMuted(Context context, String project) {
        if (project == null || project.trim().isEmpty()) {
            return false;
        }
        return prefs(context).getStringSet(PREF_MUTED_PROJECTS, new HashSet<>()).contains(project.trim());
    }

    static void muteProject(Context context, String project) {
        if (project == null || project.trim().isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            Set<String> muted = new HashSet<>(prefs(context).getStringSet(PREF_MUTED_PROJECTS, new HashSet<>()));
            muted.add(project.trim());
            prefs(context).edit().putStringSet(PREF_MUTED_PROJECTS, muted).commit();
        }
        NotificationManager manager = manager(context);
        if (manager != null) {
            manager.cancel(("terminal-group:project:" + project.trim()).hashCode());
        }
    }

    static Set<String> mutedProjects(Context context) {
        return new HashSet<>(prefs(context).getStringSet(PREF_MUTED_PROJECTS, new HashSet<>()));
    }

    static void unmuteProject(Context context, String project) {
        synchronized (LOCK) {
            Set<String> muted = new HashSet<>(prefs(context).getStringSet(PREF_MUTED_PROJECTS, new HashSet<>()));
            muted.remove(project);
            prefs(context).edit().putStringSet(PREF_MUTED_PROJECTS, muted).commit();
        }
    }

    static String deliveryHealth(Context context, boolean english) {
        NotificationManager manager = manager(context);
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return english ? "Permission required" : "需要通知权限";
        }
        if (manager != null && Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) {
            return english ? "Disabled by system" : "已被系统关闭";
        }
        int blocked = 0;
        int viewed = 0;
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            if (!entry.getKey().startsWith(PREF_DELIVERY_PREFIX) || !(entry.getValue() instanceof String)) {
                continue;
            }
            try {
                JSONObject record = new JSONObject((String) entry.getValue());
                if ("blocked".equals(record.optString("state", ""))) {
                    blocked++;
                }
                if (record.optLong("viewedAt", 0L) > 0L) {
                    viewed++;
                }
            } catch (Exception ignored) {
            }
        }
        if (blocked > 0) {
            return english ? blocked + " blocked" : blocked + " 条曾被拦截";
        }
        return english ? "Healthy · " + viewed + " viewed" : "正常 · " + viewed + " 条已查看";
    }

    static void cancelRunning(Context context, String runId) {
        if (runId == null || runId.isEmpty()) {
            return;
        }
        NotificationManager manager = manager(context);
        if (manager != null) {
            manager.cancel(runningNotificationId(runId));
        }
        prefs(context).edit().remove("notified_running_" + runId).apply();
    }

    static void cancelDeviceReconnect(Context context, String deviceId) {
        NotificationManager manager = manager(context);
        if (manager != null && deviceId != null && !deviceId.isEmpty()) {
            manager.cancel(("reconnect:" + deviceId).hashCode());
        }
    }

    private static boolean shouldNotify(Context context, JSONObject run, String status) {
        SharedPreferences prefs = prefs(context);
        if ("succeeded".equals(status) && !prefs.getBoolean(PREF_NOTIFY_SUCCESS, true)) {
            return false;
        }
        if (("failed".equals(status) || "cancelled".equals(status)) && !prefs.getBoolean(PREF_NOTIFY_FAILURE, true)) {
            return false;
        }
        String project = run.optString("project", "").trim();
        if (isProjectMuted(context, project)) {
            return false;
        }
        int minSeconds = prefs.getInt(PREF_NOTIFY_MIN_SECONDS, 0);
        if (minSeconds > 0 && runDurationSeconds(run) < minSeconds) {
            return false;
        }
        return !prefs.getBoolean(PREF_NOTIFY_QUIET_HOURS, false) || !isQuietHourNow();
    }

    private static boolean claimTerminal(Context context, String runId, String status) {
        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            String key = "notified_terminal_" + runId;
            if (status.equals(prefs.getString(key, ""))) {
                return false;
            }
            return prefs.edit().putString(key, status).commit();
        }
    }

    private static JSONObject updateBucket(Context context, String bucketKey, String runId, String status, String command, String latest) {
        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            String key = PREF_BUCKET_PREFIX + Integer.toHexString(bucketKey.hashCode());
            long now = System.currentTimeMillis();
            JSONObject bucket;
            try {
                bucket = new JSONObject(prefs.getString(key, "{}"));
            } catch (Exception ignored) {
                bucket = new JSONObject();
            }
            if (now - bucket.optLong("updatedAt", 0L) > AGGREGATE_WINDOW_MS) {
                bucket = new JSONObject();
            }
            JSONArray lines = bucket.optJSONArray("lines");
            if (lines == null) {
                lines = new JSONArray();
            }
            JSONArray updatedLines = new JSONArray();
            String line = ("succeeded".equals(status) ? "✓ " : "! ") + trim(command, 90);
            if (!latest.isEmpty()) {
                line += " · " + trim(latest, 80);
            }
            updatedLines.put(line);
            for (int i = 0; i < Math.min(4, lines.length()); i++) {
                updatedLines.put(lines.optString(i, ""));
            }
            try {
                bucket.put("updatedAt", now);
                bucket.put("total", bucket.optInt("total", 0) + 1);
                bucket.put("succeeded", bucket.optInt("succeeded", 0) + ("succeeded".equals(status) ? 1 : 0));
                bucket.put("failed", bucket.optInt("failed", 0) + ("succeeded".equals(status) ? 0 : 1));
                bucket.put("latestRunId", runId);
                bucket.put("lines", updatedLines);
                prefs.edit().putString(key, bucket.toString()).commit();
            } catch (Exception ignored) {
            }
            return bucket;
        }
    }

    private static void post(Context context, int id, Notification notification, String deliveryKey) {
        NotificationManager manager = manager(context);
        if (manager == null) {
            recordBlocked(context, deliveryKey, "manager");
            return;
        }
        try {
            manager.notify(id, notification);
            updateDelivery(context, deliveryKey, "sent", "");
            if (Build.VERSION.SDK_INT >= 23) {
                for (StatusBarNotification active : manager.getActiveNotifications()) {
                    if (active.getId() == id) {
                        updateDelivery(context, deliveryKey, "received", "");
                        break;
                    }
                }
            } else {
                updateDelivery(context, deliveryKey, "received", "");
            }
        } catch (SecurityException exception) {
            recordBlocked(context, deliveryKey, "security");
        }
    }

    private static void recordGenerated(Context context, String key, String type, String runId, String project) {
        synchronized (LOCK) {
            JSONObject record = deliveryRecord(context, key);
            try {
                record.put("type", type);
                record.put("runId", runId == null ? "" : runId);
                record.put("project", project == null ? "" : project);
                record.put("generatedAt", System.currentTimeMillis());
                record.put("state", "generated");
                saveDeliveryRecord(context, key, record);
            } catch (Exception ignored) {
            }
        }
    }

    private static void recordBlocked(Context context, String key, String reason) {
        updateDelivery(context, key, "blocked", reason);
    }

    private static void updateDelivery(Context context, String key, String state, String reason) {
        synchronized (LOCK) {
            JSONObject record = deliveryRecord(context, key);
            long now = System.currentTimeMillis();
            try {
                record.put("state", state);
                if (!reason.isEmpty()) {
                    record.put("reason", reason);
                }
                if ("sent".equals(state)) {
                    record.put("sentAt", now);
                } else if ("received".equals(state)) {
                    record.put("receivedAt", now);
                } else if ("viewed".equals(state)) {
                    record.put("viewedAt", now);
                }
                saveDeliveryRecord(context, key, record);
            } catch (Exception ignored) {
            }
        }
    }

    private static JSONObject deliveryRecord(Context context, String key) {
        try {
            return new JSONObject(prefs(context).getString(PREF_DELIVERY_PREFIX + Integer.toHexString(key.hashCode()), "{}"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static void saveDeliveryRecord(Context context, String key, JSONObject record) {
        prefs(context).edit().putString(PREF_DELIVERY_PREFIX + Integer.toHexString(key.hashCode()), record.toString()).commit();
    }

    private static PendingIntent actionIntent(Context context, String action, String runId, String project, String command) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_RUN_ID, runId == null ? "" : runId);
        intent.putExtra(EXTRA_PROJECT, project == null ? "" : project);
        intent.putExtra(EXTRA_COMMAND, command == null ? "" : command);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, (action + ":" + runId + ":" + project).hashCode(), intent, flags);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) {
            launch = new Intent(context, MainActivity.class);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 7010, launch, flags);
    }

    private static void applyPrivateLockScreen(Context context, Notification.Builder builder, boolean english) {
        builder.setVisibility(Notification.VISIBILITY_PRIVATE);
        Notification publicVersion = builder(context, CHANNEL_MONITOR)
                .setContentTitle(appName(english))
                .setContentText(english ? "Open Haoleme to view command details." : "打开好了么查看任务详情。")
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentIntent(openAppIntent(context))
                .build();
        builder.setPublicVersion(publicVersion);
    }

    private static Notification.Builder builder(Context context, String channel) {
        return Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = manager(context);
        return manager != null && (Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled());
    }

    private static int runningNotificationId(String runId) {
        return ("running:" + runId).hashCode();
    }

    private static String statusTitle(boolean english, String status, String device) {
        String state;
        if ("succeeded".equals(status)) {
            state = english ? "Command succeeded" : "任务运行成功";
        } else if ("cancelled".equals(status)) {
            state = english ? "Command cancelled" : "任务已终止";
        } else {
            state = english ? "Command failed" : "任务运行失败";
        }
        return device == null || device.isEmpty() ? state : state + " · " + device;
    }

    private static String latestOutputLine(JSONObject run) {
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
                return line;
            }
        }
        return "";
    }

    private static String displayText(SharedPreferences prefs, String raw) {
        String value = raw == null ? "" : raw;
        if (!prefs.getBoolean(PREF_MASK_SENSITIVE, true)) {
            return value;
        }
        value = value.replaceAll("(?i)(password|passwd|pwd|token|api[_-]?key|secret|access[_-]?key|authorization)(\\s*[:=]\\s*)([^\\s'\"&]+)", "$1$2••••");
        value = value.replaceAll("(?i)(--(?:password|passwd|pwd|token|api-key|api_key|secret|access-key|access_key)\\s+)(\\S+)", "$1••••");
        return value.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1••••");
    }

    private static long runDurationSeconds(JSONObject run) {
        long started = parseIso(run.optString("startedAt", ""));
        long ended = parseIso(run.optString("endedAt", run.optString("updatedAt", "")));
        if (started <= 0 || ended < started) {
            return 0;
        }
        return (ended - started) / 1000L;
    }

    private static long parseIso(String raw) {
        if (raw == null || raw.isEmpty() || "null".equals(raw)) {
            return 0L;
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                return java.time.Instant.parse(raw).toEpochMilli();
            }
        } catch (Exception ignored) {
        }
        String normalized = raw.replaceAll("\\.\\d+Z$", "Z");
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date parsed = format.parse(normalized);
            return parsed == null ? 0L : parsed.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static boolean isQuietHourNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 22 || hour < 8;
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String trim(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String appName(boolean english) {
        return english ? "Haoleme" : "好了么";
    }
}
