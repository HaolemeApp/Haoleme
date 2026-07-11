package com.haoleme.app;

final class NotificationPolicy {
    private NotificationPolicy() {
    }

    static boolean isTerminal(String status) {
        return "succeeded".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }

    static boolean shouldSummarizeOffline(long lastSuccessfulPoll, long now, long pollInterval) {
        return lastSuccessfulPoll > 0L
                && now > lastSuccessfulPoll
                && now - lastSuccessfulPoll > Math.max(30000L, pollInterval * 3L);
    }

    static String aggregationKey(String project, String device) {
        String cleanProject = project == null ? "" : project.trim();
        if (!cleanProject.isEmpty()) {
            return "project:" + cleanProject;
        }
        String cleanDevice = device == null ? "" : device.trim();
        return "device:" + (cleanDevice.isEmpty() ? "unknown" : cleanDevice);
    }
}
