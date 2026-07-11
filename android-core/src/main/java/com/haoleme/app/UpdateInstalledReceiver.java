package com.haoleme.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class UpdateInstalledReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = HaolemeNotificationCenter.CHANNEL_UPDATES;
    private static final int NOTIFICATION_ID = 4701;
    private static final String PREFS = "haoleme";
    private static final String PREF_LANGUAGE_MODE = "language_mode";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        boolean english = isEnglish(context);
        HaolemeNotificationCenter.ensureChannels(context, english);
        createChannel(manager, english);

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        builder.setContentTitle(appDisplayName(english) + (english ? " updated" : " 已更新"))
                .setContentText(english ? "Tap to open the latest version." : "点击打开最新版本。")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static void createChannel(NotificationManager manager, boolean english) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                appDisplayName(english) + (english ? " updates" : " 更新"),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(english
                ? "Notifications after Haoleme updates are installed."
                : "好了么更新安装完成后的通知。");
        manager.createNotificationChannel(channel);
    }

    private static boolean isEnglish(Context context) {
        return "en".equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_LANGUAGE_MODE, "zh"));
    }

    private static String appDisplayName(boolean english) {
        return english ? "Haoleme" : "好了么";
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
        return PendingIntent.getActivity(context, 0, launch, flags);
    }
}
