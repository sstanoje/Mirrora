package com.s2d2.mirrora.connection;

import android.content.Context;

public interface ConnectionFactory {
    ConnectionController create(ConnectionRole role, ConnectionConfig config, Context ctx);
}