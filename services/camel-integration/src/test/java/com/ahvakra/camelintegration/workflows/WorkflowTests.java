package com.ahvakra.camelintegration.workflows;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ahvakra.camelintegration.workflows.api.*;
import com.ahvakra.camelintegration.workflows.application.*;
import com.ahvakra.camelintegration.workflows.domain.*;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@Transactional
class WorkflowTests {

    @Autowired
    WorkflowService service;

    @Autowired
    WorkflowController controller;

    @Autowired
    WorkflowStore store;

    @Autowired
    EntityManager entities;

    @Autowired
    JsonMapper json;

    private ObjectNode draft() {
        var draft = (ObjectNode) json.readTree(
            """
            {"name":" Daily export ","description":"Draft with a destination",
             "nodes":[
              {"id":"source","type":"dataSource","position":{"x":100,"y":180},
               "data":{"label":"Source","connectorId":"","connectorName":"","connectorType":""}},
              {"id":"destination","type":"dataDestination","position":{"x":420,"y":180},
               "data":{"label":"Destination","connectorId":"connector-id","connectorName":"Output","connectorType":"file"}}],
             "edges":[{"id":"arrow","source":"source","target":"destination"}],
             "viewport":{"x":10,"y":20,"zoom":0.75}}
            """
        );
        draft.put("id", UUID.randomUUID().toString());
        return draft;
    }

    private WorkflowCommand command(ObjectNode draft) {
        return json.treeToValue(draft, WorkflowCommand.class);
    }

    @Test
    void persistsGraphAndDestinationWithoutCredentialsOrRenderingState() {
        var draft = draft();
        ((ObjectNode) draft.path("nodes").get(1).path("data")).put("password", "secret");
        ((ObjectNode) draft.path("nodes").get(1)).put("selected", true);
        var saved = service.save(null, command(draft));
        entities.flush();
        entities.clear();
        var loaded = service.get(saved.id());
        assertEquals("Daily export", loaded.name());
        assertEquals(2, loaded.nodes().size());
        assertEquals(
            "connector-id",
            loaded.nodes().get(1).path("data").path("connectorId").asText()
        );
        assertFalse(loaded.nodes().get(1).path("data").has("password"));
        assertFalse(loaded.nodes().get(1).has("selected"));
        assertEquals("in", loaded.edges().get(0).path("targetHandle").asText());
        assertEquals("out", loaded.edges().get(0).path("sourceHandle").asText());
        assertEquals(0.75, loaded.viewport().path("zoom").asDouble());
        assertTrue(
            service
                .list()
                .stream()
                .anyMatch(item -> item.id().equals(saved.id()))
        );
        assertThrows(ResponseStatusException.class, () -> service.save(null, command(draft)));
        draft.put("name", "Updated export");
        var updated = service.save(saved.id(), command(draft));
        assertEquals(loaded.createdAt(), updated.createdAt());
        entities.flush();
        entities.clear();
        assertEquals("Updated export", service.get(saved.id()).name());
        service.delete(saved.id());
        assertTrue(store.findById(saved.id()).isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.get(saved.id()));
        assertThrows(ResponseStatusException.class, () -> service.save(saved.id(), command(draft)));
    }

    @Test
    void allowsIncompleteDraftsButRejectsInvalidGraphs() {
        var draft = draft();
        draft.set("edges", json.createArrayNode());
        assertNotNull(service.save(null, command(draft)));
        for (String invalid : new String[] {
            draft.toString().replace("Daily export", " "),
            draft.toString().replace("dataSource", "unknown"),
            draft.toString().replace("\"zoom\":0.75", "\"zoom\":3"),
            draft.toString().replace("\"id\":\"destination\"", "\"id\":\"source\""),
            draft.toString().replace("\"x\":420", "\"x\":\"bad\""),
        }) {
            assertThrows(IllegalArgumentException.class, () ->
                service.save(null, command((ObjectNode) json.readTree(invalid)))
            );
        }
        var backwards = draft();
        ((ObjectNode) backwards.path("edges").get(0))
            .put("source", "destination")
            .put("target", "source");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, command(backwards)));
        var missingPort = draft();
        ((ObjectNode) missingPort.path("edges").get(0)).put("targetHandle", "out");
        assertThrows(IllegalArgumentException.class, () ->
            service.save(null, command(missingPort))
        );
    }

    @Test
    void rejectsCyclesDuplicatesAndTypeMismatches() {
        var draft = draft();
        var nodes = (tools.jackson.databind.node.ArrayNode) draft.path("nodes");
        nodes.add(
            json.readTree(
                """
                {"id":"a","type":"step","position":{"x":1,"y":2},"data":{"label":"A"}}
                """
            )
        );
        nodes.add(
            json.readTree(
                """
                {"id":"b","type":"step","position":{"x":3,"y":4},"data":{"label":"B"}}
                """
            )
        );
        draft.set(
            "edges",
            json.readTree(
                """
                [{"id":"a","source":"a","target":"b"},{"id":"b","source":"b","target":"a"}]
                """
            )
        );
        assertThrows(IllegalArgumentException.class, () -> service.save(null, command(draft)));
        draft.set(
            "edges",
            json.readTree(
                """
                [{"id":"a","source":"source","target":"a"},{"id":"b","source":"source","target":"a"}]
                """
            )
        );
        assertThrows(IllegalArgumentException.class, () -> service.save(null, command(draft)));
        nodes.add(
            json.readTree(
                """
                {"id":"rule","type":"normalizeRule","position":{"x":3,"y":4},
                 "data":{"label":"Normalize","ruleId":"rule-id","ruleName":"Rule","fieldDefinitionId":"fields-id"}}
                """
            )
        );
        draft.set(
            "edges",
            json.readTree(
                """
                [{"id":"a","source":"source","target":"a"},{"id":"b","source":"a","target":"rule"}]
                """
            )
        );
        assertThrows(IllegalArgumentException.class, () -> service.save(null, command(draft)));
        draft.set(
            "edges",
            json.readTree(
                """
                [{"id":"a","source":"rule","target":"destination"}]
                """
            )
        );
        assertNotNull(service.save(null, command(draft)));
    }

    @Test
    void persistsMapperAndTransformerSnapshotsWithNamedAndDynamicPorts() {
        var draft = draft();
        var nodes = (tools.jackson.databind.node.ArrayNode) draft.path("nodes");
        nodes.add(
            json.readTree(
                """
                {"id":"mapper","type":"mapper","position":{"x":1,"y":2},
                 "data":{"label":"Mapper","mapperId":"saved-mapper","mapperName":"Parse",
                  "outputs":[{"name":"Call/Records","fieldDefinitionId":"calls","fields":{"caller":"string"}}]}}
                """
            )
        );
        nodes.add(
            json.readTree(
                """
                {"id":"transformer","type":"transformer","position":{"x":3,"y":4},
                 "data":{"label":"Transformer","transformerId":"saved-transformer","transformerName":"Transform",
                  "inputs":[{"name":"records","kind":"FIELD_LIST","fieldDefinitionId":"calls","fields":{"caller":"string"}}],
                  "outputs":[{"name":"records","kind":"FIELD_LIST","fieldDefinitionId":"calls","fields":{"caller":"string"}},
                             {"name":"count","kind":"DYNAMIC_VARIABLE","fieldDefinitionId":null,"fields":{}}]}}
                """
            )
        );
        draft.set(
            "edges",
            json.readTree(
                """
                [{"id":"raw","source":"source","target":"mapper"},
                 {"id":"typed","source":"mapper","sourceHandle":"list:Call%2FRecords","target":"transformer","targetHandle":"input:records"},
                 {"id":"result","source":"transformer","sourceHandle":"output:records","target":"destination"},
                 {"id":"variable","source":"transformer","sourceHandle":"output:count","target":"destination"}]
                """
            )
        );
        var saved = service.save(null, command(draft));
        entities.flush();
        entities.clear();
        var loaded = service.get(saved.id());
        assertEquals("Call/Records", loaded.edges().get(1).path("label").asText());
        assertEquals(
            "DYNAMIC_VARIABLE",
            loaded.nodes().get(3).path("data").path("outputs").get(1).path("kind").asText()
        );
        assertEquals(
            "string",
            loaded
                .nodes()
                .get(2)
                .path("data")
                .path("outputs")
                .get(0)
                .path("fields")
                .path("caller")
                .asText()
        );
        var input = (ObjectNode) draft.path("nodes").get(3).path("data").path("inputs").get(0);
        input.put("fieldDefinitionId", "different-fields");
        assertThrows(IllegalArgumentException.class, () ->
            service.save(saved.id(), command(draft))
        );
    }

    @Test
    void exposesCrudAndUsefulHttpErrors() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new WorkflowsExceptionHandler())
            .build();
        var draft = draft();
        String id = draft.path("id").asText();
        mvc.perform(
            post("/api/workflows").contentType(MediaType.APPLICATION_JSON).content(draft.toString())
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.name").value("Daily export"));
        mvc.perform(get("/api/workflows")).andExpect(status().isOk());
        mvc.perform(get("/api/workflows/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes[1].type").value("dataDestination"));
        draft.put("name", "Updated");
        mvc.perform(
            put("/api/workflows/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(draft.toString())
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated"));
        mvc.perform(
            post("/api/workflows").contentType(MediaType.APPLICATION_JSON).content(draft.toString())
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(delete("/api/workflows/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/workflows/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/workflows/" + id)).andExpect(status().isNotFound());
    }
}
