package com.haoleme.app;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** SSE client with bounded reconnect backoff. HTTP polling remains the fallback. */
final class RealtimeSyncClient {
    interface Listener {
        void onEvent(String type, long revision, JSONObject payload);
        void onConnectionChanged(boolean connected);
    }

    private static final String TAG = "HaolemeRealtime";
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread worker;
    private volatile HttpURLConnection connection;
    private volatile String serverUrl = "";
    private volatile String token = "";
    private volatile long revision = 0L;

    RealtimeSyncClient(Listener listener) {
        this.listener = listener;
    }

    synchronized void start(String serverUrl, String token) {
        String cleanServer = serverUrl == null ? "" : serverUrl.trim();
        String cleanToken = token == null ? "" : token.trim();
        if (cleanServer.isEmpty() || cleanToken.isEmpty()) {
            stop();
            return;
        }
        if (running.get() && cleanServer.equals(this.serverUrl) && cleanToken.equals(this.token)) {
            return;
        }
        stop();
        this.serverUrl = cleanServer;
        this.token = cleanToken;
        this.revision = 0L;
        running.set(true);
        worker = new Thread(this::loop, "haoleme-realtime");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized void stop() {
        running.set(false);
        HttpURLConnection active = connection;
        connection = null;
        if (active != null) active.disconnect();
        Thread activeWorker = worker;
        worker = null;
        if (activeWorker != null) activeWorker.interrupt();
        listener.onConnectionChanged(false);
    }

    private void loop() {
        long backoffMs = 1000L;
        while (running.get()) {
            try {
                listenOnce();
                backoffMs = 1000L;
            } catch (Exception error) {
                if (running.get()) Log.d(TAG, "stream disconnected: " + error.getMessage());
            } finally {
                listener.onConnectionChanged(false);
                HttpURLConnection active = connection;
                connection = null;
                if (active != null) active.disconnect();
            }
            if (!running.get()) break;
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
            backoffMs = Math.min(backoffMs * 2L, 30000L);
        }
    }

    private void listenOnce() throws Exception {
        String separator = serverUrl.endsWith("/") ? "" : "/";
        URL url = new URL(serverUrl + separator + "api/stream?since=" + revision);
        HttpURLConnection request = (HttpURLConnection) url.openConnection();
        connection = request;
        request.setRequestMethod("GET");
        request.setConnectTimeout(10000);
        request.setReadTimeout(70000);
        request.setRequestProperty("Authorization", "Bearer " + token);
        request.setRequestProperty("Accept", "text/event-stream");
        request.setRequestProperty("Cache-Control", "no-cache");
        int status = request.getResponseCode();
        if (status == 404 || status == 405) {
            Thread.sleep(30000L);
            return;
        }
        if (status != 200) throw new IllegalStateException("HTTP " + status);
        listener.onConnectionChanged(true);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                request.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                JSONObject event = new JSONObject(line.substring(5).trim());
                long nextRevision = event.optLong("revision", revision);
                revision = Math.max(revision, nextRevision);
                listener.onEvent(event.optString("type", "refresh"), revision, event);
            }
        }
    }
}
