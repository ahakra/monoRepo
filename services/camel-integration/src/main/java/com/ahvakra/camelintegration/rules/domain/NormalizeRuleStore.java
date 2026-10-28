package com.ahvakra.camelintegration.rules.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NormalizeRuleStore {
    List<NormalizeRule> findAll();
    List<NormalizeRule> findByFieldDefinitionId(UUID id);
    Optional<NormalizeRule> findById(UUID id);
    void save(NormalizeRule rule);
    void delete(UUID id);
}
