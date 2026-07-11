package com.haoleme.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationPolicyTest {
    @Test
    public void recognizesTerminalStatuses() {
        assertTrue(NotificationPolicy.isTerminal("succeeded"));
        assertTrue(NotificationPolicy.isTerminal("failed"));
        assertTrue(NotificationPolicy.isTerminal("cancelled"));
        assertFalse(NotificationPolicy.isTerminal("running"));
    }

    @Test
    public void requiresARealOfflineGapBeforeSummarizing() {
        assertFalse(NotificationPolicy.shouldSummarizeOffline(0L, 120000L, 7000L));
        assertFalse(NotificationPolicy.shouldSummarizeOffline(100000L, 125000L, 7000L));
        assertTrue(NotificationPolicy.shouldSummarizeOffline(100000L, 131000L, 7000L));
    }

    @Test
    public void groupsByProjectBeforeDevice() {
        assertEquals("project:Trainer", NotificationPolicy.aggregationKey(" Trainer ", "Mac"));
        assertEquals("device:Mac", NotificationPolicy.aggregationKey("", "Mac"));
        assertEquals("device:unknown", NotificationPolicy.aggregationKey(null, ""));
    }
}
