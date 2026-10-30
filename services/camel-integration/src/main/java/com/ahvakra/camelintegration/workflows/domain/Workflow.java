package com.ahvakra.camelintegration.workflows.domain;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record Workflow(
    UUID id,
    String name,
    String description,
    JsonNode nodes,
    JsonNode edges,
    JsonNode viewport,
    Instant createdAt,
    Instant updatedAt
) {}
