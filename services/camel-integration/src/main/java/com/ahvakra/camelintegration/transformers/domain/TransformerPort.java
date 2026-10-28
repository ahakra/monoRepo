package com.ahvakra.camelintegration.transformers.domain;

import java.util.Map;
import java.util.UUID;

public record TransformerPort(
    String name,
    TransformerPortKind kind,
    UUID fieldDefinitionId,
    Map<String, String> fields
) {
    public TransformerPort {
        fields = Map.copyOf(fields);
    }
}
