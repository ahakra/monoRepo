package com.ahvakra.camelintegration.mappers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ahvakra.camelintegration.fields.application.*;
import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.mappers.api.*;
import com.ahvakra.camelintegration.mappers.application.*;
import com.ahvakra.camelintegration.mappers.domain.*;
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
class MapperTests {

    @Autowired
    MapperService service;

    @Autowired
    MapperStore store;

    @Autowired
    MapperController controller;

    @Autowired
    FieldDefinitionService fields;

    @Autowired
    EntityManager entities;

    @Autowired
    JsonMapper json;

    private FieldDefinition definition(String name) {
        return fields.create(
            new FieldDefinitionCommand(name, Map.of("label", "string", "count", "integer"))
        );
    }

    private MapperCommand command(List<MapperCommand.Output> outputs) {
        return new MapperCommand(
            " CDR mapper ",
            "Parse call records",
            "/opt/WASM modules",
            "cdr_parser.wasm",
            outputs
        );
    }

    private MapperCommand.Output output(String name, UUID id) {
        return new MapperCommand.Output(name, id);
    }

    @Test
    void persistsMultipleTypedOutputsAndPreservesExactNamesDirectoryAndFilename() {
        var calls = definition("Calls");
        var customers = definition("Customers");
        var created = service.save(
            null,
            command(List.of(output("CallRecords", calls.id()), output("customers", customers.id())))
        );
        entities.flush();
        entities.clear();
        var loaded = store.findById(created.id()).orElseThrow();
        assertEquals("CDR mapper", loaded.name());
        assertEquals("Parse call records", loaded.description());
        assertEquals("/opt/WASM modules", loaded.directory());
        assertEquals("cdr_parser.wasm", loaded.wasmFileName());
        assertEquals(
            List.of("CallRecords", "customers"),
            loaded.outputs().stream().map(MapperOutput::name).toList()
        );
        assertEquals(calls.id(), loaded.outputs().getFirst().fieldDefinitionId());
        assertEquals(calls.fields(), loaded.outputs().getFirst().fields());
        assertEquals(customers.fields(), loaded.outputs().get(1).fields());
        assertTrue(
            store
                .findByFieldDefinitionId(calls.id())
                .stream()
                .anyMatch(mapper -> mapper.id().equals(created.id()))
        );
        assertEquals(created.id(), service.list().getFirst().id());
    }

    @Test
    void supportsReorderingRenamingAndRemovingOutputsAcrossEditsAndDeletes() {
        var definition = definition("Calls");
        var created = service.save(
            null,
            command(List.of(output("First", definition.id()), output("Second", definition.id())))
        );
        entities.flush();
        entities.clear();
        var persisted = store.findById(created.id()).orElseThrow();
        var swapped = service.save(
            created.id(),
            command(List.of(output("Second", definition.id()), output("First", definition.id())))
        );
        entities.flush();
        entities.clear();
        assertEquals(
            List.of("Second", "First"),
            store
                .findById(created.id())
                .orElseThrow()
                .outputs()
                .stream()
                .map(MapperOutput::name)
                .toList()
        );
        assertEquals(persisted.createdAt(), swapped.createdAt());
        service.save(created.id(), command(List.of(output("Renamed", definition.id()))));
        entities.flush();
        entities.clear();
        assertEquals(1, store.findById(created.id()).orElseThrow().outputs().size());
        service.delete(created.id());
        entities.flush();
        entities.clear();
        assertTrue(store.findById(created.id()).isEmpty());
        assertEquals(
            0L,
            (
                (Number) entities
                    .createNativeQuery(
                        "SELECT COUNT(*) FROM etl_mapper_outputs WHERE mapper_id = :id"
                    )
                    .setParameter("id", created.id())
                    .getSingleResult()
            ).longValue()
        );
        assertThrows(MapperNotFoundException.class, () -> service.delete(created.id()));
    }

    @Test
    void rejectsWhitespaceDuplicatesMissingFieldsAndInvalidWasmLocations() {
        var definition = definition("Calls");
        for (String name : new String[] {
            "",
            " ",
            "Call Records",
            " Leading",
            "Trailing ",
            "tab\tname",
            "line\nbreak",
            "non\u00a0breaking",
            "control\0name",
            "x".repeat(121),
        })
            assertThrows(
                InvalidMapperException.class,
                () -> service.save(null, command(List.of(output(name, definition.id())))),
                name
            );
        assertThrows(InvalidMapperException.class, () ->
            service.save(
                null,
                command(List.of(output("Calls", definition.id()), output("Calls", definition.id())))
            )
        );
        assertThrows(InvalidMapperException.class, () -> service.save(null, command(List.of())));
        assertThrows(InvalidMapperException.class, () ->
            service.save(null, command(Collections.nCopies(101, output("Calls", definition.id()))))
        );
        assertThrows(InvalidMapperException.class, () ->
            service.save(null, command(List.of(output("Calls", null))))
        );
        assertThrows(FieldsNotFoundException.class, () ->
            service.save(null, command(List.of(output("Calls", UUID.randomUUID()))))
        );
        assertThrows(InvalidMapperException.class, () -> service.save(null, null));
        for (String filename : new String[] {
            "",
            "parser.txt",
            "../parser.wasm",
            "dir/parser.wasm",
            "dir\\parser.wasm",
            "x".repeat(251) + ".wasm",
        })
            assertThrows(InvalidMapperException.class, () ->
                service.save(
                    null,
                    new MapperCommand(
                        "Mapper",
                        "",
                        "/opt/mappers",
                        filename,
                        List.of(output("Calls", definition.id()))
                    )
                )
            );
        assertThrows(InvalidMapperException.class, () ->
            service.save(
                null,
                new MapperCommand(
                    "Mapper",
                    "",
                    " ",
                    "parser.wasm",
                    List.of(output("Calls", definition.id()))
                )
            )
        );
        // Exact case matters: these are distinct output contracts.
        var cases = service.save(
            null,
            command(List.of(output("Calls", definition.id()), output("calls", definition.id())))
        );
        assertEquals(
            List.of("Calls", "calls"),
            cases.outputs().stream().map(MapperOutput::name).toList()
        );
    }

    @Test
    void protectsOutputSchemaTypesAndReleasesDefinitionsWhenOutputsChange() {
        var first = definition("First");
        var second = definition("Second");
        var created = service.save(null, command(List.of(output("CallRecords", first.id()))));
        assertThrows(FieldsInUseException.class, () -> fields.delete(first.id()));
        assertThrows(FieldsInUseException.class, () ->
            fields.update(
                first.id(),
                new FieldDefinitionCommand("First", Map.of("label", "integer", "count", "integer"))
            )
        );
        assertThrows(FieldsInUseException.class, () ->
            fields.update(
                first.id(),
                new FieldDefinitionCommand("First", Map.of("label", "string"))
            )
        );
        var refs = fields.references(first.id());
        assertEquals("Mapper: CDR mapper / CallRecords", refs.getFirst().tableName());
        assertEquals(first.fields(), refs.getFirst().fields());
        var extended = new LinkedHashMap<>(first.fields());
        extended.put("extra", "boolean");
        fields.update(first.id(), new FieldDefinitionCommand("First", extended));
        assertFalse(
            store
                .findById(created.id())
                .orElseThrow()
                .outputs()
                .getFirst()
                .fields()
                .containsKey("extra")
        );
        service.save(created.id(), command(List.of(output("CallRecords", first.id()))));
        assertTrue(
            store
                .findById(created.id())
                .orElseThrow()
                .outputs()
                .getFirst()
                .fields()
                .containsKey("extra")
        );
        service.save(created.id(), command(List.of(output("CallRecords", second.id()))));
        entities.flush();
        entities.clear();
        assertTrue(fields.references(first.id()).isEmpty());
        fields.delete(first.id());
        service.delete(created.id());
        fields.delete(second.id());
    }

    @Test
    void exposesCrudAndUsefulHttpValidation() throws Exception {
        var definition = definition("Calls");
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new MappersExceptionHandler())
            .build();
        String body = json.writeValueAsString(
            command(List.of(output("CallRecords", definition.id())))
        );
        mvc.perform(post("/api/mappers").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.wasmFileName").value("cdr_parser.wasm"))
            .andExpect(jsonPath("$.outputs[0].name").value("CallRecords"))
            .andExpect(jsonPath("$.outputs[0].fields.label").value("string"));
        var id = service.list().getFirst().id();
        mvc.perform(get("/api/mappers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].directory").value("/opt/WASM modules"));
        mvc.perform(
            put("/api/mappers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("CallRecords", "UpdatedList"))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outputs[0].name").value("UpdatedList"));
        mvc.perform(
            post("/api/mappers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("CallRecords", "Call Records"))
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(
            post("/api/mappers").contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isBadRequest());
        mvc.perform(
            post("/api/mappers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace(definition.id().toString(), UUID.randomUUID().toString()))
        ).andExpect(status().isNotFound());
        mvc.perform(delete("/api/mappers/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/mappers/" + id)).andExpect(status().isNotFound());
    }
}
