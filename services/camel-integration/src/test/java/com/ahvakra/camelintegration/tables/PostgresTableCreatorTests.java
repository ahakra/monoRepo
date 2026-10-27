package com.ahvakra.camelintegration.tables;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import com.ahvakra.camelintegration.connections.infrastructure.postgres.PostgresConnectionFactory;
import com.ahvakra.camelintegration.tables.domain.*;
import com.ahvakra.camelintegration.tables.infrastructure.postgres.PostgresTableCreator;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresTableCreatorTests {

    private final PostgresConnectionFactory factory = mock(PostgresConnectionFactory.class);
    private final PostgresTableCreator creator = new PostgresTableCreator(factory);
    private final ConnectionDefinition connection = new ConnectionDefinition(
        UUID.randomUUID(),
        "Target",
        "postgres",
        Map.of("database", "cdr"),
        "secret"
    );

    private TableDefinition definition(Map<String, String> columns) {
        return new TableDefinition(
            UUID.randomUUID(),
            "CDR",
            connection.id(),
            UUID.randomUUID(),
            "public",
            "cdr_records",
            columns,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    void mapsEveryLogicalTypeToPostgresWithoutImplicitConstraints() {
        var columns = new LinkedHashMap<String, String>();
        List.of(
            "string",
            "integer",
            "long",
            "decimal",
            "boolean",
            "date",
            "datetime",
            "uuid",
            "json"
        ).forEach(type -> columns.put("field_" + type, type));
        assertEquals(
            """
            CREATE TABLE "public"."cdr_records" (
                "field_string" TEXT,
                "field_integer" INTEGER,
                "field_long" BIGINT,
                "field_decimal" NUMERIC,
                "field_boolean" BOOLEAN,
                "field_date" DATE,
                "field_datetime" TIMESTAMP WITH TIME ZONE,
                "field_uuid" UUID,
                "field_json" JSONB
            );""",
            creator.sql(definition(columns))
        );
        assertThrows(InvalidTableException.class, () ->
            creator.sql(definition(Map.of("injected\"", "string")))
        );
        assertThrows(InvalidTableException.class, () ->
            creator.sql(definition(Map.of("valid", "TEXT; DROP TABLE x")))
        );
    }

    @Test
    void usesSavedConnectionAndClosesJdbcResources() throws Exception {
        var target = mock(Connection.class);
        var statement = mock(Statement.class);
        when(factory.open(connection, "cdr")).thenReturn(target);
        when(target.createStatement()).thenReturn(statement);
        var definition = definition(Map.of("caller_number", "string"));
        creator.create(connection, definition);
        verify(statement).setQueryTimeout(10);
        verify(statement).executeUpdate(creator.sql(definition));
        verify(statement).close();
        verify(target).close();
        verify(factory, times(1)).open(connection, "cdr");
    }

    @Test
    void existingTableIsAConflictAndDriverErrorsDoNotLeakCredentials() throws Exception {
        when(factory.open(connection, "cdr")).thenThrow(
            new SQLException("password=secret", "42P07")
        );
        var conflict = assertThrows(TableCreationException.class, () ->
            creator.create(connection, definition(Map.of("id", "uuid")))
        );
        assertTrue(conflict.conflict());
        assertFalse(conflict.getMessage().contains("secret"));
        doThrow(new SQLException("password=secret", "42501")).when(factory).open(connection, "cdr");
        var denied = assertThrows(TableCreationException.class, () ->
            creator.create(connection, definition(Map.of("id", "uuid")))
        );
        assertFalse(denied.conflict());
        assertTrue(denied.getMessage().contains("permission"));
        assertFalse(denied.getMessage().contains("secret"));
    }
}
