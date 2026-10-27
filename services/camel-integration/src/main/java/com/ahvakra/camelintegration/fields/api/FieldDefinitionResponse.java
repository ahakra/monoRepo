package com.ahvakra.camelintegration.fields.api;

import com.ahvakra.camelintegration.fields.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FieldDefinitionResponse(
    UUID id,
    String name,
    Map<String, String> fields,
    Instant createdAt,
    Instant updatedAt,
    List<FieldReference> usedBy
) {
    public static FieldDefinitionResponse from(
        FieldDefinition definition,
        List<FieldReference> usedBy
    ) {
        return new FieldDefinitionResponse(
            definition.id(),
            definition.name(),
            definition.fields(),
            definition.createdAt(),
            definition.updatedAt(),
            usedBy
        );
    }
}
