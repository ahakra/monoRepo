package com.ahvakra.camelintegration.connections.api;

import com.ahvakra.camelintegration.connections.application.ConnectionCommand;
import java.util.Map;

public record ConnectionRequest(
    String name,
    String type,
    Map<String, String> settings,
    String password
) {
    public ConnectionCommand toCommand() {
        return new ConnectionCommand(name, type, settings, password);
    }

    @Override
    public String toString() {
        return "ConnectionRequest[type=" + type + "]";
    }
}
