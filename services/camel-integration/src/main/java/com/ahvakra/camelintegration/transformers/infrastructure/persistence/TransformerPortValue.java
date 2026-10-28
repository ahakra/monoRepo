package com.ahvakra.camelintegration.transformers.infrastructure.persistence;

import com.ahvakra.camelintegration.transformers.domain.*;
import jakarta.persistence.*;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Embeddable
public class TransformerPortValue {

    @Column(name = "port_name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "port_kind", nullable = false, length = 30)
    private TransformerPortKind kind;

    @Column(name = "field_definition_id")
    private UUID fieldDefinitionId;

    @Column(name = "fields_json", nullable = false, columnDefinition = "TEXT")
    private String fieldsJson;

    protected TransformerPortValue() {}

    public static TransformerPortValue from(TransformerPort port, JsonMapper mapper) {
        var value = new TransformerPortValue();
        value.name = port.name();
        value.kind = port.kind();
        value.fieldDefinitionId = port.fieldDefinitionId();
        value.fieldsJson = mapper.writeValueAsString(port.fields());
        return value;
    }

    public TransformerPort toDomain(JsonMapper mapper) {
        return new TransformerPort(
            name,
            kind,
            fieldDefinitionId,
            mapper.readValue(fieldsJson, new TypeReference<Map<String, String>>() {})
        );
    }
}
