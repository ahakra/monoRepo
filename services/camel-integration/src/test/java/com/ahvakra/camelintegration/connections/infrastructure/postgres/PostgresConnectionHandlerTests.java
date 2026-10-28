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
            "secret",
            java.time.Instant.now(),
            java.time.Instant.now()
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
    void validatesAdvancedPropertiesAndDefaultsOlderDefinitions() {
        var base = definition(false).settings();
        var defaults = handler.validateSettings(base);
        assertEquals("5", defaults.get("connectTimeout"));
        assertEquals("10", defaults.get("socketTimeout"));
        var configured = new java.util.HashMap<>(base);
        configured.put("connectTimeout", "30");
        configured.put("socketTimeout", "0");
        configured.put("currentSchema", "\"Customer Data\",public");
        configured.put("sslrootcert", " /opt/certs/root.crt ");
        var validated = handler.validateSettings(configured);
        assertEquals("30", validated.get("connectTimeout"));
        assertEquals("0", validated.get("socketTimeout"));
        assertEquals(configured.get("currentSchema"), validated.get("currentSchema"));
        assertEquals(configured.get("sslrootcert"), validated.get("sslrootcert"));
        for (String key : new String[] { "connectTimeout", "socketTimeout" }) {
            for (String invalid : new String[] { "-1", "1.5", "invalid", "2147485" }) {
                var settings = new java.util.HashMap<>(base);
                settings.put(key, invalid);
                assertThrows(InvalidConnectionException.class, () ->
                    handler.validateSettings(settings)
                );
            }
        }
        configured.put("currentSchema", " ");
        configured.put("sslrootcert", "");
        configured.put("connectTimeout", "");
        configured.put("socketTimeout", " ");
        var optional = handler.validateSettings(configured);
        assertFalse(optional.containsKey("currentSchema"));
        assertFalse(optional.containsKey("sslrootcert"));
        assertEquals("5", optional.get("connectTimeout"));
        assertEquals("10", optional.get("socketTimeout"));
        for (String key : new String[] { "currentSchema", "sslrootcert" }) {
            for (String invalid : new String[] { "x".repeat(2049), "bad\0value" }) {
                var settings = new java.util.HashMap<>(base);
                settings.put(key, invalid);
                assertThrows(InvalidConnectionException.class, () ->
                    handler.validateSettings(settings)
                );
            }
        }
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
