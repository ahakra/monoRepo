package com.ahvakra.camelintegration.tables.domain;

import java.util.List;

public record TableSchemaPlan(String fingerprint, List<SchemaChange> changes) {
    public TableSchemaPlan {
        changes = List.copyOf(changes);
    }
}
