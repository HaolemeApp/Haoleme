package com.haoleme.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Resolve a human-readable command from a run payload or a cached detail snapshot. */
final class RunCommandText {
    static final String ENCRYPTED_PLACEHOLDER = "Encrypted command";

    private RunCommandText() {
    }

    static boolean isUsable(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty()
                && !ENCRYPTED_PLACEHOLDER.equals(trimmed)
                && !trimmed.startsWith("Encrypted run.");
    }

    static String joinCommand(JSONObject run) {
        if (run == null) {
            return "";
        }
        JSONArray command = run.optJSONArray("command");
        if (command == null || command.length() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < command.length(); i++) {
            String part = String.valueOf(command.opt(i) == null ? "" : command.opt(i)).trim();
            if (part.isEmpty() || "null".equals(part)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(part);
        }
        return out.toString();
    }

    static String resolve(JSONObject run, JSONObject cached, String syncingLabel, String fallback) {
        String direct = run == null ? "" : run.optString("commandText", "");
        if (direct != null && direct.trim().startsWith("Encrypted run.")) {
            return direct.trim();
        }
        if (isUsable(direct)) {
            return direct.trim();
        }
        String joined = joinCommand(run);
        if (!joined.isEmpty()) {
            return joined;
        }
        if (cached != null) {
            String cachedText = cached.optString("commandText", "");
            if (isUsable(cachedText)) {
                return cachedText.trim();
            }
            String cachedJoin = joinCommand(cached);
            if (!cachedJoin.isEmpty()) {
                return cachedJoin;
            }
        }
        if (ENCRYPTED_PLACEHOLDER.equals(direct == null ? "" : direct.trim())
                || (run != null && (run.optBoolean("e2eeOmitted", false) || run.optJSONObject("e2ee") != null))) {
            return syncingLabel;
        }
        return fallback;
    }
}
