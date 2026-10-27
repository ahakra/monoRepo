package com.ahvakra.camelintegration.tables.domain;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;

public interface TableSchemaManager {
    TableSchemaPlan preview(ConnectionDefinition connection, TableDefinition definition);
    void apply(
        ConnectionDefinition connection,
        TableDefinition definition,
        SchemaChangeSelection selection
    );
}
