package com.ahvakra.camelintegration.tables.api;

import com.ahvakra.camelintegration.tables.application.TableDefinitionCommand;
import java.util.List;
import java.util.UUID;

public record TableDefinitionRequest(
    String name,
    UUID connectionId,
    UUID fieldDefinitionId,
    String schemaName,
    String tableName,
    List<String> fields
) {
    public TableDefinitionCommand toCommand() {
        return new TableDefinitionCommand(
            name,
            connectionId,
            fieldDefinitionId,
            schemaName,
            tableName,
            fields
        );
    }
}
