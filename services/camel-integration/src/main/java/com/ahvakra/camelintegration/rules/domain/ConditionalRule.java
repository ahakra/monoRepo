package com.ahvakra.camelintegration.rules.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConditionalRule(
    UUID id,
    ConditionalSpec spec,
    Map<String, String> referencedFields,
    Instant createdAt,
    Instant updatedAt
) {
    public ConditionalRule {
        referencedFields = Map.copyOf(referencedFields);
    }
}
