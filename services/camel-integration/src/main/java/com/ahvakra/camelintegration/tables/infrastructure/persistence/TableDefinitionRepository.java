package com.ahvakra.camelintegration.tables.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TableDefinitionRepository extends JpaRepository<TableDefinitionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TableDefinitionEntity t where t.id = :id")
    Optional<TableDefinitionEntity> findLockedById(UUID id);

    List<TableDefinitionEntity> findAllByOrderByUpdatedAtDesc();
    List<TableDefinitionEntity> findAllByFieldDefinitionId(UUID id);
}
