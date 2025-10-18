package com.s2d2.mirrora.connection;


public interface ConnectionController {
    void start();
    void stop();
    boolean isConnected();
}