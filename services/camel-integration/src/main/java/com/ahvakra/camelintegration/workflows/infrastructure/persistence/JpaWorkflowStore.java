package com.ahvakra.camelintegration.workflows.infrastructure.persistence;

import com.ahvakra.camelintegration.workflows.domain.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Transactional
public class JpaWorkflowStore implements WorkflowStore {

    private final WorkflowRepository repository;
    private final JsonMapper json;

    public JpaWorkflowStore(WorkflowRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Workflow> findAll() {
        return repository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(item -> item.toDomain(json))
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Workflow> findById(UUID id) {
        return repository.findById(id).map(item -> item.toDomain(json));
    }

    public void save(Workflow workflow) {
        repository.saveAndFlush(WorkflowEntity.from(workflow, json));
    }

    public void delete(UUID id) {
        repository.deleteById(id);
        repository.flush();
    }
}
