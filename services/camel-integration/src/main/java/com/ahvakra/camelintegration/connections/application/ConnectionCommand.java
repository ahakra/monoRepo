package com.ahvakra.camelintegration.connections.application;

import java.util.Map;

public record ConnectionCommand(
    String name,
    String type,
    Map<String, String> settings,
    String password
) {
    @Override
    public String toString() {
        return "ConnectionCommand[type=" + type + "]";
    }
}
