package com.ahvakra.camelintegration.connections.infrastructure.persistence;

import com.ahvakra.camelintegration.connections.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaConnectionStore implements ConnectionStore {

    private final ConnectionJpaRepository repository;

    public JpaConnectionStore(ConnectionJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ConnectionDefinition> findAll() {
        return repository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(ConnectionEntity::toDomain)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ConnectionDefinition> findById(UUID id) {
        return repository.findById(id).map(ConnectionEntity::toDomain);
    }

    public void save(ConnectionDefinition connection) {
        repository.save(ConnectionEntity.from(connection));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
