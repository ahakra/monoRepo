package com.ahvakra.camelintegration.mappers.application;

import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.mappers.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MapperService {

    private final MapperStore store;
    private final FieldDefinitionStore fields;

    public MapperService(MapperStore store, FieldDefinitionStore fields) {
        this.store = store;
        this.fields = fields;
    }

    public List<MapperDefinition> list() {
        return store.findAll();
    }

    @Transactional
    public MapperDefinition save(UUID id, MapperCommand command) {
        var existing = id == null ? null : find(id);
        validate(command);
        // Lock shared Fields definitions in a stable order before taking schema snapshots.
        var ids = command
            .outputs()
            .stream()
            .map(MapperCommand.Output::fieldDefinitionId)
            .distinct()
            .sorted()
            .toList();
        var definitions = new HashMap<UUID, FieldDefinition>();
        return withFields(ids, 0, definitions, () -> {
            var outputs = command
                .outputs()
                .stream()
                .map(output ->
                    new MapperOutput(
                        output.name(),
                        output.fieldDefinitionId(),
                        definitions.get(output.fieldDefinitionId()).fields()
                    )
                )
                .toList();
            var now = Instant.now();
            var mapper = new MapperDefinition(
                id == null ? UUID.randomUUID() : id,
                command.name().trim(),
                command.description() == null ? "" : command.description().trim(),
                command.directory(),
                command.wasmFileName(),
                outputs,
                existing == null ? now : existing.createdAt(),
                now
            );
            store.save(mapper);
            return mapper;
        });
    }

    @Transactional
    public void delete(UUID id) {
        find(id);
        store.delete(id);
    }

    private MapperDefinition find(UUID id) {
        return store.findById(id).orElseThrow(MapperNotFoundException::new);
    }

    private <T> T withFields(
        List<UUID> ids,
        int index,
        Map<UUID, FieldDefinition> definitions,
        Supplier<T> operation
    ) {
        if (index == ids.size()) return operation.get();
        return fields.withLockedDefinition(ids.get(index), definition -> {
            definitions.put(definition.id(), definition);
            return withFields(ids, index + 1, definitions, operation);
        });
    }

    private void validate(MapperCommand command) {
        if (
            command == null ||
            command.name() == null ||
            command.name().isBlank() ||
            command.name().length() > 120
        ) throw new InvalidMapperException("Enter a mapper name of up to 120 characters.");
        if (
            command.description() != null && command.description().length() > 2000
        ) throw new InvalidMapperException("Description must have up to 2000 characters.");
        if (
            command.directory() == null ||
            command.directory().isBlank() ||
            command.directory().length() > 2048 ||
            command.directory().indexOf('\0') >= 0
        ) throw new InvalidMapperException("Enter a WASM directory of up to 2048 characters.");
        if (
            command.wasmFileName() == null ||
            command.wasmFileName().isBlank() ||
            command.wasmFileName().length() > 255 ||
            !command.wasmFileName().toLowerCase(Locale.ROOT).endsWith(".wasm") ||
            command.wasmFileName().contains("/") ||
            command.wasmFileName().contains("\\") ||
            command.wasmFileName().codePoints().anyMatch(Character::isISOControl)
        ) throw new InvalidMapperException(
            "Enter a WASM filename ending in .wasm, without directory separators, up to 255 characters."
        );
        if (
            command.outputs() == null ||
            command.outputs().isEmpty() ||
            command.outputs().size() > 100
        ) throw new InvalidMapperException("Add between 1 and 100 output lists.");
        var names = new HashSet<String>();
        for (var output : command.outputs()) {
            if (
                output == null ||
                output.name() == null ||
                output.name().isEmpty() ||
                output.name().length() > 120 ||
                output
                    .name()
                    .codePoints()
                    .anyMatch(
                        c ->
                            Character.isWhitespace(c) ||
                            Character.isSpaceChar(c) ||
                            Character.isISOControl(c)
                    )
            ) throw new InvalidMapperException(
                "Each output list needs an exact name of up to 120 characters with no spaces or whitespace."
            );
            if (!names.add(output.name())) throw new InvalidMapperException(
                "Output list names must be unique within a mapper. Names are case sensitive."
            );
            if (output.fieldDefinitionId() == null) throw new InvalidMapperException(
                "Choose a Fields type for every output list."
            );
        }
    }
}
