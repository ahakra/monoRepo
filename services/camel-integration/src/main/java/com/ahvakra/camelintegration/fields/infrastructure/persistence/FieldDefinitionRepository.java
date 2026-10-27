package com.ahvakra.camelintegration.fields.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinitionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FieldDefinitionEntity f where f.id = :id")
    Optional<FieldDefinitionEntity> findLockedById(UUID id);

    List<FieldDefinitionEntity> findAllByOrderByUpdatedAtDesc();
}
