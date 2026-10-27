package com.ahvakra.camelintegration.tables.domain;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;

public interface TableCreator {
    String sql(TableDefinition definition);
    void create(ConnectionDefinition connection, TableDefinition definition);
}
