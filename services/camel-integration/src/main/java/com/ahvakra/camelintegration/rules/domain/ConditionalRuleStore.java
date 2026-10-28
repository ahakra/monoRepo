package com.ahvakra.camelintegration.rules.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConditionalRuleStore {
    List<ConditionalRule> findAll();
    List<ConditionalRule> findByFieldDefinitionId(UUID id);
    Optional<ConditionalRule> findById(UUID id);
    void save(ConditionalRule rule);
    void delete(UUID id);
}
