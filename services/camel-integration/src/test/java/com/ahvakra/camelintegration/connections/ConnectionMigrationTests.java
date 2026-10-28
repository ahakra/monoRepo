package com.ahvakra.camelintegration.connections;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ConnectionMigrationTests {

    @Test
    void migratesExistingConnectionsOnceWithoutResurrectingDeletedRows() throws Exception {
        String url =
            "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        var id = UUID.randomUUID();
        try (
            var connection = DriverManager.getConnection(url, "sa", "");
            var statement = connection.createStatement()
        ) {
            statement.execute(
                "CREATE TABLE integration_connections (id UUID PRIMARY KEY, name VARCHAR(120), host VARCHAR(253), port INTEGER, database_name VARCHAR(120), username VARCHAR(120), password TEXT, ssl_mode VARCHAR(20), updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)"
            );
            try (
                var insert = connection.prepareStatement(
                    "INSERT INTO integration_connections (id,name,host,port,database_name,username,password,ssl_mode) VALUES (?, 'Legacy', 'localhost', 5432, 'analytics', 'user', 'secret', 'disable')"
                )
            ) {
                insert.setObject(1, id);
                insert.executeUpdate();
            }
        }
        var flyway = Flyway.configure()
            .dataSource(url, "sa", "")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
        flyway.migrate();
        try (
            var connection = DriverManager.getConnection(url, "sa", "");
            var statement = connection.createStatement()
        ) {
            try (
                var rows = statement.executeQuery(
                    "SELECT type,password,created_at FROM etl_connections"
                )
            ) {
                assertTrue(rows.next());
                assertEquals("postgres", rows.getString(1));
                assertEquals("secret", rows.getString(2));
                assertNull(rows.getObject(3));
            }
            try (
                var rows = statement.executeQuery(
                    "SELECT setting_value FROM etl_connection_settings WHERE setting_key='database'"
                )
            ) {
                assertTrue(rows.next());
                assertEquals("analytics", rows.getString(1));
            }
            statement.executeUpdate("DELETE FROM etl_connections");
        }
        flyway.migrate();
        try (
            var connection = DriverManager.getConnection(url, "sa", "");
            var statement = connection.createStatement();
            var rows = statement.executeQuery("SELECT COUNT(*) FROM etl_connections")
        ) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }
}
