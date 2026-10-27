package com.ahvakra.camelintegration.tables.infrastructure.persistence;

import com.ahvakra.camelintegration.tables.domain.TableDefinition;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "etl_table_definitions")
public class TableDefinitionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "field_definition_id", nullable = false)
    private UUID fieldDefinitionId;

    @Column(name = "schema_name", nullable = false, length = 63)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = 63)
    private String tableName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "etl_table_columns", joinColumns = @JoinColumn(name = "table_id"))
    @MapKeyColumn(name = "column_key", length = 63)
    @Column(name = "column_type", nullable = false, length = 20)
    private Map<String, String> columns = new HashMap<>();

    protected TableDefinitionEntity() {}

    public static TableDefinitionEntity from(TableDefinition definition) {
        var entity = new TableDefinitionEntity();
        entity.id = definition.id();
        entity.name = definition.name();
        entity.connectionId = definition.connectionId();
        entity.fieldDefinitionId = definition.fieldDefinitionId();
        entity.schemaName = definition.schemaName();
        entity.tableName = definition.tableName();
        entity.createdAt = definition.createdAt();
        entity.updatedAt = definition.updatedAt();
        entity.columns = new HashMap<>(definition.columns());
        return entity;
    }

    public TableDefinition toDomain() {
        var ordered = new LinkedHashMap<String, String>();
        columns
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return new TableDefinition(
            id,
            name,
            connectionId,
            fieldDefinitionId,
            schemaName,
            tableName,
            ordered,
            createdAt,
            updatedAt
        );
    }
}
