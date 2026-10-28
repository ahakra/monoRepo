package com.ahvakra.camelintegration.connections;

import static org.junit.jupiter.api.Assertions.*;

import com.ahvakra.camelintegration.connections.api.*;
import com.ahvakra.camelintegration.connections.domain.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ConnectionControllerTests {

    @Autowired
    ConnectionController controller;

    @Autowired
    ConnectionStore store;

    @Autowired
    jakarta.persistence.EntityManager entities;

    private ConnectionRequest input(String host, String password) {
        return new ConnectionRequest(
            "Analytics",
            "postgres",
            Map.of(
                "host",
                host,
                "port",
                "5432",
                "database",
                "camel_etl",
                "username",
                "user",
                "sslMode",
                "disable",
                "createDatabaseIfNotExists",
                "true"
            ),
            password
        );
    }

    @Test
    void savesUpdatesAndDeletesWithoutReturningCredentials() {
        var id = controller.create(input("localhost", "secret")).get("id");
        entities.flush();
        entities.clear();
        var saved = controller
            .list()
            .stream()
            .filter(item -> item.id().equals(id))
            .findFirst()
            .orElseThrow();
        assertTrue(saved.hasPassword());
        assertNotNull(saved.createdAt());
        assertNotNull(saved.updatedAt());
        assertEquals("true", saved.settings().get("createDatabaseIfNotExists"));
        assertFalse(saved.toString().contains("secret"));
        controller.update(id, input("database", ""));
        entities.flush();
        entities.clear();
        var updated = store.findById(id).orElseThrow();
        assertEquals(saved.createdAt(), updated.createdAt());
        assertFalse(updated.updatedAt().isBefore(saved.updatedAt()));
        assertEquals("secret", store.findById(id).orElseThrow().password());
        assertEquals("database", store.findById(id).orElseThrow().settings().get("host"));
        controller.update(id, input("database", "replacement"));
        assertEquals("replacement", store.findById(id).orElseThrow().password());
        controller.delete(id);
        assertTrue(store.findById(id).isEmpty());
        assertThrows(ConnectionNotFoundException.class, () ->
            controller.update(id, input("localhost", ""))
        );
        assertThrows(ConnectionNotFoundException.class, () -> controller.delete(id));
    }

    @Test
    void persistsAndReturnsAdvancedPostgresSettings() {
        var original = input("localhost", "secret");
        var settings = new java.util.HashMap<>(original.settings());
        settings.put("currentSchema", "analytics,public");
        settings.put("sslrootcert", "/opt/certs/root.crt");
        settings.put("connectTimeout", "30");
        settings.put("socketTimeout", "0");
        var id = controller
            .create(
                new ConnectionRequest(
                    original.name(),
                    original.type(),
                    settings,
                    original.password()
                )
            )
            .get("id");
        entities.flush();
        entities.clear();
        var saved = controller
            .list()
            .stream()
            .filter(item -> item.id().equals(id))
            .findFirst()
            .orElseThrow();
        assertEquals("analytics,public", saved.settings().get("currentSchema"));
        assertEquals("/opt/certs/root.crt", saved.settings().get("sslrootcert"));
        assertEquals("30", saved.settings().get("connectTimeout"));
        assertEquals("0", saved.settings().get("socketTimeout"));
        controller.update(id, input("localhost", ""));
        entities.flush();
        entities.clear();
        var updated = store.findById(id).orElseThrow();
        assertEquals(saved.createdAt(), updated.createdAt());
        assertEquals("5", updated.settings().get("connectTimeout"));
        assertEquals("10", updated.settings().get("socketTimeout"));
        assertFalse(updated.settings().containsKey("currentSchema"));
        assertEquals("secret", updated.password());
    }

    @Test
    void keepsUnknownLegacyCreationTimesUnknownAfterEditing() {
        var id = controller.create(input("localhost", "secret")).get("id");
        entities.flush();
        entities
            .createNativeQuery("UPDATE etl_connections SET created_at = NULL WHERE id = :id")
            .setParameter("id", id)
            .executeUpdate();
        entities.clear();
        assertNull(store.findById(id).orElseThrow().createdAt());
        controller.update(id, input("database", ""));
        entities.flush();
        entities.clear();
        assertNull(store.findById(id).orElseThrow().createdAt());
        assertNotNull(store.findById(id).orElseThrow().updatedAt());
    }

    @Test
    void rejectsJdbcParameterInjectionAndUnsupportedTypes() {
        assertThrows(InvalidConnectionException.class, () ->
            controller.create(input("localhost/test?user=other", ""))
        );
        assertThrows(InvalidConnectionException.class, () ->
            controller.create(new ConnectionRequest("Test", "mysql", Map.of(), ""))
        );
    }
}
