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
        var saved = controller
            .list()
            .stream()
            .filter(item -> item.id().equals(id))
            .findFirst()
            .orElseThrow();
        assertTrue(saved.hasPassword());
        assertEquals("true", saved.settings().get("createDatabaseIfNotExists"));
        assertFalse(saved.toString().contains("secret"));
        controller.update(id, input("database", ""));
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
    void rejectsJdbcParameterInjectionAndUnsupportedTypes() {
        assertThrows(InvalidConnectionException.class, () ->
            controller.create(input("localhost/test?user=other", ""))
        );
        assertThrows(InvalidConnectionException.class, () ->
            controller.create(new ConnectionRequest("Test", "mysql", Map.of(), ""))
        );
    }
}
