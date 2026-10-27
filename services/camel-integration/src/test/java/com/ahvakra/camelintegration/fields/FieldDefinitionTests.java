package com.ahvakra.camelintegration.fields;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ahvakra.camelintegration.fields.api.*;
import com.ahvakra.camelintegration.fields.domain.*;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FieldDefinitionTests {

    @Autowired
    FieldDefinitionController controller;

    @Autowired
    FieldDefinitionStore store;

    @Autowired
    EntityManager entities;

    @Test
    void persistsDictionaryDefaultsAndTimestampsAcrossEditsAndDeletes() {
        var created = controller.create(
            new FieldDefinitionRequest(" Customer ", Map.of("name", "string", "lastname", "string"))
        );
        entities.flush();
        entities.clear();
        var saved = store.findById(created.id()).orElseThrow();
        assertEquals("Customer", saved.name());
        assertEquals(
            Map.of("id", "uuid", "datetime", "datetime", "name", "string", "lastname", "string"),
            saved.fields()
        );
        assertNotNull(saved.createdAt());
        assertNotNull(saved.updatedAt());
        assertEquals(created.id(), controller.list().getFirst().id());

        controller.update(
            created.id(),
            new FieldDefinitionRequest("Customers", Map.of("age", "integer"))
        );
        entities.flush();
        entities.clear();
        var updated = store.findById(created.id()).orElseThrow();
        assertEquals(saved.createdAt(), updated.createdAt());
        assertFalse(updated.updatedAt().isBefore(saved.updatedAt()));
        assertEquals(
            Map.of("id", "uuid", "datetime", "datetime", "age", "integer"),
            updated.fields()
        );
        assertEquals("Customers", updated.name());

        controller.delete(created.id());
        entities.flush();
        entities.clear();
        assertTrue(store.findById(created.id()).isEmpty());
        assertEquals(
            0L,
            (
                (Number) entities
                    .createNativeQuery(
                        "SELECT COUNT(*) FROM etl_field_entries WHERE definition_id = :id"
                    )
                    .setParameter("id", created.id())
                    .getSingleResult()
            ).longValue()
        );
        assertThrows(FieldsNotFoundException.class, () -> controller.delete(created.id()));
        assertThrows(FieldsNotFoundException.class, () ->
            controller.update(created.id(), new FieldDefinitionRequest("Missing", Map.of()))
        );
    }

    @Test
    void validatesTypesKeysDefaultsAndDefinitionLimits() {
        for (String type : new String[] {
            "string",
            "integer",
            "long",
            "decimal",
            "boolean",
            "date",
            "datetime",
            "uuid",
            "json",
        }) {
            assertEquals(
                type,
                controller
                    .create(new FieldDefinitionRequest(type, Map.of("custom", type)))
                    .fields()
                    .get("custom")
            );
        }
        for (String key : new String[] {
            "",
            "first name",
            "Name",
            "1name",
            "name;DROP TABLE x",
            "a".repeat(64),
        }) {
            assertThrows(InvalidFieldsException.class, () ->
                controller.create(new FieldDefinitionRequest("Invalid", Map.of(key, "string")))
            );
        }
        assertThrows(InvalidFieldsException.class, () ->
            controller.create(new FieldDefinitionRequest("Invalid", Map.of("name", "VARCHAR(255)")))
        );
        assertThrows(InvalidFieldsException.class, () ->
            controller.create(new FieldDefinitionRequest("Invalid", Map.of("id", "string")))
        );
        assertThrows(InvalidFieldsException.class, () ->
            controller.create(new FieldDefinitionRequest("Invalid", Map.of("datetime", "date")))
        );
        assertThrows(InvalidFieldsException.class, () ->
            controller.create(new FieldDefinitionRequest(" ", Map.of()))
        );
        assertThrows(InvalidFieldsException.class, () ->
            controller.create(new FieldDefinitionRequest("x".repeat(121), Map.of()))
        );
        assertThrows(InvalidFieldsException.class, () ->
            controller.create(new FieldDefinitionRequest("Invalid", null))
        );
        var nullType = new HashMap<String, String>();
        nullType.put("name", null);
        assertThrows(InvalidFieldsException.class, () ->
            controller.create(new FieldDefinitionRequest("Invalid", nullType))
        );
        var tooMany = new HashMap<String, String>();
        for (int i = 0; i < 98; i++) tooMany.put("field_" + i, "string");
        assertEquals(
            100,
            controller.create(new FieldDefinitionRequest("Limit", tooMany)).fields().size()
        );
        tooMany.put("extra", "string");
        assertThrows(InvalidFieldsException.class, () ->
            controller.create(new FieldDefinitionRequest("Invalid", tooMany))
        );
    }

    @Test
    void exposesCrudAndUsefulHttpValidationErrors() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new FieldsExceptionHandler())
            .build();
        mvc.perform(
            post("/api/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Customer", "fields":{"name":"string", "lastname":"string"}}
                    """
                )
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.fields.id").value("uuid"))
            .andExpect(jsonPath("$.fields.datetime").value("datetime"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());
        var id = store.findAll().getFirst().id();
        mvc.perform(get("/api/fields"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fields.lastname").value("string"));
        mvc.perform(
            put("/api/fields/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Renamed", "fields":{"active":"boolean"}}
                    """
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed"));
        mvc.perform(
            post("/api/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Invalid", "fields":{"id":"string"}}
                    """
                )
        )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.detail").value(
                    "Default fields must keep their types: id (uuid) and datetime (datetime)."
                )
            );
        mvc.perform(
            post("/api/fields").contentType(MediaType.APPLICATION_JSON).content("{broken")
        ).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/fields/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/fields/" + UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Field definition not found."));
    }
}
