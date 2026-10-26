package com.ahvakra.camelintegration.connections.domain;

import java.util.Map;
import java.util.UUID;

public record ConnectionDefinition(
    UUID id,
    String name,
    String type,
    Map<String, String> settings,
    String password
) {
    public ConnectionDefinition {
        settings = Map.copyOf(settings);
    }

    @Override
    public String toString() {
        return "ConnectionDefinition[id=" + id + ", type=" + type + "]";
    }
}
