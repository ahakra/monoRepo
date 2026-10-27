package com.ahvakra.camelintegration.tables.application;

import java.util.List;
import java.util.UUID;

public record TableDefinitionCommand(
    String name,
    UUID connectionId,
    UUID fieldDefinitionId,
    String schemaName,
    String tableName,
    List<String> fields
) {}
