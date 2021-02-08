package com.yamanoglu.connectedclipboard;

import java.io.Serializable;
import java.util.Objects;

public class RoomModal implements Serializable {
    private String name;
    private String ip;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomModal roomModal = (RoomModal) o;
        return getIp().equals(roomModal.getIp());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIp());
    }
}
