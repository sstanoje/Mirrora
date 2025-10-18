package com.s2d2.mirrora.connection;

import org.webrtc.PeerConnection;
import java.util.List;

public class ConnectionConfig {
    public final String roomCode;
    public final String signalingUrl;
    public final List<PeerConnection.IceServer> iceServers;

    private ConnectionConfig(Builder b) {
        this.roomCode = b.roomCode;
        this.signalingUrl = b.signalingUrl;
        this.iceServers = b.iceServers;
    }

    public static class Builder {
        private String roomCode;
        private String signalingUrl;
        private List<PeerConnection.IceServer> iceServers;

        public Builder roomCode(String v) { this.roomCode = v; return this; }
        public Builder signalingUrl(String v) { this.signalingUrl = v; return this; }
        public Builder iceServers(List<PeerConnection.IceServer> v) { this.iceServers = v; return this; }
        public ConnectionConfig build() { return new ConnectionConfig(this); }
    }
}