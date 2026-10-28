package com.ahvakra.camelintegration.rules;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ahvakra.camelintegration.fields.application.*;
import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.rules.api.*;
import com.ahvakra.camelintegration.rules.application.ConditionalRuleService;
import com.ahvakra.camelintegration.rules.domain.*;
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
class ConditionalRuleTests {

    @Autowired
    ConditionalRuleService rules;

    @Autowired
    ConditionalRuleStore store;

    @Autowired
    ConditionalRuleController controller;

    @Autowired
    FieldDefinitionService fields;

    @Autowired
    EntityManager entities;

    @Autowired
    JsonMapper mapper;

    private FieldDefinition definition() {
        return fields.create(
            new FieldDefinitionCommand(
                "Calls",
                Map.of(
                    "start",
                    "datetime",
                    "end",
                    "datetime",
                    "count",
                    "integer",
                    "status",
                    "string",
                    "data",
                    "json"
                )
            )
        );
    }

    private ConditionalSpec spec(UUID id) {
        var comparison = new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            "end",
            ConditionalOperator.GREATER_THAN,
            ConditionalOperandType.FIELD,
            null,
            "start"
        );
        var status = new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            "status",
            ConditionalOperator.EQUALS,
            ConditionalOperandType.VALUE,
            "completed",
            null
        );
        var expression = new ConditionalExpression(
            ConditionalNodeType.GROUP,
            ConditionalJunction.AND,
            List.of(
                comparison,
                new ConditionalExpression(
                    ConditionalNodeType.GROUP,
                    ConditionalJunction.OR,
                    List.of(status),
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ),
            null,
            null,
            null,
            null,
            null
        );
        return new ConditionalSpec(" Completed calls ", "Check end after start", id, expression);
    }

    @Test
    void persistsNestedTreesEditsAppliesAndDeletes() {
        var definition = definition();
        var created = rules.save(null, spec(definition.id()));
        entities.flush();
        entities.clear();
        var loaded = store.findById(created.id()).orElseThrow();
        assertEquals("Completed calls", loaded.spec().name());
        assertEquals(created.spec().expression(), loaded.spec().expression());
        assertEquals(
            Map.of("start", "datetime", "end", "datetime", "status", "string"),
            loaded.referencedFields()
        );
        var record = Map.<String, Object>of(
            "start",
            "2026-10-28T12:00:00Z",
            "end",
            "2026-10-28T13:00:00Z",
            "status",
            "completed"
        );
        assertTrue(rules.apply(created.id(), record).matched());
        assertEquals(record, rules.apply(created.id(), record).record());
        assertFalse(rules.apply(created.id(), Map.of()).matched());
        var updated = rules.save(
            created.id(),
            new ConditionalSpec("Renamed", "Edited", definition.id(), loaded.spec().expression())
        );
        assertEquals(loaded.createdAt(), updated.createdAt());
        assertEquals("Renamed", rules.list().getFirst().spec().name());
        rules.delete(created.id());
        entities.flush();
        entities.clear();
        assertTrue(store.findById(created.id()).isEmpty());
        assertEquals(
            0L,
            (
                (Number) entities
                    .createNativeQuery(
                        "SELECT COUNT(*) FROM etl_conditional_rule_fields WHERE rule_id = :id"
                    )
                    .setParameter("id", created.id())
                    .getSingleResult()
            ).longValue()
        );
        assertThrows(ConditionalRuleNotFoundException.class, () ->
            rules.apply(created.id(), record)
        );
    }

    @Test
    void protectsAllReferencedFieldsIncludingTheRightOperandAndReleasesThemAfterEdits() {
        var definition = definition();
        var created = rules.save(null, spec(definition.id()));
        assertThrows(FieldsInUseException.class, () -> fields.delete(definition.id()));
        var changed = new LinkedHashMap<>(definition.fields());
        changed.put("start", "string");
        assertThrows(FieldsInUseException.class, () ->
            fields.update(definition.id(), new FieldDefinitionCommand("Calls", changed))
        );
        var removed = new LinkedHashMap<>(definition.fields());
        removed.remove("end");
        assertThrows(FieldsInUseException.class, () ->
            fields.update(definition.id(), new FieldDefinitionCommand("Calls", removed))
        );
        var refs = fields.references(definition.id());
        assertEquals("Conditional rule: Completed calls", refs.getFirst().tableName());
        assertTrue(refs.getFirst().fields().containsKey("start"));
        // Unused fields can change while the rule is retained.
        var unused = new LinkedHashMap<>(definition.fields());
        unused.put("count", "long");
        fields.update(definition.id(), new FieldDefinitionCommand("Calls", unused));
        var replacement = new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            "status",
            ConditionalOperator.IS_NOT_NULL,
            null,
            null,
            null
        );
        rules.save(
            created.id(),
            new ConditionalSpec("Status present", "", definition.id(), replacement)
        );
        fields.update(
            definition.id(),
            new FieldDefinitionCommand("Calls", Map.of("status", "string"))
        );
        rules.delete(created.id());
        fields.delete(definition.id());
    }

    @Test
    void exposesCrudAndPreviewWithUsefulValidationErrors() throws Exception {
        var definition = definition();
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RulesExceptionHandler())
            .build();
        String json = mapper.writeValueAsString(spec(definition.id()));
        mvc.perform(
            post("/api/rules/conditional").contentType(MediaType.APPLICATION_JSON).content(json)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.spec.name").value("Completed calls"));
        var id = rules.list().getFirst().id();
        mvc.perform(get("/api/rules/conditional"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].spec.expression.junction").value("AND"));
        mvc.perform(
            post("/api/rules/conditional/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"spec\":" +
                        json +
                        ",\"record\":{\"start\":\"2026-10-28T12:00:00Z\",\"end\":\"2026-10-28T13:00:00Z\",\"status\":\"completed\"}}"
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched").value(true));
        mvc.perform(
            post("/api/rules/conditional/" + id + "/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched").value(false));
        mvc.perform(
            put("/api/rules/conditional/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("Completed calls", "Updated"))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spec.name").value("Updated"));
        mvc.perform(
            post("/api/rules/conditional")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("GREATER_THAN", "CONTAINS"))
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(
            post("/api/rules/conditional/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isBadRequest());
        mvc.perform(
            post("/api/rules/conditional")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("GREATER_THAN", "UNKNOWN"))
        ).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/rules/conditional/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/rules/conditional/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void roundTripsJsonLiteralsAndRejectsBadMetadata() {
        var definition = definition();
        var expression = new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            "data",
            ConditionalOperator.EQUALS,
            ConditionalOperandType.VALUE,
            Map.of("labels", List.of("a", "b"), "count", 1),
            null
        );
        var created = rules.save(
            null,
            new ConditionalSpec("JSON", null, definition.id(), expression)
        );
        entities.flush();
        entities.clear();
        assertTrue(
            rules
                .apply(
                    created.id(),
                    Map.of("data", Map.of("labels", List.of("a", "b"), "count", 1.0))
                )
                .matched()
        );
        assertThrows(InvalidRuleException.class, () -> rules.save(null, null));
        assertThrows(InvalidRuleException.class, () ->
            rules.save(null, new ConditionalSpec(" ", "", definition.id(), expression))
        );
        assertThrows(InvalidRuleException.class, () ->
            rules.save(
                null,
                new ConditionalSpec("R", "x".repeat(2001), definition.id(), expression)
            )
        );
        assertThrows(InvalidRuleException.class, () ->
            rules.save(null, new ConditionalSpec("R", "", null, expression))
        );
        assertThrows(FieldsNotFoundException.class, () ->
            rules.save(null, new ConditionalSpec("R", "", UUID.randomUUID(), expression))
        );
    }
}
