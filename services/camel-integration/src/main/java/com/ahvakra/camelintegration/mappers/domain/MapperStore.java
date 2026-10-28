package com.ahvakra.camelintegration.mappers.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MapperStore {
    List<MapperDefinition> findAll();
    List<MapperDefinition> findByFieldDefinitionId(UUID id);
    Optional<MapperDefinition> findById(UUID id);
    void save(MapperDefinition mapper);
    void delete(UUID id);
}
