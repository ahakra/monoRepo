package com.ahvakra.camelintegration.tables.infrastructure.persistence;

import com.ahvakra.camelintegration.tables.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaTableDefinitionStore implements TableDefinitionStore {

    private final TableDefinitionRepository repository;

    public JpaTableDefinitionStore(TableDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TableDefinition> findAll() {
        return repository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(TableDefinitionEntity::toDomain)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TableDefinition> findByFieldDefinitionId(UUID id) {
        return repository
            .findAllByFieldDefinitionId(id)
            .stream()
            .map(TableDefinitionEntity::toDomain)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TableDefinition> findById(UUID id) {
        return repository.findById(id).map(TableDefinitionEntity::toDomain);
    }

    public void save(TableDefinition definition) {
        repository.saveAndFlush(TableDefinitionEntity.from(definition));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
        repository.flush();
    }

    public <T> T withLockedDefinition(UUID id, Function<TableDefinition, T> operation) {
        return operation.apply(
            repository.findLockedById(id).orElseThrow(TableNotFoundException::new).toDomain()
        );
    }
}
