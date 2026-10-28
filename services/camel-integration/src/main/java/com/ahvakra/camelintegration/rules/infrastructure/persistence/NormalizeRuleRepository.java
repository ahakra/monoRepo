package com.ahvakra.camelintegration.rules.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizeRuleRepository extends JpaRepository<NormalizeRuleEntity, UUID> {
    List<NormalizeRuleEntity> findAllByOrderByUpdatedAtDesc();
    List<NormalizeRuleEntity> findAllByFieldDefinitionId(UUID id);
}
