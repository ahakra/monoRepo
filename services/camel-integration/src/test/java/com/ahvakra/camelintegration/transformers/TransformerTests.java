package com.ahvakra.camelintegration.transformers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ahvakra.camelintegration.fields.application.*;
import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.transformers.api.*;
import com.ahvakra.camelintegration.transformers.application.*;
import com.ahvakra.camelintegration.transformers.domain.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Transactional
class TransformerTests {

    @Autowired
    TransformerService service;

    @Autowired
    TransformerStore store;

    @Autowired
    TransformerController controller;

    @Autowired
    FieldDefinitionService fields;

    @Autowired
    EntityManager entities;

    @Autowired
    JsonMapper json;

    private FieldDefinition fields() {
        return fields.create(
            new FieldDefinitionCommand("Calls", Map.of("caller", "string", "duration", "long"))
        );
    }

    private TransformerCommand.Port list(String name, UUID id) {
        return new TransformerCommand.Port(name, TransformerPortKind.FIELD_LIST, id);
    }

    private TransformerCommand.Port variable(String name) {
        return new TransformerCommand.Port(name, TransformerPortKind.DYNAMIC_VARIABLE, null);
    }

    private TransformerCommand command(
        List<TransformerCommand.Port> inputs,
        List<TransformerCommand.Port> outputs
    ) {
        return new TransformerCommand(
            " Enrich calls ",
            "Multiple inputs and outputs",
            "/opt/WASM modules",
            "enrich_calls.wasm",
            inputs,
            outputs
        );
    }

    @Test
    void validatesAndUpdatesWasmDirectoryAndFilenameLikeMappers() {
        var ports = List.of(variable("value"));
        for (String directory : new String[] { null, "", " ", "x".repeat(2049), "bad\0path" }) {
            assertThrows(InvalidTransformerException.class, () ->
                service.save(
                    null,
                    new TransformerCommand(
                        "Transformer",
                        "",
                        directory,
                        "transform.wasm",
                        ports,
                        ports
                    )
                )
            );
        }
        for (String filename : new String[] {
            null,
            "",
            " ",
            "file.txt",
            "../file.wasm",
            "dir/file.wasm",
            "dir\\file.wasm",
            "bad\nfile.wasm",
            "x".repeat(251) + ".wasm",
        }) {
            assertThrows(InvalidTransformerException.class, () ->
                service.save(
                    null,
                    new TransformerCommand(
                        "Transformer",
                        "",
                        "/opt/transformers",
                        filename,
                        ports,
                        ports
                    )
                )
            );
        }
        var created = service.save(null, command(ports, ports));
        service.save(
            created.id(),
            new TransformerCommand(
                "Transformer",
                "Updated module",
                "/opt/WASM modules/v2",
                "Transform V2.wasm",
                ports,
                ports
            )
        );
        entities.flush();
        entities.clear();
        var loaded = store.findById(created.id()).orElseThrow();
        assertEquals("/opt/WASM modules/v2", loaded.directory());
        assertEquals("Transform V2.wasm", loaded.wasmFileName());
    }

    @Test
    void persistsMixedPortsAndSupportsReplacementReorderingAndDeletion() {
        var definition = fields();
        var created = service.save(
            null,
            command(
                List.of(list("Calls", definition.id()), variable("threshold")),
                List.of(list("Calls", definition.id()), variable("count"))
            )
        );
        entities.flush();
        entities.clear();
        var loaded = store.findById(created.id()).orElseThrow();
        assertEquals("Enrich calls", loaded.name());
        assertEquals("/opt/WASM modules", loaded.directory());
        assertEquals("enrich_calls.wasm", loaded.wasmFileName());
        assertEquals(
            List.of("Calls", "threshold"),
            loaded.inputs().stream().map(TransformerPort::name).toList()
        );
        assertEquals(
            List.of("Calls", "count"),
            loaded.outputs().stream().map(TransformerPort::name).toList()
        );
        assertEquals(definition.fields(), loaded.inputs().getFirst().fields());
        assertEquals(TransformerPortKind.DYNAMIC_VARIABLE, loaded.inputs().get(1).kind());
        assertNull(loaded.inputs().get(1).fieldDefinitionId());
        assertTrue(loaded.inputs().get(1).fields().isEmpty());
        var swapped = service.save(
            created.id(),
            command(
                List.of(variable("threshold"), list("Calls", definition.id())),
                List.of(variable("count"), list("Calls", definition.id()))
            )
        );
        entities.flush();
        entities.clear();
        assertEquals(loaded.createdAt(), swapped.createdAt());
        var reordered = store.findById(created.id()).orElseThrow();
        assertEquals(
            List.of("threshold", "Calls"),
            reordered.inputs().stream().map(TransformerPort::name).toList()
        );
        assertEquals(
            List.of("count", "Calls"),
            reordered.outputs().stream().map(TransformerPort::name).toList()
        );
        service.save(created.id(), command(List.of(variable("value")), List.of(variable("value"))));
        entities.flush();
        entities.clear();
        assertEquals(1, store.findById(created.id()).orElseThrow().inputs().size());
        assertTrue(store.findByFieldDefinitionId(definition.id()).isEmpty());
        service.delete(created.id());
        entities.flush();
        entities.clear();
        for (String table : List.of("etl_transformer_inputs", "etl_transformer_outputs")) {
            assertEquals(
                0L,
                (
                    (Number) entities
                        .createNativeQuery(
                            "SELECT COUNT(*) FROM " + table + " WHERE transformer_id = :id"
                        )
                        .setParameter("id", created.id())
                        .getSingleResult()
                ).longValue()
            );
        }
        assertThrows(TransformerNotFoundException.class, () -> service.delete(created.id()));
    }

    @Test
    void protectsFieldsUsedOnEitherSideAndReleasesReferencesWhenSwitchedToVariables() {
        var inputType = fields();
        var outputType = fields();
        var created = service.save(
            null,
            command(List.of(list("in", inputType.id())), List.of(list("out", outputType.id())))
        );
        for (var definition : List.of(inputType, outputType)) {
            assertThrows(FieldsInUseException.class, () -> fields.delete(definition.id()));
            assertThrows(FieldsInUseException.class, () ->
                fields.update(
                    definition.id(),
                    new FieldDefinitionCommand(
                        "Calls",
                        Map.of("caller", "integer", "duration", "long")
                    )
                )
            );
            assertFalse(fields.references(definition.id()).isEmpty());
            assertTrue(
                fields.references(definition.id()).getFirst().tableName().startsWith("Transformer:")
            );
        }
        service.save(created.id(), command(List.of(variable("in")), List.of(variable("out"))));
        entities.flush();
        entities.clear();
        assertTrue(fields.references(inputType.id()).isEmpty());
        assertTrue(fields.references(outputType.id()).isEmpty());
        fields.delete(inputType.id());
        fields.delete(outputType.id());
    }

    @Test
    void validatesNamesKindsAndReferencesAndAllowsVariablesWithoutAnyFields() {
        var port = variable("value");
        assertNotNull(service.save(null, command(List.of(port), List.of(port))).id());
        assertNotNull(
            service
                .save(null, command(List.of(variable("Calls"), variable("calls")), List.of(port)))
                .id()
        );
        assertThrows(InvalidTransformerException.class, () -> service.save(null, null));
        assertThrows(InvalidTransformerException.class, () ->
            service.save(null, command(List.of(), List.of(port)))
        );
        assertThrows(InvalidTransformerException.class, () ->
            service.save(null, command(List.of(port), List.of()))
        );
        assertThrows(InvalidTransformerException.class, () ->
            service.save(null, command(List.of(port, port), List.of(port)))
        );
        assertThrows(InvalidTransformerException.class, () ->
            service.save(null, command(List.of(port), List.of(port, port)))
        );
        for (var invalid : List.of(
            variable("has space"),
            variable("\tvalue"),
            variable(""),
            variable("x".repeat(121)),
            new TransformerCommand.Port("x", null, null),
            list("x", null),
            new TransformerCommand.Port(
                "x",
                TransformerPortKind.DYNAMIC_VARIABLE,
                UUID.randomUUID()
            )
        )) {
            assertThrows(InvalidTransformerException.class, () ->
                service.save(null, command(List.of(invalid), List.of(port)))
            );
            assertThrows(InvalidTransformerException.class, () ->
                service.save(null, command(List.of(port), List.of(invalid)))
            );
        }
        assertThrows(FieldsNotFoundException.class, () ->
            service.save(null, command(List.of(list("x", UUID.randomUUID())), List.of(port)))
        );
    }

    @Test
    void exposesCrudAndRejectsInvalidApiConfigurations() throws Exception {
        var definition = fields();
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TransformersExceptionHandler())
            .build();
        String body = json.writeValueAsString(
            command(
                List.of(list("Calls", definition.id()), variable("threshold")),
                List.of(variable("total"))
            )
        );
        mvc.perform(post("/api/transformers").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.directory").value("/opt/WASM modules"))
            .andExpect(jsonPath("$.wasmFileName").value("enrich_calls.wasm"))
            .andExpect(jsonPath("$.inputs[0].kind").value("FIELD_LIST"))
            .andExpect(jsonPath("$.inputs[0].fields.caller").value("string"))
            .andExpect(jsonPath("$.outputs[0].kind").value("DYNAMIC_VARIABLE"));
        var id = service.list().getFirst().id();
        mvc.perform(get("/api/transformers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Enrich calls"));
        mvc.perform(
            put("/api/transformers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("Enrich calls", "Updated"))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated"));
        mvc.perform(
            post("/api/transformers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("enrich_calls.wasm", "enrich_calls.txt"))
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(
            post("/api/transformers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("FIELD_LIST", "UNKNOWN"))
        ).andExpect(status().isBadRequest());
        mvc.perform(post("/api/transformers").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(delete("/api/transformers/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/transformers/" + id)).andExpect(status().isNotFound());
    }
}
