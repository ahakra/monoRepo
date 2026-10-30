package com.ahvakra.camelintegration.workflows.infrastructure.persistence;

import com.ahvakra.camelintegration.workflows.domain.Workflow;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@Entity
@Table(name = "etl_workflows")
public class WorkflowEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "nodes_json", nullable = false, columnDefinition = "text")
    private String nodes;

    @Column(name = "edges_json", nullable = false, columnDefinition = "text")
    private String edges;

    @Column(name = "viewport_json", nullable = false, columnDefinition = "text")
    private String viewport;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkflowEntity() {}

    static WorkflowEntity from(Workflow workflow, JsonMapper json) {
        var entity = new WorkflowEntity();
        entity.id = workflow.id();
        entity.name = workflow.name();
        entity.description = workflow.description();
        entity.nodes = json.writeValueAsString(workflow.nodes());
        entity.edges = json.writeValueAsString(workflow.edges());
        entity.viewport = json.writeValueAsString(workflow.viewport());
        entity.createdAt = workflow.createdAt();
        entity.updatedAt = workflow.updatedAt();
        return entity;
    }

    Workflow toDomain(JsonMapper json) {
        return new Workflow(
            id,
            name,
            description,
            json.readTree(nodes),
            json.readTree(edges),
            json.readTree(viewport),
            createdAt,
            updatedAt
        );
    }
}
