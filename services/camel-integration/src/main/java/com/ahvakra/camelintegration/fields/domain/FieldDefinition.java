package com.ahvakra.camelintegration.fields.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record FieldDefinition(
    UUID id,
    String name,
    Map<String, String> fields,
    Instant createdAt,
    Instant updatedAt
) {
    public FieldDefinition {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
