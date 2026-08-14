package com.haoleme.app;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** WebSocket-first event client with SSE and ordinary HTTP polling fallbacks. */
final class RealtimeSyncClient {
    interface Listener {
        void onEvent(String type, long revision, JSONObject payload);
        void onConnectionChanged(boolean connected);
    }

    private static final String TAG = "HaolemeRealtime";
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final OkHttpClient webSocketClient = new OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build();
    private volatile Thread worker;
    private volatile HttpURLConnection connection;
    private volatile WebSocket webSocket;
    private volatile String serverUrl = "";
    private volatile String token = "";
    private volatile long revision = 0L;

    RealtimeSyncClient(Listener listener) {
        this.listener = listener;
    }

    synchronized void start(String serverUrl, String token) {
        String cleanServer = serverUrl == null ? "" : serverUrl.trim().replaceAll("/+$", "");
        String cleanToken = token == null ? "" : token.trim();
        if (cleanServer.isEmpty() || cleanToken.isEmpty()) {
            stop();
            return;
        }
        if (running.get() && cleanServer.equals(this.serverUrl) && cleanToken.equals(this.token)) return;
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
        WebSocket activeSocket = webSocket;
        webSocket = null;
        if (activeSocket != null) activeSocket.close(1000, "app stopped");
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
        int webSocketFailures = 0;
        while (running.get()) {
            try {
                if (webSocketFailures < 3) {
                    listenWebSocketOnce();
                } else {
                    listenSseOnce();
                    webSocketFailures = 0;
                }
                backoffMs = 1000L;
            } catch (Exception error) {
                webSocketFailures++;
                if (running.get()) Log.d(TAG, "realtime disconnected: " + error.getMessage());
                if (webSocketFailures >= 3) {
                    try {
                        listenSseOnce();
                        webSocketFailures = 0;
                        backoffMs = 1000L;
                        continue;
                    } catch (Exception sseError) {
                        if (running.get()) Log.d(TAG, "SSE fallback disconnected: " + sseError.getMessage());
                    }
                }
            } finally {
                listener.onConnectionChanged(false);
                webSocket = null;
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

    private void listenWebSocketOnce() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        String wsBase = serverUrl.startsWith("https://")
                ? "wss://" + serverUrl.substring(8)
                : serverUrl.startsWith("http://")
                ? "ws://" + serverUrl.substring(7)
                : serverUrl;
        Request request = new Request.Builder()
                .url(wsBase + "/ws/app?since=" + revision)
                .header("Authorization", "Bearer " + token)
                .build();
        webSocket = webSocketClient.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket socket, Response response) {
                listener.onConnectionChanged(true);
            }

            @Override public void onMessage(WebSocket socket, String text) {
                acceptEvent(text);
            }

            @Override public void onClosing(WebSocket socket, int code, String reason) {
                socket.close(code, reason);
            }

            @Override public void onClosed(WebSocket socket, int code, String reason) {
                closed.countDown();
            }

            @Override public void onFailure(WebSocket socket, Throwable error, Response response) {
                closed.countDown();
            }
        });
        while (running.get() && !closed.await(1, TimeUnit.SECONDS)) {
            // OkHttp owns socket IO; this worker only controls reconnect lifecycle.
        }
        if (running.get()) throw new IllegalStateException("WebSocket closed");
    }

    private void listenSseOnce() throws Exception {
        URL url = new URL(serverUrl + "/api/stream?since=" + revision);
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
                if (line.startsWith("data:")) acceptEvent(line.substring(5).trim());
            }
        }
    }

    private void acceptEvent(String raw) {
        try {
            JSONObject event = new JSONObject(raw);
            long nextRevision = event.optLong("revision", revision);
            revision = Math.max(revision, nextRevision);
            listener.onEvent(event.optString("type", "refresh"), revision, event);
        } catch (Exception error) {
            Log.d(TAG, "invalid realtime event: " + error.getMessage());
        }
    }
}
