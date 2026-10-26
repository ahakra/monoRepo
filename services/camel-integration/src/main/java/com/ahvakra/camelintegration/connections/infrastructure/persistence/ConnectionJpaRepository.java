package com.ahvakra.camelintegration.connections.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionJpaRepository extends JpaRepository<ConnectionEntity, UUID> {
    List<ConnectionEntity> findAllByOrderByUpdatedAtDesc();
}
