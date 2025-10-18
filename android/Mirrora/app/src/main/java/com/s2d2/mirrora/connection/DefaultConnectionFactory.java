package com.s2d2.mirrora.connection;

import android.content.Context;

public class DefaultConnectionFactory implements ConnectionFactory {
    private static final DefaultConnectionFactory INSTANCE = new DefaultConnectionFactory();
    public static DefaultConnectionFactory getInstance() { return INSTANCE; }

    @Override
    public ConnectionController create(ConnectionRole role, ConnectionConfig cfg, Context ctx) {
        switch (role) {
            case SENDER:
                return new SenderController(ctx, cfg);
            case VIEWER:
                return new ViewerController(ctx, cfg);
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }
}