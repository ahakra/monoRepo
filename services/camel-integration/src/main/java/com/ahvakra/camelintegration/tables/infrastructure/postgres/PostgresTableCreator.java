package com.ahvakra.camelintegration.tables.infrastructure.postgres;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import com.ahvakra.camelintegration.connections.infrastructure.postgres.PostgresConnectionFactory;
import com.ahvakra.camelintegration.tables.domain.*;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PostgresTableCreator implements TableCreator {

    private static final Map<String, String> TYPES = Map.of(
        "string",
        "TEXT",
        "integer",
        "INTEGER",
        "long",
        "BIGINT",
        "decimal",
        "NUMERIC",
        "boolean",
        "BOOLEAN",
        "date",
        "DATE",
        "datetime",
        "TIMESTAMP WITH TIME ZONE",
        "uuid",
        "UUID",
        "json",
        "JSONB"
    );
    private final PostgresConnectionFactory connections;

    public PostgresTableCreator(PostgresConnectionFactory connections) {
        this.connections = connections;
    }

    public String sql(TableDefinition definition) {
        if (definition.columns().isEmpty()) throw new InvalidTableException(
            "Select at least one field."
        );
        String columns = definition
            .columns()
            .entrySet()
            .stream()
            .map(entry -> {
                String type = sqlType(entry.getValue());
                return "    " + identifier(entry.getKey()) + " " + type;
            })
            .collect(Collectors.joining(",\n"));
        return (
            "CREATE TABLE " +
            identifier(definition.schemaName()) +
            "." +
            identifier(definition.tableName()) +
            " (\n" +
            columns +
            "\n);"
        );
    }

    public void create(ConnectionDefinition connection, TableDefinition definition) {
        if (!"postgres".equals(connection.type())) throw new InvalidTableException(
            "Tables currently support PostgreSQL connections only."
        );
        String ddl = sql(definition);
        try (
            var target = connections.open(connection, connection.settings().get("database"));
            var statement = target.createStatement()
        ) {
            statement.setQueryTimeout(10);
            statement.executeUpdate(ddl);
        } catch (SQLException exception) {
            String state = exception.getSQLState() == null ? "" : exception.getSQLState();
            String message = switch (state) {
                case
                    "42P07",
                    "42710",
                    "23505" -> "A table or relation with this name already exists. Choose another table name; existing tables are not modified.";
                case "3F000" -> "The PostgreSQL schema does not exist. Choose an existing schema.";
                case "42501" -> "The connection user does not have permission to create a table in this schema.";
                case
                    "28P01",
                    "28000" -> "Authentication failed. Check the saved connection credentials.";
                default -> "Could not create the PostgreSQL table. Check the connection, schema, and database permissions. If the request timed out, check the database before retrying.";
            };
            throw new TableCreationException(
                message,
                "42P07".equals(state) || "42710".equals(state) || "23505".equals(state)
            );
        }
    }

    static String sqlType(String value) {
        String type = value == null ? null : TYPES.get(value);
        if (type == null) throw new InvalidTableException("Unsupported field type.");
        return type;
    }

    static String identifier(String value) {
        if (
            value == null || !value.matches("[a-z_][a-z0-9_]{0,62}")
        ) throw new InvalidTableException(
            "Schema, table, and column names must start with a lowercase letter or underscore and contain up to 63 lowercase letters, numbers, or underscores."
        );
        return "\"" + value + "\"";
    }
}
