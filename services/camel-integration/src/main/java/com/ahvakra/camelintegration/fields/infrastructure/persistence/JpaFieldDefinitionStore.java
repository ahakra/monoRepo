package com.ahvakra.camelintegration.fields.infrastructure.persistence;

import com.ahvakra.camelintegration.fields.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaFieldDefinitionStore implements FieldDefinitionStore {

    private final FieldDefinitionRepository repository;

    public JpaFieldDefinitionStore(FieldDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<FieldDefinition> findAll() {
        return repository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(FieldDefinitionEntity::toDomain)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<FieldDefinition> findById(UUID id) {
        return repository.findById(id).map(FieldDefinitionEntity::toDomain);
    }

    public void save(FieldDefinition definition) {
        repository.save(FieldDefinitionEntity.from(definition));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
    }

    public <T> T withLockedDefinition(UUID id, Function<FieldDefinition, T> operation) {
        return operation.apply(
            repository.findLockedById(id).orElseThrow(FieldsNotFoundException::new).toDomain()
        );
    }
}
