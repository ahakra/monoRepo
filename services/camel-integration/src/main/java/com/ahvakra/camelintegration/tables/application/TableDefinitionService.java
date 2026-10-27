package com.ahvakra.camelintegration.tables.application;

import com.ahvakra.camelintegration.connections.domain.*;
import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.tables.domain.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class TableDefinitionService {

    private final TableDefinitionStore store;
    private final FieldDefinitionStore fields;
    private final ConnectionStore connections;
    private final TableCreator creator;
    private final TableSchemaManager schema;

    public TableDefinitionService(
        TableDefinitionStore store,
        FieldDefinitionStore fields,
        ConnectionStore connections,
        TableCreator creator,
        TableSchemaManager schema
    ) {
        this.store = store;
        this.fields = fields;
        this.connections = connections;
        this.creator = creator;
        this.schema = schema;
    }

    public List<TableDefinition> list() {
        return store.findAll();
    }

    public TableDefinition create(TableDefinitionCommand command) {
        var now = Instant.now();
        return save(UUID.randomUUID(), command, now, now);
    }

    public TableDefinition update(UUID id, TableDefinitionCommand command) {
        var existing = find(id);
        return save(id, command, existing.createdAt(), Instant.now());
    }

    public void delete(UUID id) {
        find(id);
        store.delete(id);
    }

    public String sql(TableDefinition definition) {
        return creator.sql(definition);
    }

    public void createPhysicalTable(UUID id) {
        var definition = find(id);
        creator.create(validatedConnection(definition), definition);
    }

    public TableSchemaPlan previewSchema(UUID id) {
        var definition = find(id);
        return schema.preview(validatedConnection(definition), definition);
    }

    public void applySchema(UUID id, SchemaChangeSelection selection) {
        store.withLockedDefinition(id, definition -> {
            schema.apply(validatedConnection(definition), definition, selection);
            return null;
        });
    }

    private ConnectionDefinition validatedConnection(TableDefinition definition) {
        var connection = postgres(definition.connectionId());
        var dictionary = dictionary(definition.fieldDefinitionId());
        definition.columns().forEach((key, type) -> {
            if (!type.equals(dictionary.fields().get(key))) throw new InvalidTableException(
                "Selected fields have changed. Edit and save the table configuration before creating it."
            );
        });
        return connection;
    }

    private TableDefinition find(UUID id) {
        return store.findById(id).orElseThrow(TableNotFoundException::new);
    }

    private ConnectionDefinition postgres(UUID id) {
        if (id == null) throw new InvalidTableException(
            "Select an existing PostgreSQL connection."
        );
        var connection = connections
            .findById(id)
            .orElseThrow(() -> new InvalidTableException("Selected connection no longer exists."));
        if (!"postgres".equals(connection.type())) throw new InvalidTableException(
            "Tables currently support PostgreSQL connections only."
        );
        return connection;
    }

    private FieldDefinition dictionary(UUID id) {
        if (id == null) throw new InvalidTableException("Select a saved Fields definition.");
        return fields
            .findById(id)
            .orElseThrow(() ->
                new InvalidTableException("Selected Fields definition no longer exists.")
            );
    }

    private TableDefinition prepare(
        UUID id,
        TableDefinitionCommand command,
        Instant createdAt,
        Instant updatedAt
    ) {
        if (
            command == null ||
            command.name() == null ||
            command.name().isBlank() ||
            command.name().length() > 120
        ) throw new InvalidTableException("Enter a configuration name of up to 120 characters.");
        postgres(command.connectionId());
        var dictionary = dictionary(command.fieldDefinitionId());
        if (
            command.fields() == null || command.fields().isEmpty() || command.fields().size() > 100
        ) throw new InvalidTableException(
            "Select between 1 and 100 fields from the chosen Fields definition."
        );
        var columns = new LinkedHashMap<String, String>();
        for (String key : command.fields()) {
            if (
                key == null || !dictionary.fields().containsKey(key)
            ) throw new InvalidTableException(
                "Only fields in the selected Fields definition may be added to a table."
            );
            if (
                columns.putIfAbsent(key, dictionary.fields().get(key)) != null
            ) throw new InvalidTableException("Each field can only be selected once.");
        }
        var definition = new TableDefinition(
            id,
            command.name().trim(),
            command.connectionId(),
            command.fieldDefinitionId(),
            command.schemaName() == null ? null : command.schemaName().trim(),
            command.tableName() == null ? null : command.tableName().trim(),
            columns,
            createdAt,
            updatedAt
        );
        creator.sql(definition); // Validate identifiers and mapped types before storing configuration.
        return definition;
    }

    private TableDefinition save(
        UUID id,
        TableDefinitionCommand command,
        Instant createdAt,
        Instant updatedAt
    ) {
        if (command == null || command.fieldDefinitionId() == null) throw new InvalidTableException(
            "Select a saved Fields definition."
        );
        try {
            // The same row lock is held by field edits and deletes, closing the check/save race.
            return fields.withLockedDefinition(command.fieldDefinitionId(), ignored -> {
                var definition = prepare(id, command, createdAt, updatedAt);
                store.save(definition);
                return definition;
            });
        } catch (FieldsNotFoundException exception) {
            throw new InvalidTableException("Selected Fields definition no longer exists.");
        }
    }
}
