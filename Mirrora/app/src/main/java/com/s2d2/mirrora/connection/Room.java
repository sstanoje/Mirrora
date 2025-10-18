package com.s2d2.mirrora.connection;

import java.io.Serializable;

public class Room implements Serializable {
    private String roomNumber;

    public Room(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

}
