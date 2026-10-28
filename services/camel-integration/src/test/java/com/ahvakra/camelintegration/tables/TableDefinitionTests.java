package com.ahvakra.camelintegration.tables;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ahvakra.camelintegration.connections.api.*;
import com.ahvakra.camelintegration.connections.domain.*;
import com.ahvakra.camelintegration.fields.api.*;
import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.tables.api.*;
import com.ahvakra.camelintegration.tables.application.TableDefinitionService;
import com.ahvakra.camelintegration.tables.domain.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
class TableDefinitionTests {

    @Autowired
    TableDefinitionController controller;

    @Autowired
    TableDefinitionStore store;

    @Autowired
    FieldDefinitionController fields;

    @Autowired
    FieldDefinitionStore fieldStore;

    @Autowired
    ConnectionController connections;

    @Autowired
    ConnectionStore connectionStore;

    private final List<UUID> tableIds = new ArrayList<>();
    private final List<UUID> fieldIds = new ArrayList<>();
    private final List<UUID> connectionIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        tableIds.forEach(id -> {
            if (store.findById(id).isPresent()) controller.delete(id);
        });
        fieldIds.forEach(id -> fields.delete(id));
        connectionIds.forEach(id -> connections.delete(id));
    }

    private UUID connection() {
        var id = connections
            .create(
                new ConnectionRequest(
                    "Test target",
                    "postgres",
                    Map.of(
                        "host",
                        "localhost",
                        "port",
                        "5432",
                        "database",
                        "test_target",
                        "username",
                        "test",
                        "sslMode",
                        "disable"
                    ),
                    "secret"
                )
            )
            .get("id");
        connectionIds.add(id);
        return id;
    }

    private UUID dictionary() {
        var id = fields
            .create(
                new FieldDefinitionRequest(
                    "Test CDR",
                    Map.of("caller_number", "string", "duration_seconds", "integer")
                )
            )
            .id();
        fieldIds.add(id);
        return id;
    }

    private TableDefinitionRequest request(
        UUID connectionId,
        UUID dictionaryId,
        List<String> selected
    ) {
        return new TableDefinitionRequest(
            "CDR target",
            connectionId,
            dictionaryId,
            "public",
            "cdr_records",
            selected
        );
    }

    private TableDefinitionResponse save(TableDefinitionRequest request) {
        var saved = controller.create(request);
        tableIds.add(saved.id());
        return saved;
    }

    @Test
    void cdrSampleIsAvailableAfterMigration() {
        var cdr = fieldStore
            .findById(UUID.fromString("cd000000-0000-4000-8000-000000000001"))
            .orElseThrow();
        assertEquals("CDR (Call Detail Record)", cdr.name());
        assertEquals("string", cdr.fields().get("caller_number"));
        assertEquals("integer", cdr.fields().get("duration_seconds"));
        assertEquals("datetime", cdr.fields().get("start_time"));
        assertEquals("decimal", cdr.fields().get("charge_amount"));
    }

    @Test
    void savesAllOrIndividualFieldsAndDerivesTypesWithoutCreatingTargetTable() {
        var connection = connection();
        var dictionary = dictionary();
        var saved = save(request(connection, dictionary, List.of("caller_number")));
        var persisted = store.findById(saved.id()).orElseThrow();
        assertEquals(Map.of("caller_number", "string"), persisted.columns());
        assertEquals(
            "CREATE TABLE \"public\".\"cdr_records\" (\n    \"caller_number\" TEXT\n);",
            saved.sql()
        );
        var all = new ArrayList<>(fieldStore.findById(dictionary).orElseThrow().fields().keySet());
        controller.update(saved.id(), request(connection, dictionary, all));
        var updated = store.findById(saved.id()).orElseThrow();
        assertEquals(fieldStore.findById(dictionary).orElseThrow().fields(), updated.columns());
        assertEquals(persisted.createdAt(), updated.createdAt());
        assertFalse(updated.updatedAt().isBefore(persisted.updatedAt()));
        controller.delete(saved.id());
        assertTrue(store.findById(saved.id()).isEmpty());
        assertThrows(TableNotFoundException.class, () -> controller.delete(saved.id()));
    }

    @Test
    void rejectsForeignEmptyDuplicateAndInvalidFieldSelections() {
        var connection = connection();
        var dictionary = dictionary();
        for (var selected : List.of(
            List.<String>of(),
            List.of("not_in_dictionary"),
            List.of("caller_number", "caller_number")
        )) {
            assertThrows(InvalidTableException.class, () ->
                controller.create(request(connection, dictionary, selected))
            );
        }
        assertThrows(InvalidTableException.class, () ->
            controller.create(
                new TableDefinitionRequest(
                    "CDR",
                    connection,
                    dictionary,
                    "public;DROP SCHEMA public",
                    "cdr",
                    List.of("id")
                )
            )
        );
        assertThrows(InvalidTableException.class, () ->
            controller.create(
                new TableDefinitionRequest(
                    "CDR",
                    connection,
                    dictionary,
                    "public",
                    "cdr\";DROP TABLE x",
                    List.of("id")
                )
            )
        );
        assertThrows(InvalidTableException.class, () ->
            controller.create(request(null, dictionary, List.of("id")))
        );
        assertThrows(InvalidTableException.class, () ->
            controller.create(request(connection, UUID.randomUUID(), List.of("id")))
        );
        var unsupported = UUID.randomUUID();
        connectionStore.save(
            new ConnectionDefinition(
                unsupported,
                "HTTP",
                "http",
                Map.of(),
                "",
                java.time.Instant.now(),
                java.time.Instant.now()
            )
        );
        connectionIds.add(unsupported);
        assertThrows(InvalidTableException.class, () ->
            controller.create(request(unsupported, dictionary, List.of("id")))
        );
    }

    @Test
    void selectedFieldKeysAndTypesCannotChangeButUnusedFieldsCan() {
        var dictionary = dictionary();
        save(request(connection(), dictionary, List.of("caller_number")));
        assertThrows(InvalidFieldsException.class, () ->
            fields.update(
                dictionary,
                new FieldDefinitionRequest("CDR", Map.of("duration_seconds", "integer"))
            )
        );
        assertThrows(InvalidFieldsException.class, () ->
            fields.update(
                dictionary,
                new FieldDefinitionRequest("CDR", Map.of("caller_number", "integer"))
            )
        );
        fields.update(
            dictionary,
            new FieldDefinitionRequest(
                "Renamed CDR",
                Map.of("caller_number", "string", "new_field", "boolean")
            )
        );
        assertEquals(
            "boolean",
            fieldStore.findById(dictionary).orElseThrow().fields().get("new_field")
        );
    }

    @Test
    void creationEndpointRevalidatesSavedFieldsAndConnectionBeforeCallingCreator()
        throws Exception {
        var connection = connection();
        var dictionary = dictionary();
        var saved = save(request(connection, dictionary, List.of("caller_number")));
        var creator = mock(TableCreator.class);
        var service = new TableDefinitionService(
            store,
            fieldStore,
            connectionStore,
            creator,
            mock(TableSchemaManager.class)
        );
        var mvc = MockMvcBuilders.standaloneSetup(new TableDefinitionController(service))
            .setControllerAdvice(new TablesExceptionHandler())
            .build();
        mvc.perform(post("/api/tables/" + saved.id() + "/create"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("PostgreSQL table created successfully."));
        verify(creator).create(
            connectionStore.findById(connection).orElseThrow(),
            store.findById(saved.id()).orElseThrow()
        );
        clearInvocations(creator);

        // Simulate stale storage that bypassed the definition-edit guard.
        var definition = fieldStore.findById(dictionary).orElseThrow();
        fieldStore.save(
            new FieldDefinition(
                definition.id(),
                definition.name(),
                Map.of("id", "uuid", "datetime", "datetime"),
                definition.createdAt(),
                definition.updatedAt()
            )
        );
        mvc.perform(post("/api/tables/" + saved.id() + "/create")).andExpect(
            status().isBadRequest()
        );
        connectionStore.save(
            new ConnectionDefinition(
                connection,
                "Changed type",
                "http",
                Map.of(),
                "",
                java.time.Instant.now(),
                java.time.Instant.now()
            )
        );
        mvc.perform(post("/api/tables/" + saved.id() + "/create")).andExpect(
            status().isBadRequest()
        );
        verifyNoInteractions(creator);
    }

    @Test
    void fieldMustBeReleasedFromEveryMappingBeforeEditingOrDeleting() throws Exception {
        var dictionary = dictionary();
        var first = save(request(connection(), dictionary, List.of("caller_number")));
        var second = save(request(connection(), dictionary, List.of("caller_number")));
        var response = fields
            .list()
            .stream()
            .filter(item -> item.id().equals(dictionary))
            .findFirst()
            .orElseThrow();
        assertEquals(2, response.usedBy().size());
        assertTrue(
            response
                .usedBy()
                .stream()
                .allMatch(reference -> reference.fields().containsKey("caller_number"))
        );
        var mvc = MockMvcBuilders.standaloneSetup(fields)
            .setControllerAdvice(new FieldsExceptionHandler())
            .build();
        String change = "{\"name\":\"CDR\",\"fields\":{\"caller_number\":\"integer\"}}";
        mvc.perform(
            put("/api/fields/" + dictionary)
                .contentType(MediaType.APPLICATION_JSON)
                .content(change)
        ).andExpect(status().isConflict());
        mvc.perform(delete("/api/fields/" + dictionary)).andExpect(status().isConflict());
        controller.update(first.id(), request(first.connectionId(), dictionary, List.of("id")));
        mvc.perform(
            put("/api/fields/" + dictionary)
                .contentType(MediaType.APPLICATION_JSON)
                .content(change)
        ).andExpect(status().isConflict());
        controller.update(second.id(), request(second.connectionId(), dictionary, List.of("id")));
        mvc.perform(
            put("/api/fields/" + dictionary)
                .contentType(MediaType.APPLICATION_JSON)
                .content(change)
        ).andExpect(status().isOk());
        controller.update(
            first.id(),
            request(first.connectionId(), dictionary, List.of("id", "caller_number"))
        );
        assertEquals(
            "integer",
            store.findById(first.id()).orElseThrow().columns().get("caller_number")
        );
        // Renaming a selected key is also a removal and must be rejected.
        mvc.perform(
            put("/api/fields/" + dictionary)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CDR\",\"fields\":{\"renamed\":\"integer\"}}")
        ).andExpect(status().isConflict());
        controller.delete(first.id());
        controller.delete(second.id());
        mvc.perform(delete("/api/fields/" + dictionary)).andExpect(status().isNoContent());
        fieldIds.remove(dictionary);
    }

    @Test
    void schemaEndpointsUseCurrentSavedDefinitionAndRejectMissingConfigurations() throws Exception {
        var saved = save(request(connection(), dictionary(), List.of("id")));
        var schema = mock(TableSchemaManager.class);
        var service = new TableDefinitionService(
            store,
            fieldStore,
            connectionStore,
            mock(TableCreator.class),
            schema
        );
        var definition = store.findById(saved.id()).orElseThrow();
        var connection = connectionStore.findById(saved.connectionId()).orElseThrow();
        when(schema.preview(connection, definition)).thenReturn(
            new TableSchemaPlan(
                "review-token",
                List.of(
                    new SchemaChange(
                        "drop:old",
                        "DROP",
                        "old",
                        "text",
                        null,
                        "ALTER TABLE ...",
                        true
                    )
                )
            )
        );
        var mvc = MockMvcBuilders.standaloneSetup(new TableDefinitionController(service))
            .setControllerAdvice(new TablesExceptionHandler())
            .build();
        mvc.perform(get("/api/tables/" + saved.id() + "/schema-plan"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fingerprint").value("review-token"))
            .andExpect(jsonPath("$.changes[0].destructive").value(true));
        mvc.perform(
            post("/api/tables/" + saved.id() + "/schema-changes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fingerprint\":\"review-token\",\"changeIds\":[\"drop:old\"],\"acknowledgeDataLoss\":true}"
                )
        ).andExpect(status().isOk());
        verify(schema).apply(
            connection,
            definition,
            new SchemaChangeSelection("review-token", List.of("drop:old"), true)
        );
        mvc.perform(get("/api/tables/" + UUID.randomUUID() + "/schema-plan")).andExpect(
            status().isNotFound()
        );
    }

    @Test
    void httpConflictsProtectReferencesAndDuplicateTargets() throws Exception {
        var connection = connection();
        var dictionary = dictionary();
        var saved = save(request(connection, dictionary, List.of("id")));
        var mvc = MockMvcBuilders.standaloneSetup(controller, fields, connections)
            .setControllerAdvice(
                new TablesExceptionHandler(),
                new FieldsExceptionHandler(),
                new ConnectionExceptionHandler()
            )
            .build();
        mvc.perform(get("/api/tables"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].columns.id").value("uuid"))
            .andExpect(jsonPath("$[0].sql").isNotEmpty());
        mvc.perform(delete("/api/fields/" + dictionary))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(delete("/api/connections/" + connection)).andExpect(status().isConflict());
        assertTrue(fieldStore.findById(dictionary).isPresent());
        assertTrue(connectionStore.findById(connection).isPresent());
        String body =
            "{\"name\":\"Duplicate\",\"connectionId\":\"" +
            connection +
            "\",\"fieldDefinitionId\":\"" +
            dictionary +
            "\",\"schemaName\":\"public\",\"tableName\":\"cdr_records\",\"fields\":[\"id\"]}";
        mvc.perform(
            post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isConflict());
        mvc.perform(
            post("/api/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("[\"id\"]", "[\"foreign_field\"]"))
        ).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tables/" + UUID.randomUUID() + "/create")).andExpect(
            status().isNotFound()
        );
        mvc.perform(delete("/api/tables/" + saved.id())).andExpect(status().isNoContent());
    }
}
