package com.ahvakra.camelintegration.fields.application;

import com.ahvakra.camelintegration.fields.domain.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FieldDefinitionService {

    private static final Set<String> TYPES = Set.of(
        "string",
        "integer",
        "long",
        "decimal",
        "boolean",
        "date",
        "datetime",
        "uuid",
        "json"
    );
    private final FieldDefinitionStore store;
    private final List<FieldDefinitionUsage> usages;

    public FieldDefinitionService(FieldDefinitionStore store, List<FieldDefinitionUsage> usages) {
        this.store = store;
        this.usages = List.copyOf(usages);
    }

    public List<FieldDefinition> list() {
        return store.findAll();
    }

    public FieldDefinition create(FieldDefinitionCommand command) {
        var now = Instant.now();
        var definition = prepare(UUID.randomUUID(), command, now, now);
        store.save(definition);
        return definition;
    }

    public FieldDefinition update(UUID id, FieldDefinitionCommand command) {
        return store.withLockedDefinition(id, existing -> {
            var definition = prepare(id, command, existing.createdAt(), Instant.now());
            usages.forEach(usage -> usage.validateUpdate(definition));
            store.save(definition);
            return definition;
        });
    }

    public void delete(UUID id) {
        store.withLockedDefinition(id, existing -> {
            var references = references(id);
            if (!references.isEmpty()) throw new FieldsInUseException(
                "This Fields definition is used by: " +
                    references
                        .stream()
                        .map(FieldReference::tableName)
                        .distinct()
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(", ")) +
                    ". Edit or delete the dependent tables, rules, mappers, or transformers first."
            );
            store.delete(id);
            return null;
        });
    }

    public List<FieldReference> references(UUID id) {
        return usages
            .stream()
            .flatMap(usage -> usage.references(id).stream())
            .toList();
    }

    private FieldDefinition prepare(
        UUID id,
        FieldDefinitionCommand command,
        Instant createdAt,
        Instant updatedAt
    ) {
        if (
            command == null ||
            command.name() == null ||
            command.name().isBlank() ||
            command.name().length() > 120
        ) {
            throw new InvalidFieldsException("Enter a definition name of up to 120 characters.");
        }
        if (command.fields() == null) {
            throw new InvalidFieldsException("Provide a dictionary of field keys and types.");
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("id", "uuid");
        fields.put("datetime", "datetime");
        for (var entry : command.fields().entrySet()) {
            String key = entry.getKey();
            String type = entry.getValue();
            if (key == null || !key.matches("[a-z_][a-z0-9_]{0,62}")) {
                throw new InvalidFieldsException(
                    "Field keys must start with a lowercase letter or underscore and contain up to 63 lowercase letters, numbers, or underscores."
                );
            }
            if (type == null || !TYPES.contains(type)) {
                throw new InvalidFieldsException(
                    "Choose a supported field type: " +
                        String.join(", ", TYPES.stream().sorted().toList()) +
                        "."
                );
            }
            if (
                (key.equals("id") && !type.equals("uuid")) ||
                (key.equals("datetime") && !type.equals("datetime"))
            ) {
                throw new InvalidFieldsException(
                    "Default fields must keep their types: id (uuid) and datetime (datetime)."
                );
            }
            fields.put(key, type);
        }
        if (fields.size() > 100) {
            throw new InvalidFieldsException(
                "A definition can contain up to 100 fields, including id and datetime."
            );
        }
        return new FieldDefinition(id, command.name().trim(), fields, createdAt, updatedAt);
    }
}
