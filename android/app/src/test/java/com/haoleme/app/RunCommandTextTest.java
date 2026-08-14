package com.haoleme.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunCommandTextTest {
    @Test
    public void prefersPlainCommandText() throws Exception {
        JSONObject run = new JSONObject().put("commandText", "hao python train.py");
        assertEquals("hao python train.py", RunCommandText.resolve(run, null, "syncing", "unknown"));
    }

    @Test
    public void joinsCommandArrayWhenTextMissing() throws Exception {
        JSONObject run = new JSONObject()
                .put("command", new JSONArray().put("python").put("train.py"))
                .put("commandText", "");
        assertEquals("python train.py", RunCommandText.resolve(run, null, "syncing", "unknown"));
    }

    @Test
    public void usesCachedCommandWhenLivePayloadIsEncryptedPlaceholder() throws Exception {
        JSONObject run = new JSONObject().put("commandText", "Encrypted command");
        JSONObject cached = new JSONObject().put("commandText", "echo hello");
        assertEquals("echo hello", RunCommandText.resolve(run, cached, "syncing", "unknown"));
    }

    @Test
    public void showsSyncingWhenEncryptedCommandHasNoCache() throws Exception {
        JSONObject run = new JSONObject().put("commandText", "Encrypted command");
        assertEquals("syncing", RunCommandText.resolve(run, null, "syncing", "unknown"));
    }

    @Test
    public void emptyRunFallsBackToUnknown() {
        assertEquals("unknown", RunCommandText.resolve(null, null, "syncing", "unknown"));
        assertFalse(RunCommandText.isUsable(""));
        assertFalse(RunCommandText.isUsable("Encrypted command"));
        assertTrue(RunCommandText.isUsable("ls"));
    }
}
