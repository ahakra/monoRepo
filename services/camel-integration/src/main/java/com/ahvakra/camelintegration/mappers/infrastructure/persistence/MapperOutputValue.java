package com.ahvakra.camelintegration.mappers.infrastructure.persistence;

import com.ahvakra.camelintegration.mappers.domain.MapperOutput;
import jakarta.persistence.*;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Embeddable
public class MapperOutputValue {

    @Column(name = "list_name", nullable = false, length = 120)
    private String name;

    @Column(name = "field_definition_id", nullable = false)
    private UUID fieldDefinitionId;

    @Column(name = "fields_json", nullable = false, columnDefinition = "TEXT")
    private String fieldsJson;

    protected MapperOutputValue() {}

    public static MapperOutputValue from(MapperOutput output, JsonMapper mapper) {
        var value = new MapperOutputValue();
        value.name = output.name();
        value.fieldDefinitionId = output.fieldDefinitionId();
        value.fieldsJson = mapper.writeValueAsString(output.fields());
        return value;
    }

    public MapperOutput toDomain(JsonMapper mapper) {
        return new MapperOutput(
            name,
            fieldDefinitionId,
            mapper.readValue(fieldsJson, new TypeReference<Map<String, String>>() {})
        );
    }
}
