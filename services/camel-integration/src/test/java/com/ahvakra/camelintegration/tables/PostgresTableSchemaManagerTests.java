package com.ahvakra.camelintegration.tables;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import com.ahvakra.camelintegration.connections.infrastructure.postgres.PostgresConnectionFactory;
import com.ahvakra.camelintegration.tables.domain.*;
import com.ahvakra.camelintegration.tables.infrastructure.postgres.PostgresTableSchemaManager;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgresTableSchemaManagerTests {

    record Column(String name, String type, String identity, String generated, boolean inherited) {
        Column(String name, String type) {
            this(name, type, "", "", false);
        }
    }

    private final PostgresConnectionFactory factory = mock(PostgresConnectionFactory.class);
    private final Connection target = mock(Connection.class);
    private final Statement statement = mock(Statement.class);
    private final PreparedStatement query = mock(PreparedStatement.class);
    private final PostgresTableSchemaManager manager = new PostgresTableSchemaManager(factory);
    private final ConnectionDefinition connection = new ConnectionDefinition(
        UUID.randomUUID(),
        "CDR",
        "postgres",
        Map.of("database", "cdr"),
        "secret",
        Instant.now(),
        Instant.now()
    );
    private final TableDefinition definition = new TableDefinition(
        UUID.randomUUID(),
        "CDR",
        connection.id(),
        UUID.randomUUID(),
        "public",
        "cdr",
        Map.of("id", "uuid", "duration", "integer", "status", "string"),
        Instant.now(),
        Instant.now()
    );
    private List<Column> actual = List.of(
        new Column("id", "uuid"),
        new Column("duration", "text"),
        new Column("obsolete", "integer")
    );

    @BeforeEach
    void jdbc() throws Exception {
        when(factory.open(connection, "cdr")).thenReturn(target);
        when(target.createStatement()).thenReturn(statement);
        when(target.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenAnswer(invocation -> {
            var columns = List.copyOf(actual);
            var index = new AtomicInteger(-1);
            var rows = mock(ResultSet.class);
            when(rows.next()).thenAnswer(call -> index.incrementAndGet() < columns.size());
            when(rows.getString(anyString())).thenAnswer(call -> {
                var column = columns.get(index.get());
                return switch ((String) call.getArgument(0)) {
                    case "attname" -> column.name();
                    case "data_type" -> column.type();
                    case "attidentity" -> column.identity();
                    case "attgenerated" -> column.generated();
                    case "relation_id" -> "123";
                    default -> "";
                };
            });
            when(rows.getBoolean(anyString())).thenAnswer(
                call ->
                    "inherited".equals(call.getArgument(0)) && columns.get(index.get()).inherited()
            );
            return rows;
        });
    }

    @Test
    void previewFindsAddsCastsAndDropsWithoutExecutingDdl() throws Exception {
        var plan = manager.preview(connection, definition);
        assertEquals(
            Set.of("add:status", "alter:duration", "drop:obsolete"),
            plan.changes().stream().map(SchemaChange::id).collect(Collectors.toSet())
        );
        assertEquals(
            "ALTER TABLE \"public\".\"cdr\" ALTER COLUMN \"duration\" TYPE INTEGER USING \"duration\"::INTEGER;",
            plan
                .changes()
                .stream()
                .filter(change -> change.action().equals("ALTER"))
                .findFirst()
                .orElseThrow()
                .sql()
        );
        assertEquals(2, plan.changes().stream().filter(SchemaChange::destructive).count());
        verify(query).setString(1, "public");
        verify(query).setString(2, "cdr");
        verify(statement, never()).executeUpdate(anyString());
        verify(target, never()).commit();
        verify(target).close();
    }

    @Test
    void appliesOnlySelectedChangesUnderLockAndCommits() throws Exception {
        var plan = manager.preview(connection, definition);
        manager.apply(
            connection,
            definition,
            new SchemaChangeSelection(plan.fingerprint(), List.of("add:status"), false)
        );
        var order = inOrder(statement, target);
        order.verify(statement).execute("LOCK TABLE \"public\".\"cdr\" IN ACCESS EXCLUSIVE MODE");
        order
            .verify(statement)
            .executeUpdate("ALTER TABLE \"public\".\"cdr\" ADD COLUMN \"status\" TEXT;");
        order.verify(target).commit();
        verify(statement, times(1)).executeUpdate(anyString());
        verify(target, never()).rollback();
    }

    @Test
    void destructiveChangesNeedAcknowledgmentAndNoSqlComesFromClient() throws Exception {
        var plan = manager.preview(connection, definition);
        assertThrows(InvalidTableException.class, () ->
            manager.apply(
                connection,
                definition,
                new SchemaChangeSelection(plan.fingerprint(), List.of("drop:obsolete"), false)
            )
        );
        assertThrows(TableSchemaException.class, () ->
            manager.apply(
                connection,
                definition,
                new SchemaChangeSelection(plan.fingerprint(), List.of("DROP TABLE cdr"), true)
            )
        );
        assertThrows(InvalidTableException.class, () ->
            manager.apply(
                connection,
                definition,
                new SchemaChangeSelection(
                    plan.fingerprint(),
                    List.of("add:status", "add:status"),
                    true
                )
            )
        );
        verify(statement, never()).executeUpdate(anyString());
        verify(target, times(3)).rollback();
        manager.apply(
            connection,
            definition,
            new SchemaChangeSelection(plan.fingerprint(), List.of("drop:obsolete"), true)
        );
        verify(statement).executeUpdate("ALTER TABLE \"public\".\"cdr\" DROP COLUMN \"obsolete\";");
        verify(target).commit();
    }

    @Test
    void staleDatabaseOrMappingPlansAreRejected() throws Exception {
        var plan = manager.preview(connection, definition);
        actual = List.of(new Column("id", "uuid"), new Column("duration", "bigint"));
        var stale = assertThrows(TableSchemaException.class, () ->
            manager.apply(
                connection,
                definition,
                new SchemaChangeSelection(plan.fingerprint(), List.of("add:status"), false)
            )
        );
        assertTrue(stale.conflict());
        var updated = new TableDefinition(
            definition.id(),
            definition.name(),
            definition.connectionId(),
            definition.fieldDefinitionId(),
            definition.schemaName(),
            definition.tableName(),
            Map.of("id", "uuid"),
            definition.createdAt(),
            Instant.now()
        );
        assertThrows(TableSchemaException.class, () ->
            manager.apply(
                connection,
                updated,
                new SchemaChangeSelection(plan.fingerprint(), List.of("add:status"), false)
            )
        );
        verify(statement, never()).executeUpdate(anyString());
    }

    @Test
    void failedConversionRollsBackAllSelectedChangesAndHidesDriverDetails() throws Exception {
        var plan = manager.preview(connection, definition);
        when(statement.executeUpdate(anyString()))
            .thenReturn(0)
            .thenThrow(new SQLException("password=secret", "22P02"));
        var error = assertThrows(TableSchemaException.class, () ->
            manager.apply(
                connection,
                definition,
                new SchemaChangeSelection(
                    plan.fingerprint(),
                    List.of("add:status", "alter:duration"),
                    true
                )
            )
        );
        assertFalse(error.getMessage().contains("secret"));
        verify(target).rollback();
        verify(target, never()).commit();
        verify(statement, times(2)).executeUpdate(anyString());
    }

    @Test
    void missingAndInheritedTablesAndGeneratedTypeChangesAreRejected() {
        actual = List.of();
        assertThrows(InvalidTableException.class, () -> manager.preview(connection, definition));
        actual = List.of(new Column("duration", "text", "", "s", false));
        assertThrows(InvalidTableException.class, () -> manager.preview(connection, definition));
        actual = List.of(new Column("id", "uuid", "", "", true));
        assertThrows(InvalidTableException.class, () -> manager.preview(connection, definition));
    }
}
