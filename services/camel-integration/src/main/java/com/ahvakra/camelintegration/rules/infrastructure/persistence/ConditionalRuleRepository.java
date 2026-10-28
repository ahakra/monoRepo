package com.ahvakra.camelintegration.rules.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConditionalRuleRepository extends JpaRepository<ConditionalRuleEntity, UUID> {
    List<ConditionalRuleEntity> findAllByOrderByUpdatedAtDesc();
    List<ConditionalRuleEntity> findAllByFieldDefinitionId(UUID id);
}
