package com.ahvakra.camelintegration.transformers.infrastructure.persistence;

import com.ahvakra.camelintegration.transformers.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Transactional
public class JpaTransformerStore implements TransformerStore {

    private final TransformerRepository repository;
    private final JsonMapper mapper;

    public JpaTransformerStore(TransformerRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TransformerDefinition> findAll() {
        return repository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(entity -> entity.toDomain(mapper))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TransformerDefinition> findByFieldDefinitionId(UUID id) {
        return repository
            .findByFieldDefinitionId(id)
            .stream()
            .map(entity -> entity.toDomain(mapper))
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TransformerDefinition> findById(UUID id) {
        return repository.findById(id).map(entity -> entity.toDomain(mapper));
    }

    public void save(TransformerDefinition definition) {
        // Remove old port-name slots before inserting replacements, so names can be swapped.
        // Both flushes remain inside the same transaction; failures restore the original ports.
        repository.findById(definition.id()).ifPresent(existing -> {
            existing.clearPorts();
            repository.flush();
        });
        repository.saveAndFlush(TransformerDefinitionEntity.from(definition, mapper));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
        repository.flush();
    }
}
