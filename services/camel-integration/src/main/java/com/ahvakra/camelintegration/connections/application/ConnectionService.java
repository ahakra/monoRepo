package com.ahvakra.camelintegration.connections.application;

import com.ahvakra.camelintegration.connections.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConnectionService {

    private final ConnectionStore store;
    private final Map<String, ConnectionTypeHandler> handlers;

    public ConnectionService(ConnectionStore store, List<ConnectionTypeHandler> handlers) {
        this.store = store;
        this.handlers = handlers
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(ConnectionTypeHandler::type, Function.identity())
            );
    }

    public List<ConnectionDefinition> list() {
        return store.findAll();
    }

    public UUID create(ConnectionCommand command) {
        var connection = prepare(UUID.randomUUID(), command, "", Instant.now());
        store.save(connection);
        return connection.id();
    }

    public void update(UUID id, ConnectionCommand command) {
        var existing = find(id);
        if (!existing.type().equals(command.type())) throw new InvalidConnectionException(
            "A saved connector's type cannot be changed. Create a new connector for a different type."
        );
        store.save(prepare(id, command, existing.password(), existing.createdAt()));
    }

    public void delete(UUID id) {
        find(id);
        store.delete(id);
    }

    public ConnectionTestResult test(UUID id, ConnectionCommand command) {
        var connection = prepare(id, command, id == null ? "" : find(id).password(), null);
        return handler(connection.type()).test(connection);
    }

    private ConnectionDefinition find(UUID id) {
        return store.findById(id).orElseThrow(ConnectionNotFoundException::new);
    }

    private ConnectionTypeHandler handler(String type) {
        if (type == null || !handlers.containsKey(type)) throw new InvalidConnectionException(
            "Unsupported connection type."
        );
        return handlers.get(type);
    }

    private ConnectionDefinition prepare(
        UUID id,
        ConnectionCommand command,
        String savedPassword,
        Instant createdAt
    ) {
        if (
            command.name() == null || command.name().isBlank() || command.name().length() > 120
        ) throw new InvalidConnectionException("Enter a name of up to 120 characters.");
        if (
            command.password() != null && command.password().length() > 4096
        ) throw new InvalidConnectionException("Password is too long.");
        var settings = handler(command.type()).validateSettings(command.settings());
        String password =
            command.password() == null || command.password().isEmpty()
                ? savedPassword
                : command.password();
        return new ConnectionDefinition(
            id,
            command.name().trim(),
            command.type(),
            settings,
            password,
            createdAt,
            Instant.now()
        );
    }
}
