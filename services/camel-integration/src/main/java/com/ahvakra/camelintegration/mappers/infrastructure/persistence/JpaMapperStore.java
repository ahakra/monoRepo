package com.ahvakra.camelintegration.mappers.infrastructure.persistence;

import com.ahvakra.camelintegration.mappers.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Transactional
public class JpaMapperStore implements MapperStore {

    private final MapperRepository repository;
    private final JsonMapper mapper;

    public JpaMapperStore(MapperRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<MapperDefinition> findAll() {
        return repository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(entity -> entity.toDomain(mapper))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MapperDefinition> findByFieldDefinitionId(UUID id) {
        return repository
            .findByFieldDefinitionId(id)
            .stream()
            .map(entity -> entity.toDomain(mapper))
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<MapperDefinition> findById(UUID id) {
        return repository.findById(id).map(entity -> entity.toDomain(mapper));
    }

    public void save(MapperDefinition definition) {
        // Remove old list-name slots before inserting replacements, so names can be swapped.
        // Both flushes remain inside the same transaction; failures restore the original outputs.
        repository.findById(definition.id()).ifPresent(existing -> {
            existing.clearOutputs();
            repository.flush();
        });
        repository.saveAndFlush(MapperDefinitionEntity.from(definition, mapper));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
        repository.flush();
    }
}
