package com.ahvakra.camelintegration.mappers.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MapperDefinition(
    UUID id,
    String name,
    String description,
    String directory,
    String wasmFileName,
    List<MapperOutput> outputs,
    Instant createdAt,
    Instant updatedAt
) {
    public MapperDefinition {
        outputs = List.copyOf(outputs);
    }
}
