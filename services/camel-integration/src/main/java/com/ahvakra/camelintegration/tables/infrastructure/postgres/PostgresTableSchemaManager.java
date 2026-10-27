package com.ahvakra.camelintegration.tables.infrastructure.postgres;

import static com.ahvakra.camelintegration.tables.infrastructure.postgres.PostgresTableCreator.identifier;
import static com.ahvakra.camelintegration.tables.infrastructure.postgres.PostgresTableCreator.sqlType;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import com.ahvakra.camelintegration.connections.infrastructure.postgres.PostgresConnectionFactory;
import com.ahvakra.camelintegration.tables.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class PostgresTableSchemaManager implements TableSchemaManager {

    private static final String COLUMNS = """
    SELECT a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type,
           a.attnotnull, COALESCE(pg_catalog.pg_get_expr(d.adbin, d.adrelid), '') AS default_value,
           a.attidentity, a.attgenerated, c.oid AS relation_id,
           EXISTS (SELECT 1 FROM pg_catalog.pg_inherits i WHERE i.inhparent = c.oid OR i.inhrelid = c.oid) AS inherited,
           COALESCE((SELECT string_agg(pg_catalog.pg_get_constraintdef(co.oid), E'\\n' ORDER BY co.oid)
                     FROM pg_catalog.pg_constraint co WHERE co.conrelid = c.oid), '') AS constraints
    FROM pg_catalog.pg_attribute a
    JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
    LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
    WHERE n.nspname = ? AND c.relname = ? AND c.relkind IN ('r', 'p')
      AND a.attnum > 0 AND NOT a.attisdropped
    ORDER BY a.attnum
    """;
    private final PostgresConnectionFactory connections;

    public PostgresTableSchemaManager(PostgresConnectionFactory connections) {
        this.connections = connections;
    }

    public TableSchemaPlan preview(ConnectionDefinition connection, TableDefinition definition) {
        requirePostgres(connection);
        try (var target = connections.open(connection, connection.settings().get("database"))) {
            return plan(target, connection, definition);
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    public void apply(
        ConnectionDefinition connection,
        TableDefinition definition,
        SchemaChangeSelection selection
    ) {
        requirePostgres(connection);
        if (
            selection == null ||
            selection.fingerprint() == null ||
            selection.changeIds() == null ||
            selection.changeIds().isEmpty() ||
            selection.changeIds().size() > 1600
        ) throw new InvalidTableException(
            "Review and select at least one column change before applying it."
        );
        try (var target = connections.open(connection, connection.settings().get("database"))) {
            target.setAutoCommit(false);
            try {
                try (var statement = target.createStatement()) {
                    statement.setQueryTimeout(10);
                    statement.execute("SET LOCAL lock_timeout = '5s'");
                    statement.execute("SET LOCAL statement_timeout = '10s'");
                    statement.execute(
                        "LOCK TABLE " + qualified(definition) + " IN ACCESS EXCLUSIVE MODE"
                    );
                }
                var current = plan(target, connection, definition);
                if (
                    !current.fingerprint().equals(selection.fingerprint())
                ) throw new TableSchemaException(
                    "The table, connection, or saved mapping changed since review. Refresh the column plan and review it again.",
                    true
                );
                var selected = new HashSet<>(selection.changeIds());
                if (
                    selected.size() != selection.changeIds().size()
                ) throw new InvalidTableException("Select each column change only once.");
                var changes = current
                    .changes()
                    .stream()
                    .filter(change -> selected.contains(change.id()))
                    .toList();
                if (changes.size() != selected.size()) throw new TableSchemaException(
                    "Selected changes are no longer available. Refresh the column plan.",
                    true
                );
                if (
                    !selection.acknowledgeDataLoss() &&
                    changes.stream().anyMatch(SchemaChange::destructive)
                ) throw new InvalidTableException(
                    "Acknowledge possible data loss before applying DROP or ALTER column changes."
                );
                try (var statement = target.createStatement()) {
                    statement.setQueryTimeout(10);
                    for (var change : changes) statement.executeUpdate(change.sql());
                }
                target.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    target.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    private TableSchemaPlan plan(
        Connection target,
        ConnectionDefinition connection,
        TableDefinition definition
    ) throws SQLException {
        String table = qualified(definition);
        var actual = new LinkedHashMap<String, String>();
        var protectedColumns = new HashSet<String>();
        var fingerprint = new ArrayList<String>();
        fingerprint.add(definition.id().toString());
        fingerprint.add(connection.id().toString());
        fingerprint.add(new TreeMap<>(connection.settings()).toString());
        fingerprint.add(table);
        fingerprint.add(new TreeMap<>(definition.columns()).toString());
        fingerprint.add(definition.updatedAt().toString());
        try (var statement = target.prepareStatement(COLUMNS)) {
            statement.setQueryTimeout(10);
            statement.setString(1, definition.schemaName());
            statement.setString(2, definition.tableName());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (rows.getBoolean("inherited")) throw new InvalidTableException(
                        "Partitioned or inherited tables must be altered directly in PostgreSQL."
                    );
                    String name = rows.getString("attname");
                    String type = rows.getString("data_type");
                    String identity = rows.getString("attidentity");
                    String generated = rows.getString("attgenerated");
                    actual.put(name, type);
                    if (
                        (identity != null && !identity.isEmpty()) ||
                        (generated != null && !generated.isEmpty())
                    ) protectedColumns.add(name);
                    fingerprint.add(
                        List.of(
                            name,
                            type,
                            Boolean.toString(rows.getBoolean("attnotnull")),
                            rows.getString("default_value"),
                            identity == null ? "" : identity,
                            generated == null ? "" : generated,
                            rows.getString("relation_id"),
                            rows.getString("constraints")
                        ).toString()
                    );
                }
            }
        }
        if (actual.isEmpty()) throw new InvalidTableException(
            "The target PostgreSQL table was not found or has no columns. Create the table first."
        );
        var changes = new ArrayList<SchemaChange>();
        definition
            .columns()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String key = entry.getKey();
                String type = sqlType(entry.getValue());
                if (!actual.containsKey(key)) {
                    changes.add(
                        new SchemaChange(
                            "add:" + key,
                            "ADD",
                            key,
                            null,
                            type,
                            "ALTER TABLE " +
                                table +
                                " ADD COLUMN " +
                                identifier(key) +
                                " " +
                                type +
                                ";",
                            false
                        )
                    );
                } else if (!actual.get(key).equalsIgnoreCase(type)) {
                    if (protectedColumns.contains(key)) throw new InvalidTableException(
                        "Column '" +
                            key +
                            "' is an identity or generated column. Manage its type directly in PostgreSQL."
                    );
                    changes.add(
                        new SchemaChange(
                            "alter:" + key,
                            "ALTER",
                            key,
                            actual.get(key),
                            type,
                            "ALTER TABLE " +
                                table +
                                " ALTER COLUMN " +
                                identifier(key) +
                                " TYPE " +
                                type +
                                " USING " +
                                identifier(key) +
                                "::" +
                                type +
                                ";",
                            true
                        )
                    );
                }
            });
        actual.forEach((key, type) -> {
            if (!definition.columns().containsKey(key)) changes.add(
                new SchemaChange(
                    "drop:" + key,
                    "DROP",
                    key,
                    type,
                    null,
                    "ALTER TABLE " + table + " DROP COLUMN " + identifier(key) + ";",
                    true
                )
            );
        });
        return new TableSchemaPlan(hash(fingerprint.toString()), changes);
    }

    private static String qualified(TableDefinition definition) {
        return identifier(definition.schemaName()) + "." + identifier(definition.tableName());
    }

    private static void requirePostgres(ConnectionDefinition connection) {
        if (!"postgres".equals(connection.type())) throw new InvalidTableException(
            "Tables currently support PostgreSQL connections only."
        );
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static TableSchemaException failure(SQLException exception) {
        String state = exception.getSQLState() == null ? "" : exception.getSQLState();
        String message = switch (state) {
            case "42501" -> "The connection user lacks permission to inspect or alter this table.";
            case
                "42P01",
                "3F000" -> "The table or schema no longer exists. Refresh the column plan.";
            case "2BP01" -> "A column has dependent objects. No changes were applied; resolve its dependencies in PostgreSQL first.";
            case
                "55P03",
                "57014" -> "The table is busy or the change timed out. Refresh the plan and retry. If connection loss interrupted commit, verify the database first.";
            default -> "Could not read or apply PostgreSQL column changes. Check the connection, permissions, and whether existing data and defaults can be converted. Changes in a failed transaction are rolled back; after connection loss, verify the database before retrying.";
        };
        return new TableSchemaException(
            message,
            "42P01".equals(state) ||
                "3F000".equals(state) ||
                "2BP01".equals(state) ||
                "55P03".equals(state)
        );
    }
}
