package com.ahvakra.camelintegration.rules.infrastructure.persistence;

import com.ahvakra.camelintegration.rules.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaNormalizeRuleStore implements NormalizeRuleStore {

    private final NormalizeRuleRepository repository;

    public JpaNormalizeRuleStore(NormalizeRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NormalizeRule> findAll() {
        return repository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(NormalizeRuleEntity::toDomain)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<NormalizeRule> findByFieldDefinitionId(UUID id) {
        return repository
            .findAllByFieldDefinitionId(id)
            .stream()
            .map(NormalizeRuleEntity::toDomain)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<NormalizeRule> findById(UUID id) {
        return repository.findById(id).map(NormalizeRuleEntity::toDomain);
    }

    public void save(NormalizeRule rule) {
        repository.saveAndFlush(NormalizeRuleEntity.from(rule));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
        repository.flush();
    }
}
