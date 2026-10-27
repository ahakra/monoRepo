package com.ahvakra.camelintegration.tables.api;

import com.ahvakra.camelintegration.tables.domain.TableDefinition;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TableDefinitionResponse(
    UUID id,
    String name,
    UUID connectionId,
    UUID fieldDefinitionId,
    String schemaName,
    String tableName,
    Map<String, String> columns,
    Instant createdAt,
    Instant updatedAt,
    String sql
) {
    public static TableDefinitionResponse from(TableDefinition definition, String sql) {
        return new TableDefinitionResponse(
            definition.id(),
            definition.name(),
            definition.connectionId(),
            definition.fieldDefinitionId(),
            definition.schemaName(),
            definition.tableName(),
            definition.columns(),
            definition.createdAt(),
            definition.updatedAt(),
            sql
        );
    }
}
