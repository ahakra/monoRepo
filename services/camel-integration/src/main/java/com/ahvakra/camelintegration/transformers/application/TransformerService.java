package com.ahvakra.camelintegration.transformers.application;

import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.transformers.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransformerService {

    private final TransformerStore store;
    private final FieldDefinitionStore fields;

    public TransformerService(TransformerStore store, FieldDefinitionStore fields) {
        this.store = store;
        this.fields = fields;
    }

    public List<TransformerDefinition> list() {
        return store.findAll();
    }

    @Transactional
    public TransformerDefinition save(UUID id, TransformerCommand command) {
        var existing = id == null ? null : find(id);
        validate(command);
        var ids = Stream.concat(command.inputs().stream(), command.outputs().stream())
            .filter(port -> port.kind() == TransformerPortKind.FIELD_LIST)
            .map(TransformerCommand.Port::fieldDefinitionId)
            .distinct()
            .sorted()
            .toList();
        var definitions = new HashMap<UUID, FieldDefinition>();
        return withFields(ids, 0, definitions, () -> {
            var now = Instant.now();
            var definition = new TransformerDefinition(
                id == null ? UUID.randomUUID() : id,
                command.name().trim(),
                command.description() == null ? "" : command.description().trim(),
                command.directory(),
                command.wasmFileName(),
                ports(command.inputs(), definitions),
                ports(command.outputs(), definitions),
                existing == null ? now : existing.createdAt(),
                now
            );
            store.save(definition);
            return definition;
        });
    }

    private List<TransformerPort> ports(
        List<TransformerCommand.Port> ports,
        Map<UUID, FieldDefinition> definitions
    ) {
        return ports
            .stream()
            .map(port ->
                new TransformerPort(
                    port.name(),
                    port.kind(),
                    port.fieldDefinitionId(),
                    port.kind() == TransformerPortKind.FIELD_LIST
                        ? definitions.get(port.fieldDefinitionId()).fields()
                        : Map.of()
                )
            )
            .toList();
    }

    @Transactional
    public void delete(UUID id) {
        find(id);
        store.delete(id);
    }

    private TransformerDefinition find(UUID id) {
        return store.findById(id).orElseThrow(TransformerNotFoundException::new);
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

    private void validate(TransformerCommand command) {
        if (
            command == null ||
            command.name() == null ||
            command.name().isBlank() ||
            command.name().length() > 120
        ) throw new InvalidTransformerException(
            "Enter a transformer name of up to 120 characters."
        );
        if (
            command.description() != null && command.description().length() > 2000
        ) throw new InvalidTransformerException("Description must have up to 2000 characters.");
        if (
            command.directory() == null ||
            command.directory().isBlank() ||
            command.directory().length() > 2048 ||
            command.directory().indexOf('\0') >= 0
        ) throw new InvalidTransformerException("Enter a WASM directory of up to 2048 characters.");
        if (
            command.wasmFileName() == null ||
            command.wasmFileName().isBlank() ||
            command.wasmFileName().length() > 255 ||
            !command.wasmFileName().toLowerCase(Locale.ROOT).endsWith(".wasm") ||
            command.wasmFileName().contains("/") ||
            command.wasmFileName().contains("\\") ||
            command.wasmFileName().codePoints().anyMatch(Character::isISOControl)
        ) throw new InvalidTransformerException(
            "Enter a WASM filename ending in .wasm, without directory separators, up to 255 characters."
        );
        validatePorts(command.inputs(), "input");
        validatePorts(command.outputs(), "output");
    }

    private void validatePorts(List<TransformerCommand.Port> ports, String direction) {
        if (
            ports == null || ports.isEmpty() || ports.size() > 100
        ) throw new InvalidTransformerException("Add between 1 and 100 " + direction + "s.");
        var names = new HashSet<String>();
        for (var port : ports) {
            if (
                port == null ||
                port.name() == null ||
                port.name().isEmpty() ||
                port.name().length() > 120 ||
                port
                    .name()
                    .codePoints()
                    .anyMatch(
                        c ->
                            Character.isWhitespace(c) ||
                            Character.isSpaceChar(c) ||
                            Character.isISOControl(c)
                    )
            ) throw new InvalidTransformerException(
                "Each " +
                    direction +
                    " needs an exact name of up to 120 characters without whitespace."
            );
            if (!names.add(port.name())) throw new InvalidTransformerException(
                "Names must be unique within the " + direction + "s. Names are case sensitive."
            );
            if (port.kind() == null) throw new InvalidTransformerException(
                "Choose Fields list or Dynamic variable for each " + direction + "."
            );
            if (
                port.kind() == TransformerPortKind.FIELD_LIST && port.fieldDefinitionId() == null
            ) throw new InvalidTransformerException(
                "Choose a Fields definition for each Fields list."
            );
            if (
                port.kind() == TransformerPortKind.DYNAMIC_VARIABLE &&
                port.fieldDefinitionId() != null
            ) throw new InvalidTransformerException(
                "A dynamic variable cannot reference a Fields definition."
            );
        }
    }
}
