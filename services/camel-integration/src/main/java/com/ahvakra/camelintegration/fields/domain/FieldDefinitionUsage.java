package com.ahvakra.camelintegration.fields.domain;

import java.util.List;
import java.util.UUID;

public interface FieldDefinitionUsage {
    void validateUpdate(FieldDefinition definition);
    List<FieldReference> references(UUID id);
}
