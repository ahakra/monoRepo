package com.ahvakra.camelintegration.transformers.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransformerStore {
    List<TransformerDefinition> findAll();
    List<TransformerDefinition> findByFieldDefinitionId(UUID id);
    Optional<TransformerDefinition> findById(UUID id);
    void save(TransformerDefinition definition);
    void delete(UUID id);
}
