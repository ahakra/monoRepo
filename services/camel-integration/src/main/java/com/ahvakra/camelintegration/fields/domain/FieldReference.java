package com.ahvakra.camelintegration.fields.domain;

import java.util.Map;
import java.util.UUID;

public record FieldReference(UUID tableId, String tableName, Map<String, String> fields) {
    public FieldReference {
        fields = Map.copyOf(fields);
    }
}
