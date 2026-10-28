package com.ahvakra.camelintegration.connections.infrastructure.postgres;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PostgresConnectionFactoryTests {

    private final PostgresConnectionFactory factory = new PostgresConnectionFactory();

    private Properties open(Map<String, String> extra) throws Exception {
        var settings = new HashMap<>(
            Map.of(
                "host",
                "localhost",
                "port",
                "5432",
                "database",
                "analytics",
                "username",
                "etl_user",
                "sslMode",
                "verify-full"
            )
        );
        settings.putAll(extra);
        var definition = new ConnectionDefinition(
            UUID.randomUUID(),
            "Analytics",
            "postgres",
            settings,
            "test-password",
            Instant.now(),
            Instant.now()
        );
        try (var driver = mockStatic(DriverManager.class)) {
            var connection = mock(Connection.class);
            driver
                .when(() -> DriverManager.getConnection(anyString(), any(Properties.class)))
                .thenReturn(connection);
            assertSame(connection, factory.open(definition, "analytics"));
            var properties = ArgumentCaptor.forClass(Properties.class);
            driver.verify(() ->
                DriverManager.getConnection(
                    eq("jdbc:postgresql://localhost:5432/analytics"),
                    properties.capture()
                )
            );
            return properties.getValue();
        }
    }

    @Test
    void passesConfiguredPropertiesToThePostgresDriverInsteadOfFixedTimeouts() throws Exception {
        var properties = open(
            Map.of(
                "connectTimeout",
                "30",
                "socketTimeout",
                "120",
                "currentSchema",
                "\"Customer Data\",public",
                "sslrootcert",
                "/opt/SSL Certificates/root.crt"
            )
        );
        assertEquals("30", properties.getProperty("connectTimeout"));
        assertEquals("120", properties.getProperty("socketTimeout"));
        assertEquals("\"Customer Data\",public", properties.getProperty("currentSchema"));
        assertEquals("/opt/SSL Certificates/root.crt", properties.getProperty("sslrootcert"));
        assertEquals("verify-full", properties.getProperty("sslmode"));
        assertEquals("etl_user", properties.getProperty("user"));
        assertEquals("test-password", properties.getProperty("password"));
    }

    @Test
    void preservesLegacyDefaultsAndAllowsExplicitlyDisabledTimeouts() throws Exception {
        var legacy = open(Map.of());
        assertEquals("5", legacy.getProperty("connectTimeout"));
        assertEquals("10", legacy.getProperty("socketTimeout"));
        assertFalse(legacy.containsKey("currentSchema"));
        assertFalse(legacy.containsKey("sslrootcert"));
        var disabled = open(
            Map.of(
                "connectTimeout",
                "0",
                "socketTimeout",
                "0",
                "currentSchema",
                "",
                "sslrootcert",
                ""
            )
        );
        assertEquals("0", disabled.getProperty("connectTimeout"));
        assertEquals("0", disabled.getProperty("socketTimeout"));
        assertFalse(disabled.containsKey("currentSchema"));
        assertFalse(disabled.containsKey("sslrootcert"));
    }
}
