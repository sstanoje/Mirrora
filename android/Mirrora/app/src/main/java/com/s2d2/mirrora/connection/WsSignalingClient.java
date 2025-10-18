package com.s2d2.mirrora.connection;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WsSignalingClient {

    public enum Role { SENDER, VIEWER }

    public interface Listener {
        void onOpen();
        void onJoined(String room, int peers);
        void onReady(String room);
        void onPeerLeft();
        void onError(String reason);
        void onClosed();
        void onOffer(String room, String sdp);
        void onAnswer(String room, String sdp);
        void onIce(IceCandidate c);

    }

    private final OkHttpClient http = new OkHttpClient();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String url;
    private final String room;
    private final Listener listener;
    private final Role role;

    private WebSocket ws;

    public WsSignalingClient(String url, String room, Listener listener, Role role) {
        this.url = url;
        this.room = room;
        this.listener = listener;
        this.role = role;
    }

    public void connect() {
        Request req = new Request.Builder().url(url).build();
        ws = http.newWebSocket(req, new WS());
    }

    public void close() {
        if (ws != null) {
            ws.close(1000, "bye");
        }
    }

    private void sendJson(JSONObject obj) {
        if (ws != null) ws.send(obj.toString());
    }

    private final class WS extends WebSocketListener {
        @Override public void onOpen(@NonNull WebSocket webSocket, @NonNull okhttp3.Response response) {
            post(listener::onOpen);
            try {
                String roleStr = (role == Role.SENDER) ? "sender" : "viewer";
                JSONObject join = new JSONObject()
                        .put("type", "join")
                        .put("room", room)
                        .put("role", roleStr);
                sendJson(join);
            } catch (JSONException ignore) {}
        }

        @Override public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            try {
                JSONObject msg = new JSONObject(text);
                String type = msg.optString("type", "");
                switch (type) {
                    case "joined":
                        post(() -> listener.onJoined(msg.optString("room", ""),
                                msg.optInt("peers", 1)));
                        break;
                    case "ready":
                        post(() -> listener.onReady(msg.optString("room", "")));
                        break;
                    case "offer": {
                        String sdp = msg.optString("sdp", "");
                        String r   = msg.optString("room", "");
                        post(() -> listener.onOffer(r, sdp));
                        break;
                    }
                    case "answer": {
                        String sdp = msg.optString("sdp", "");
                        String r   = msg.optString("room", "");
                        if (role == Role.SENDER) {
                            post(() -> listener.onAnswer(r, sdp));
                        }
                        break;
                    }
                    case "ice": {
                        JSONObject cand = msg.optJSONObject("candidate");
                        if (cand != null) {
                            IceCandidate c = new IceCandidate(
                                    cand.optString("sdpMid", null),
                                    cand.optInt("sdpMLineIndex", 0),
                                    cand.optString("candidate", "")
                            );
                            post(() -> listener.onIce(c));
                        }
                        break;
                    }
                    case "peer-left":
                        post(listener::onPeerLeft);
                        break;
                    case "error":
                        post(() -> listener.onError(msg.optString("reason", "unknown")));
                        break;
                    default:
                        break;
                }
            } catch (JSONException e) {
                post(() -> listener.onError("invalid-json"));
            }
        }

        @Override public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            post(listener::onClosed);
        }

        @Override public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, okhttp3.Response r) {
            post(() -> listener.onError(t.getMessage() != null ? t.getMessage() : "ws-failure"));
            post(listener::onClosed);
        }
    }

    private void post(Runnable r) { main.post(r); }

    public void sendOffer(String room, String sdp) {
        try {
            org.json.JSONObject msg = new org.json.JSONObject()
                    .put("type", "offer")
                    .put("room", room)
                    .put("sdp", sdp);
            ws.send(msg.toString());
        } catch (org.json.JSONException ignore) {}
    }

    public void sendIce(String room, org.webrtc.IceCandidate c) {
        try {
            org.json.JSONObject cand = new org.json.JSONObject()
                    .put("candidate", c.sdp)
                    .put("sdpMid", c.sdpMid)
                    .put("sdpMLineIndex", c.sdpMLineIndex);
            org.json.JSONObject msg = new org.json.JSONObject()
                    .put("type", "ice")
                    .put("room", room)
                    .put("candidate", cand);
            ws.send(msg.toString());
        } catch (org.json.JSONException ignore) {}
    }

    public void sendAnswer(String room, String sdp) {
        try {
            org.json.JSONObject msg = new org.json.JSONObject()
                    .put("type", "answer")
                    .put("room", room)
                    .put("sdp", sdp);
            ws.send(msg.toString());
        } catch (org.json.JSONException ignore) {}
    }
}
