package com.ahvakra.camelintegration.tables.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record TableDefinition(
    UUID id,
    String name,
    UUID connectionId,
    UUID fieldDefinitionId,
    String schemaName,
    String tableName,
    Map<String, String> columns,
    Instant createdAt,
    Instant updatedAt
) {
    public TableDefinition {
        columns = Collections.unmodifiableMap(new LinkedHashMap<>(columns));
    }
}
