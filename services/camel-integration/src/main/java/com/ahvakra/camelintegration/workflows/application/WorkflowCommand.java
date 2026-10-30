package com.ahvakra.camelintegration.workflows.application;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

// Client timestamps are deliberately ignored. The server owns draft timestamps.
public record WorkflowCommand(
    UUID id,
    String name,
    String description,
    JsonNode nodes,
    JsonNode edges,
    JsonNode viewport
) {}
