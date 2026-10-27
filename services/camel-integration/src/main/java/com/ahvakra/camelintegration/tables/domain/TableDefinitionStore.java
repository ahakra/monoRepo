package com.ahvakra.camelintegration.tables.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface TableDefinitionStore {
    List<TableDefinition> findAll();
    List<TableDefinition> findByFieldDefinitionId(UUID id);
    Optional<TableDefinition> findById(UUID id);
    void save(TableDefinition definition);
    void delete(UUID id);
    <T> T withLockedDefinition(UUID id, Function<TableDefinition, T> operation);
}
