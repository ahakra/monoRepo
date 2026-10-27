package com.ahvakra.camelintegration.fields.infrastructure.persistence;

import com.ahvakra.camelintegration.fields.domain.FieldDefinition;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "etl_field_definitions")
public class FieldDefinitionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "etl_field_entries", joinColumns = @JoinColumn(name = "definition_id"))
    @MapKeyColumn(name = "field_key", length = 63)
    @Column(name = "field_type", nullable = false, length = 20)
    private Map<String, String> fields = new HashMap<>();

    protected FieldDefinitionEntity() {}

    public static FieldDefinitionEntity from(FieldDefinition definition) {
        var entity = new FieldDefinitionEntity();
        entity.id = definition.id();
        entity.name = definition.name();
        entity.createdAt = definition.createdAt();
        entity.updatedAt = definition.updatedAt();
        entity.fields = new HashMap<>(definition.fields());
        return entity;
    }

    public FieldDefinition toDomain() {
        var ordered = new LinkedHashMap<String, String>();
        ordered.put("id", fields.get("id"));
        ordered.put("datetime", fields.get("datetime"));
        fields
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return new FieldDefinition(id, name, ordered, createdAt, updatedAt);
    }
}
