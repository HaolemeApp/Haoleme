package com.haoleme.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction() == null ? "" : intent.getAction();
        String runId = intent.getStringExtra(HaolemeNotificationCenter.EXTRA_RUN_ID);
        String project = intent.getStringExtra(HaolemeNotificationCenter.EXTRA_PROJECT);
        String command = intent.getStringExtra(HaolemeNotificationCenter.EXTRA_COMMAND);

        if (HaolemeNotificationCenter.ACTION_MUTE_PROJECT.equals(action)) {
            if (project != null && !project.trim().isEmpty()) {
                HaolemeNotificationCenter.muteProject(context, project);
                boolean english = "en".equals(context.getSharedPreferences("haoleme", Context.MODE_PRIVATE)
                        .getString("language_mode", "zh"));
                Toast.makeText(context,
                        english ? "Muted notifications for " + project : "已静音项目 " + project,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        HaolemeNotificationCenter.markViewed(context, runId);
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch.putExtra(HaolemeNotificationCenter.EXTRA_RUN_ID, runId == null ? "" : runId);
        launch.putExtra(HaolemeNotificationCenter.EXTRA_COMMAND, command == null ? "" : command);
        launch.putExtra("notification_action", action);
        context.startActivity(launch);
    }
}
