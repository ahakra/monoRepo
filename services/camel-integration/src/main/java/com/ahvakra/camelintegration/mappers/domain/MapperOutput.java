package com.ahvakra.camelintegration.mappers.domain;

import java.util.Map;
import java.util.UUID;

public record MapperOutput(String name, UUID fieldDefinitionId, Map<String, String> fields) {
    public MapperOutput {
        fields = Map.copyOf(fields);
    }
}
