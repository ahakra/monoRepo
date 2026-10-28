package com.ahvakra.camelintegration.rules;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ahvakra.camelintegration.fields.application.*;
import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.rules.api.*;
import com.ahvakra.camelintegration.rules.application.NormalizeRuleService;
import com.ahvakra.camelintegration.rules.domain.*;
import jakarta.persistence.EntityManager;
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
class NormalizeRuleTests {

    @Autowired
    NormalizeRuleService rules;

    @Autowired
    NormalizeRuleStore store;

    @Autowired
    FieldDefinitionService fields;

    @Autowired
    NormalizeRuleController controller;

    @Autowired
    EntityManager entities;

    private UUID definition() {
        return fields
            .create(
                new FieldDefinitionCommand(
                    "Phone",
                    Map.of("phone", "string", "normalized", "string", "count", "integer")
                )
            )
            .id();
    }

    private NormalizeSpec spec(
        UUID fieldsId,
        String source,
        String destination,
        NormalizeCondition condition,
        String value,
        NormalizeAction action,
        String actionValue
    ) {
        return new NormalizeSpec(
            " Phone normalization ",
            "Remove country code",
            fieldsId,
            source,
            condition,
            value,
            action,
            actionValue,
            destination,
            ""
        );
    }

    private NormalizeSpec valid(UUID fieldsId) {
        return spec(
            fieldsId,
            "phone",
            "normalized",
            NormalizeCondition.STARTS_WITH,
            "+961",
            NormalizeAction.REMOVE_PREFIX,
            "+961"
        );
    }

    @Test
    void persistsAndTestsReplacementThroughTheApiAndValidatesBothInputs() throws Exception {
        var id = definition();
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RulesExceptionHandler())
            .build();
        String json = """
        {"name":"Replace separators","fieldDefinitionId":"%s",
         "sourceField":"phone","destinationField":"normalized","condition":"CONTAINS",
         "conditionValue":"-","action":"REPLACE","actionValue":"-","replacementValue":" "}
        """.formatted(id);
        mvc.perform(
            post("/api/rules/normalize").contentType(MediaType.APPLICATION_JSON).content(json)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.spec.replacementValue").value(" "));
        var rule = rules.list().getFirst();
        entities.flush();
        entities.clear();
        assertEquals(" ", store.findById(rule.id()).orElseThrow().spec().replacementValue());
        assertEquals(
            "1 2 3",
            rules.apply(rule.id(), Map.of("phone", "1-2-3")).record().get("normalized")
        );
        mvc.perform(
            post("/api/rules/normalize/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spec\":" + json + ",\"record\":{\"phone\":\"1-2-3\"}}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.record.normalized").value("1 2 3"));
        var removal = new NormalizeSpec(
            "Remove",
            "",
            id,
            "phone",
            NormalizeCondition.CONTAINS,
            "-",
            NormalizeAction.REPLACE,
            "-",
            "normalized",
            ""
        );
        rules.save(rule.id(), removal);
        entities.flush();
        entities.clear();
        assertEquals("", store.findById(rule.id()).orElseThrow().spec().replacementValue());
        assertEquals(
            "123",
            rules.apply(rule.id(), Map.of("phone", "1-2-3")).record().get("normalized")
        );
        for (String invalidReplacement : new String[] { null, "x".repeat(2001) }) {
            assertThrows(InvalidRuleException.class, () ->
                rules.save(
                    null,
                    new NormalizeSpec(
                        "Invalid",
                        "",
                        id,
                        "phone",
                        NormalizeCondition.CONTAINS,
                        "-",
                        NormalizeAction.REPLACE,
                        "-",
                        "normalized",
                        invalidReplacement
                    )
                )
            );
        }
        assertThrows(InvalidRuleException.class, () ->
            rules.save(
                null,
                new NormalizeSpec(
                    "Invalid",
                    "",
                    id,
                    "phone",
                    NormalizeCondition.CONTAINS,
                    "-",
                    NormalizeAction.REPLACE,
                    "",
                    "normalized",
                    "x"
                )
            )
        );
    }

    @Test
    void persistsEditsDeletesAndAppliesSavedRules() {
        var id = definition();
        var created = rules.save(null, valid(id));
        entities.flush();
        entities.clear();
        var loaded = store.findById(created.id()).orElseThrow();
        assertEquals("Phone normalization", loaded.spec().name());
        assertEquals("Remove country code", loaded.spec().description());
        assertEquals(NormalizeCondition.STARTS_WITH, loaded.spec().condition());
        assertEquals(
            "123",
            rules.apply(created.id(), Map.of("phone", "+961123")).record().get("normalized")
        );
        var noMatch = Map.<String, Object>of("phone", "123", "normalized", "keep");
        assertEquals(noMatch, rules.apply(created.id(), noMatch).record());
        var updated = rules.save(
            created.id(),
            spec(
                id,
                "phone",
                "normalized",
                NormalizeCondition.LENGTH_EQUALS,
                "3",
                NormalizeAction.ADD_PREFIX,
                "+"
            )
        );
        entities.flush();
        entities.clear();
        assertEquals(loaded.createdAt(), updated.createdAt());
        assertEquals(
            "+123",
            rules.apply(updated.id(), Map.of("phone", "123")).record().get("normalized")
        );
        assertTrue(
            rules
                .list()
                .stream()
                .anyMatch(rule -> rule.id().equals(created.id()))
        );
        rules.delete(created.id());
        assertTrue(store.findById(created.id()).isEmpty());
        assertThrows(RuleNotFoundException.class, () -> rules.apply(created.id(), Map.of()));
    }

    @Test
    void rejectsNonTextUnknownFieldsBadOperatorsAndInvalidValues() {
        var id = definition();
        for (String key : new String[] { "count", "unknown", "id", "datetime" }) {
            assertThrows(InvalidRuleException.class, () ->
                rules.save(
                    null,
                    spec(
                        id,
                        key,
                        "normalized",
                        NormalizeCondition.EQUALS,
                        "",
                        NormalizeAction.COPY,
                        ""
                    )
                )
            );
            assertThrows(InvalidRuleException.class, () ->
                rules.save(
                    null,
                    spec(id, "phone", key, NormalizeCondition.EQUALS, "", NormalizeAction.COPY, "")
                )
            );
        }
        for (String length : new String[] { "-1", "1.5", "abc", "2147483648", "" }) {
            assertThrows(InvalidRuleException.class, () ->
                rules.save(
                    null,
                    spec(
                        id,
                        "phone",
                        "normalized",
                        NormalizeCondition.LENGTH_EQUALS,
                        length,
                        NormalizeAction.COPY,
                        ""
                    )
                )
            );
        }
        assertThrows(InvalidRuleException.class, () ->
            rules.save(null, spec(id, "phone", "normalized", null, "x", NormalizeAction.COPY, ""))
        );
        assertThrows(InvalidRuleException.class, () ->
            rules.save(
                null,
                spec(id, "phone", "normalized", NormalizeCondition.EQUALS, "x", null, "")
            )
        );
        assertThrows(InvalidRuleException.class, () ->
            rules.save(
                null,
                spec(
                    id,
                    "phone",
                    "normalized",
                    NormalizeCondition.STARTS_WITH,
                    "",
                    NormalizeAction.COPY,
                    ""
                )
            )
        );
        assertThrows(InvalidRuleException.class, () ->
            rules.save(
                null,
                spec(
                    id,
                    "phone",
                    "normalized",
                    NormalizeCondition.EQUALS,
                    "x",
                    NormalizeAction.REMOVE_PREFIX,
                    ""
                )
            )
        );
        assertThrows(InvalidRuleException.class, () -> rules.save(null, null));
        assertThrows(FieldsNotFoundException.class, () ->
            rules.save(null, valid(UUID.randomUUID()))
        );
        assertTrue(
            rules
                .test(
                    spec(
                        id,
                        "phone",
                        "normalized",
                        NormalizeCondition.EQUALS,
                        "",
                        NormalizeAction.COPY,
                        ""
                    ),
                    Map.of("phone", "")
                )
                .matched()
        );
    }

    @Test
    void protectsReferencedDefinitionsAndKeysUntilRuleIsDeleted() {
        var id = definition();
        var rule = rules.save(null, valid(id));
        assertTrue(
            fields
                .references(id)
                .stream()
                .anyMatch(ref -> ref.tableName().contains("Normalize rule"))
        );
        assertThrows(FieldsInUseException.class, () -> fields.delete(id));
        assertThrows(FieldsInUseException.class, () ->
            fields.update(
                id,
                new FieldDefinitionCommand(
                    "Phone",
                    Map.of("phone", "integer", "normalized", "string")
                )
            )
        );
        assertThrows(FieldsInUseException.class, () ->
            fields.update(id, new FieldDefinitionCommand("Phone", Map.of("phone", "string")))
        );
        fields.update(
            id,
            new FieldDefinitionCommand(
                "Renamed",
                Map.of("phone", "string", "normalized", "string", "extra", "boolean")
            )
        );
        rules.delete(rule.id());
        fields.delete(id);
    }

    @Test
    void exposesCrudPreviewApplyAndHttpValidation() throws Exception {
        var id = definition();
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RulesExceptionHandler())
            .build();
        String json = """
        {"name":"Normalize phone","description":"Drop country code","fieldDefinitionId":"%s",
         "sourceField":"phone","destinationField":"normalized","condition":"STARTS_WITH",
         "conditionValue":"+961","action":"REMOVE_PREFIX","actionValue":"+961"}
        """.formatted(id);
        mvc.perform(
            post("/api/rules/normalize").contentType(MediaType.APPLICATION_JSON).content(json)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.spec.name").value("Normalize phone"));
        var ruleId = rules.list().getFirst().id();
        mvc.perform(get("/api/rules/normalize"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].spec.sourceField").value("phone"));
        mvc.perform(
            post("/api/rules/normalize/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spec\":" + json + ",\"record\":{\"phone\":\"+961123\"}}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched").value(true))
            .andExpect(jsonPath("$.record.normalized").value("123"));
        mvc.perform(
            post("/api/rules/normalize/" + ruleId + "/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"other\",\"normalized\":\"keep\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched").value(false))
            .andExpect(jsonPath("$.record.normalized").value("keep"));
        mvc.perform(
            put("/api/rules/normalize/" + ruleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("Normalize phone", "Renamed"))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spec.name").value("Renamed"));
        mvc.perform(
            post("/api/rules/normalize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("STARTS_WITH", "UNKNOWN"))
        ).andExpect(status().isBadRequest());
        mvc.perform(
            post("/api/rules/normalize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("\"sourceField\":\"phone\"", "\"sourceField\":\"count\""))
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(
            post("/api/rules/normalize/test").contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/rules/normalize/" + ruleId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/rules/normalize/" + ruleId)).andExpect(status().isNotFound());
    }
}
