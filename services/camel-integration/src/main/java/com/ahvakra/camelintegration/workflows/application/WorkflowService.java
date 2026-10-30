package com.ahvakra.camelintegration.workflows.application;

import com.ahvakra.camelintegration.workflows.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WorkflowService {

    private final WorkflowStore store;
    private final WorkflowValidator validator;

    public WorkflowService(WorkflowStore store, WorkflowValidator validator) {
        this.store = store;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<Workflow> list() {
        return store.findAll();
    }

    @Transactional(readOnly = true)
    public Workflow get(UUID id) {
        return store
            .findById(id)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found.")
            );
    }

    public Workflow save(UUID id, WorkflowCommand command) {
        var graph = validator.validate(command);
        var existing = id == null ? null : get(id);
        if (id != null && command.id() != null && !id.equals(command.id())) {
            throw new IllegalArgumentException("Workflow ID does not match the URL.");
        }
        UUID workflowId = id != null ? id : command.id() != null ? command.id() : UUID.randomUUID();
        if (id == null && store.findById(workflowId).isPresent()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This workflow already exists. Refresh before updating it."
            );
        }
        var now = Instant.now();
        var workflow = new Workflow(
            workflowId,
            command.name().trim(),
            command.description().trim(),
            graph.nodes(),
            graph.edges(),
            graph.viewport(),
            existing == null ? now : existing.createdAt(),
            now
        );
        store.save(workflow);
        return workflow;
    }

    public void delete(UUID id) {
        get(id);
        store.delete(id);
    }
}
