package com.ahvakra.camelintegration.fields.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface FieldDefinitionStore {
    List<FieldDefinition> findAll();
    Optional<FieldDefinition> findById(UUID id);
    void save(FieldDefinition definition);
    void delete(UUID id);
    <T> T withLockedDefinition(UUID id, Function<FieldDefinition, T> operation);
}
