package com.ahvakra.camelintegration.mappers.infrastructure.persistence;

import com.ahvakra.camelintegration.mappers.domain.MapperDefinition;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@Entity
@Table(name = "etl_mappers")
public class MapperDefinitionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 2048)
    private String directory;

    @Column(name = "wasm_file_name", nullable = false, length = 255)
    private String wasmFileName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "etl_mapper_outputs", joinColumns = @JoinColumn(name = "mapper_id"))
    @OrderColumn(name = "output_order")
    private List<MapperOutputValue> outputs = new ArrayList<>();

    protected MapperDefinitionEntity() {}

    void clearOutputs() {
        outputs.clear();
    }

    public static MapperDefinitionEntity from(MapperDefinition definition, JsonMapper mapper) {
        var entity = new MapperDefinitionEntity();
        entity.id = definition.id();
        entity.name = definition.name();
        entity.description = definition.description();
        entity.directory = definition.directory();
        entity.wasmFileName = definition.wasmFileName();
        entity.createdAt = definition.createdAt();
        entity.updatedAt = definition.updatedAt();
        entity.outputs = new ArrayList<>(
            definition
                .outputs()
                .stream()
                .map(output -> MapperOutputValue.from(output, mapper))
                .toList()
        );
        return entity;
    }

    public MapperDefinition toDomain(JsonMapper mapper) {
        return new MapperDefinition(
            id,
            name,
            description,
            directory,
            wasmFileName,
            outputs
                .stream()
                .map(output -> output.toDomain(mapper))
                .toList(),
            createdAt,
            updatedAt
        );
    }
}
