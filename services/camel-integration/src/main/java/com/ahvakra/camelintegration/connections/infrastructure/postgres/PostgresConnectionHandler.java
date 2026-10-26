package com.ahvakra.camelintegration.connections.infrastructure.postgres;

import com.ahvakra.camelintegration.connections.domain.*;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PostgresConnectionHandler implements ConnectionTypeHandler {

    private static final Set<String> KEYS = Set.of(
        "host",
        "port",
        "database",
        "username",
        "sslMode",
        "createDatabaseIfNotExists"
    );
    private final PostgresConnectionFactory connections;

    public PostgresConnectionHandler(PostgresConnectionFactory connections) {
        this.connections = connections;
    }

    public String type() {
        return "postgres";
    }

    public Map<String, String> validateSettings(Map<String, String> settings) {
        if (
            settings == null ||
            !KEYS.containsAll(settings.keySet()) ||
            settings
                .values()
                .stream()
                .anyMatch(value -> value == null)
        ) throw new InvalidConnectionException("Invalid PostgreSQL settings.");
        var normalized = new HashMap<>(settings);
        normalized.replaceAll((key, value) -> value.trim());
        require(
            normalized.get("host"),
            "[a-zA-Z0-9.-]{1,253}",
            "Enter a valid hostname or IPv4 address."
        );
        require(
            normalized.get("database"),
            "[a-zA-Z0-9_-]{1,63}",
            "Database name must contain 1–63 letters, numbers, underscores, or hyphens."
        );
        String username = normalized.get("username");
        if (
            username == null || username.isBlank() || username.length() > 120
        ) throw new InvalidConnectionException("Enter a username of up to 120 characters.");
        try {
            int port = Integer.parseInt(normalized.getOrDefault("port", ""));
            if (port < 1 || port > 65535) throw new NumberFormatException();
            normalized.put("port", Integer.toString(port));
        } catch (NumberFormatException exception) {
            throw new InvalidConnectionException("Port must be between 1 and 65535.");
        }
        if (
            !Set.of("disable", "require", "verify-full").contains(
                normalized.getOrDefault("sslMode", "")
            )
        ) throw new InvalidConnectionException("Choose a valid SSL mode.");
        String create = normalized.getOrDefault("createDatabaseIfNotExists", "false");
        if (!Set.of("true", "false").contains(create)) throw new InvalidConnectionException(
            "Invalid create database option."
        );
        normalized.put("createDatabaseIfNotExists", create);
        return normalized;
    }

    public ConnectionTestResult test(ConnectionDefinition definition) {
        try {
            try {
                return check(definition);
            } catch (SQLException exception) {
                if (
                    !"3D000".equals(exception.getSQLState()) ||
                    !Boolean.parseBoolean(definition.settings().get("createDatabaseIfNotExists"))
                ) throw exception;
            }
            createDatabase(definition);
            return check(definition);
        } catch (SQLException exception) {
            String message = switch (
                exception.getSQLState() == null ? "" : exception.getSQLState()
            ) {
                case "42501" -> "The database user does not have permission to create this database.";
                case "3D000" -> "Database does not exist. Enable create database if needed.";
                case "28P01", "28000" -> "Authentication failed. Check the database credentials.";
                default -> "Could not connect. Check the host, database, credentials, and SSL mode.";
            };
            return new ConnectionTestResult(false, message);
        }
    }

    private ConnectionTestResult check(ConnectionDefinition definition) throws SQLException {
        try (var connection = connections.open(definition, definition.settings().get("database"))) {
            boolean valid = connection.isValid(5);
            return new ConnectionTestResult(
                valid,
                valid
                    ? "Connection successful. PostgreSQL is ready."
                    : "PostgreSQL did not respond."
            );
        }
    }

    private void createDatabase(ConnectionDefinition definition) throws SQLException {
        // PostgreSQL CREATE DATABASE requires an independent, auto-commit connection.
        try (var connection = connections.open(definition, "postgres")) {
            connection.setAutoCommit(true);
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                String database = definition.settings().get("database");
                try {
                    statement.executeUpdate(
                        "CREATE DATABASE \"" + database.replace("\"", "\"\"") + "\""
                    );
                } catch (SQLException exception) {
                    if (!"42P04".equals(exception.getSQLState())) throw exception;
                }
            }
        }
    }

    private static void require(String value, String pattern, String message) {
        if (value == null || !value.matches(pattern)) throw new InvalidConnectionException(message);
    }
}
