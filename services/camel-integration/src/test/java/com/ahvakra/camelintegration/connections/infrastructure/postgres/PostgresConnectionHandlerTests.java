package com.ahvakra.camelintegration.connections.infrastructure.postgres;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ahvakra.camelintegration.connections.domain.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresConnectionHandlerTests {

    private final PostgresConnectionFactory factory = mock(PostgresConnectionFactory.class);
    private final PostgresConnectionHandler handler = new PostgresConnectionHandler(factory);

    private ConnectionDefinition definition(boolean create) {
        return new ConnectionDefinition(
            UUID.randomUUID(),
            "Target",
            "postgres",
            Map.of(
                "host",
                "localhost",
                "port",
                "5432",
                "database",
                "etl-target",
                "username",
                "user",
                "sslMode",
                "disable",
                "createDatabaseIfNotExists",
                Boolean.toString(create)
            ),
            "secret"
        );
    }

    @Test
    void existingDatabaseDoesNotNeedCreationPrivileges() throws Exception {
        var definition = definition(true);
        var target = mock(Connection.class);
        when(factory.open(definition, "etl-target")).thenReturn(target);
        when(target.isValid(5)).thenReturn(true);
        assertTrue(handler.test(definition).success());
        verify(factory, never()).open(definition, "postgres");
        verify(target).close();
    }

    @Test
    void missingDatabaseIsCreatedAndRetested() throws Exception {
        var definition = definition(true);
        var target = mock(Connection.class);
        var admin = mock(Connection.class);
        var statement = mock(Statement.class);
        when(factory.open(definition, "etl-target"))
            .thenThrow(new SQLException("missing", "3D000"))
            .thenReturn(target);
        when(factory.open(definition, "postgres")).thenReturn(admin);
        when(admin.createStatement()).thenReturn(statement);
        when(target.isValid(5)).thenReturn(true);
        assertTrue(handler.test(definition).success());
        verify(admin).setAutoCommit(true);
        verify(statement).executeUpdate("CREATE DATABASE \"etl-target\"");
        verify(factory, times(2)).open(definition, "etl-target");
        verify(admin).close();
        verify(target).close();
    }

    @Test
    void missingDatabaseIsNotCreatedWithoutOptIn() throws Exception {
        var definition = definition(false);
        when(factory.open(definition, "etl-target")).thenThrow(
            new SQLException("missing", "3D000")
        );
        assertFalse(handler.test(definition).success());
        verify(factory, never()).open(definition, "postgres");
    }

    @Test
    void authenticationFailureNeverAttemptsCreationOrLeaksDriverMessage() throws Exception {
        var definition = definition(true);
        when(factory.open(definition, "etl-target")).thenThrow(new SQLException("secret", "28P01"));
        var result = handler.test(definition);
        assertFalse(result.success());
        assertFalse(result.message().contains("secret"));
        verify(factory, never()).open(definition, "postgres");
    }

    @Test
    void concurrentCreationIsRetested() throws Exception {
        var definition = definition(true);
        var target = mock(Connection.class);
        var admin = mock(Connection.class);
        var statement = mock(Statement.class);
        when(factory.open(definition, "etl-target"))
            .thenThrow(new SQLException("missing", "3D000"))
            .thenReturn(target);
        when(factory.open(definition, "postgres")).thenReturn(admin);
        when(admin.createStatement()).thenReturn(statement);
        when(statement.executeUpdate(anyString())).thenThrow(new SQLException("exists", "42P04"));
        when(target.isValid(5)).thenReturn(true);
        assertTrue(handler.test(definition).success());
    }

    @Test
    void rejectsUnsafeOrTruncatedDatabaseNames() {
        var settings = new java.util.HashMap<>(definition(true).settings());
        settings.put("database", "db?sslmode=disable");
        assertThrows(InvalidConnectionException.class, () -> handler.validateSettings(settings));
        settings.put("database", "a".repeat(64));
        assertThrows(InvalidConnectionException.class, () -> handler.validateSettings(settings));
    }
}
