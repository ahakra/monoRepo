package com.ahvakra.camelintegration.workflows.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowStore {
    List<Workflow> findAll();
    Optional<Workflow> findById(UUID id);
    void save(Workflow workflow);
    void delete(UUID id);
}
