package com.ahvakra.camelintegration.transformers.infrastructure.persistence;

import com.ahvakra.camelintegration.transformers.domain.TransformerDefinition;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@Entity
@Table(name = "etl_transformers")
public class TransformerDefinitionEntity {

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
    @CollectionTable(
        name = "etl_transformer_inputs",
        joinColumns = @JoinColumn(name = "transformer_id")
    )
    @OrderColumn(name = "port_order")
    private List<TransformerPortValue> inputs = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
        name = "etl_transformer_outputs",
        joinColumns = @JoinColumn(name = "transformer_id")
    )
    @OrderColumn(name = "port_order")
    private List<TransformerPortValue> outputs = new ArrayList<>();

    protected TransformerDefinitionEntity() {}

    void clearPorts() {
        inputs.clear();
        outputs.clear();
    }

    public static TransformerDefinitionEntity from(
        TransformerDefinition definition,
        JsonMapper mapper
    ) {
        var entity = new TransformerDefinitionEntity();
        entity.id = definition.id();
        entity.name = definition.name();
        entity.description = definition.description();
        entity.directory = definition.directory();
        entity.wasmFileName = definition.wasmFileName();
        entity.createdAt = definition.createdAt();
        entity.updatedAt = definition.updatedAt();
        entity.inputs = new ArrayList<>(
            definition
                .inputs()
                .stream()
                .map(port -> TransformerPortValue.from(port, mapper))
                .toList()
        );
        entity.outputs = new ArrayList<>(
            definition
                .outputs()
                .stream()
                .map(port -> TransformerPortValue.from(port, mapper))
                .toList()
        );
        return entity;
    }

    public TransformerDefinition toDomain(JsonMapper mapper) {
        return new TransformerDefinition(
            id,
            name,
            description,
            directory,
            wasmFileName,
            inputs
                .stream()
                .map(port -> port.toDomain(mapper))
                .toList(),
            outputs
                .stream()
                .map(port -> port.toDomain(mapper))
                .toList(),
            createdAt,
            updatedAt
        );
    }
}
