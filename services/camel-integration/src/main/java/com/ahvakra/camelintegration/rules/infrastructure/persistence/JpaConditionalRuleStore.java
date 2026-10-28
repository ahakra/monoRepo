package com.ahvakra.camelintegration.rules.infrastructure.persistence;

import com.ahvakra.camelintegration.rules.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaConditionalRuleStore implements ConditionalRuleStore {

    private final ConditionalRuleRepository repository;
    private final tools.jackson.databind.json.JsonMapper mapper;

    public JpaConditionalRuleStore(
        ConditionalRuleRepository repository,
        tools.jackson.databind.json.JsonMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ConditionalRule> findAll() {
        return repository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(entity -> entity.toDomain(mapper))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ConditionalRule> findByFieldDefinitionId(UUID id) {
        return repository
            .findAllByFieldDefinitionId(id)
            .stream()
            .map(entity -> entity.toDomain(mapper))
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ConditionalRule> findById(UUID id) {
        return repository.findById(id).map(entity -> entity.toDomain(mapper));
    }

    public void save(ConditionalRule rule) {
        repository.saveAndFlush(ConditionalRuleEntity.from(rule, mapper));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
        repository.flush();
    }
}
