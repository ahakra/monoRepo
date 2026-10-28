package com.ahvakra.camelintegration.connections.api;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConnectionResponse(
    UUID id,
    String name,
    String type,
    Map<String, String> settings,
    boolean hasPassword,
    Instant createdAt,
    Instant updatedAt
) {
    public static ConnectionResponse from(ConnectionDefinition connection) {
        return new ConnectionResponse(
            connection.id(),
            connection.name(),
            connection.type(),
            connection.settings(),
            !connection.password().isEmpty(),
            connection.createdAt(),
            connection.updatedAt()
        );
    }
}
