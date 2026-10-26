package com.ahvakra.camelintegration.connections.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectionStore {
    List<ConnectionDefinition> findAll();
    Optional<ConnectionDefinition> findById(UUID id);
    void save(ConnectionDefinition connection);
    void delete(UUID id);
}
