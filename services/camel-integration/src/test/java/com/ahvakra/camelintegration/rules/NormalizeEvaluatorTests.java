package com.ahvakra.camelintegration.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.ahvakra.camelintegration.rules.domain.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NormalizeEvaluatorTests {

    private final NormalizeEvaluator evaluator = new NormalizeEvaluator();

    private NormalizeSpec spec(
        NormalizeCondition condition,
        String match,
        NormalizeAction action,
        String value
    ) {
        return new NormalizeSpec(
            "Normalize",
            "",
            UUID.randomUUID(),
            "source",
            condition,
            match,
            action,
            value,
            "destination",
            ""
        );
    }

    @Test
    void replacesAllLiteralOccurrencesAndSupportsRemovalWithoutChangingNonMatches() {
        var replace = new NormalizeSpec(
            "Replace",
            "",
            UUID.randomUUID(),
            "source",
            NormalizeCondition.CONTAINS,
            ".",
            NormalizeAction.REPLACE,
            ".",
            "destination",
            "$1\\"
        );
        var input = Map.<String, Object>of("source", "a.a.", "destination", "old");
        assertEquals("a$1\\a$1\\", evaluator.evaluate(replace, input).record().get("destination"));
        assertEquals("old", input.get("destination"));
        assertFalse(
            evaluator.evaluate(replace, Map.of("source", "ABC", "destination", "keep")).matched()
        );
        assertEquals(
            "keep",
            evaluator
                .evaluate(replace, Map.of("source", "ABC", "destination", "keep"))
                .record()
                .get("destination")
        );
        var remove = new NormalizeSpec(
            "Remove",
            "",
            UUID.randomUUID(),
            "source",
            NormalizeCondition.LENGTH_GREATER_THAN,
            "0",
            NormalizeAction.REPLACE,
            "-",
            "source",
            ""
        );
        assertEquals(
            "123",
            evaluator.evaluate(remove, Map.of("source", "1-2-3")).record().get("source")
        );
        assertEquals(
            "ABC",
            evaluator.evaluate(remove, Map.of("source", "ABC")).record().get("source")
        );
        var caseSensitive = new NormalizeSpec(
            "Case",
            "",
            UUID.randomUUID(),
            "source",
            NormalizeCondition.LENGTH_GREATER_THAN,
            "0",
            NormalizeAction.REPLACE,
            "a",
            "destination",
            "x"
        );
        assertEquals(
            "AxA",
            evaluator.evaluate(caseSensitive, Map.of("source", "AaA")).record().get("destination")
        );
    }

    @Test
    void matchesAllConditionsIncludingUnicodeLength() {
        for (NormalizeCondition condition : NormalizeCondition.values()) {
            String match = switch (condition) {
                case STARTS_WITH -> "a";
                case ENDS_WITH -> "b";
                case CONTAINS -> "😀";
                case EQUALS -> "a😀b";
                case LENGTH_EQUALS -> "3";
                case LENGTH_GREATER_THAN -> "2";
                case LENGTH_LESS_THAN -> "4";
            };
            var result = evaluator.evaluate(
                spec(condition, match, NormalizeAction.COPY, ""),
                Map.of("source", "a😀b")
            );
            assertTrue(result.matched(), condition.name());
            assertEquals("a😀b", result.record().get("destination"));
        }
    }

    @Test
    void preservesTheWholeRecordForEveryNonMatchingCondition() {
        for (NormalizeCondition condition : NormalizeCondition.values()) {
            String match = switch (condition) {
                case STARTS_WITH, ENDS_WITH, CONTAINS, EQUALS -> "xyz";
                case LENGTH_EQUALS -> "4";
                case LENGTH_GREATER_THAN -> "3";
                case LENGTH_LESS_THAN -> "3";
            };
            var input = Map.<String, Object>of("source", "abc", "destination", "keep");
            var result = evaluator.evaluate(
                spec(condition, match, NormalizeAction.COPY, ""),
                input
            );
            assertFalse(result.matched(), condition.name());
            assertEquals(input, result.record());
            var absentDestination = evaluator.evaluate(
                spec(condition, match, NormalizeAction.COPY, ""),
                Map.of("source", "abc")
            );
            assertFalse(absentDestination.record().containsKey("destination"));
        }
    }

    @Test
    void implementsAllActionsWithoutChangingTheInputRecord() {
        var expected = Map.of(
            NormalizeAction.REPLACE,
            "pre-value-end",
            NormalizeAction.COPY,
            "pre-value-end",
            NormalizeAction.ADD_PREFIX,
            "xpre-value-end",
            NormalizeAction.ADD_SUFFIX,
            "pre-value-endx",
            NormalizeAction.REMOVE_PREFIX,
            "value-end",
            NormalizeAction.REMOVE_SUFFIX,
            "pre-value",
            NormalizeAction.TRIM,
            "pre-value-end",
            NormalizeAction.UPPERCASE,
            "PRE-VALUE-END",
            NormalizeAction.LOWERCASE,
            "pre-value-end"
        );
        for (NormalizeAction action : NormalizeAction.values()) {
            String argument = switch (action) {
                case REMOVE_PREFIX -> "pre-";
                case REMOVE_SUFFIX -> "-end";
                default -> "x";
            };
            var input = Map.<String, Object>of(
                "source",
                "pre-value-end",
                "destination",
                "old",
                "other",
                42
            );
            var result = evaluator.evaluate(
                spec(NormalizeCondition.STARTS_WITH, "pre", action, argument),
                input
            );
            assertTrue(result.matched());
            assertEquals(expected.get(action), result.record().get("destination"), action.name());
            assertEquals(42, result.record().get("other"));
            assertEquals("old", input.get("destination"));
        }
        assertEquals(
            "Hello",
            evaluator
                .evaluate(
                    spec(NormalizeCondition.CONTAINS, "Hello", NormalizeAction.TRIM, ""),
                    Map.of("source", "  Hello  ")
                )
                .record()
                .get("destination")
        );
        assertEquals(
            "mixed",
            evaluator
                .evaluate(
                    spec(NormalizeCondition.EQUALS, "MiXeD", NormalizeAction.LOWERCASE, ""),
                    Map.of("source", "MiXeD")
                )
                .record()
                .get("destination")
        );
    }

    @Test
    void leavesNonMatchesNullMissingAndNonStringsUnchanged() {
        var spec = spec(NormalizeCondition.STARTS_WITH, "abc", NormalizeAction.ADD_PREFIX, "x");
        for (Object source : new Object[] { "ABC", "other", 123, true }) {
            var input = Map.of("source", source, "destination", "keep");
            var result = evaluator.evaluate(spec, input);
            assertFalse(result.matched());
            assertEquals(input, result.record());
        }
        var nullRecord = new HashMap<String, Object>();
        nullRecord.put("source", null);
        assertEquals(nullRecord, evaluator.evaluate(spec, nullRecord).record());
        assertFalse(evaluator.evaluate(spec, Map.of()).matched());
        assertThrows(InvalidRuleException.class, () -> evaluator.evaluate(spec, null));
    }

    @Test
    void removesOnlyTheMatchingEdgeAndSupportsSameSourceAndDestination() {
        for (NormalizeAction action : new NormalizeAction[] {
            NormalizeAction.REMOVE_PREFIX,
            NormalizeAction.REMOVE_SUFFIX,
        }) {
            assertEquals(
                "abc-abc",
                evaluator
                    .evaluate(
                        spec(NormalizeCondition.CONTAINS, "abc", action, "-"),
                        Map.of("source", "abc-abc")
                    )
                    .record()
                    .get("destination")
            );
        }
        assertEquals(
            "abc-",
            evaluator
                .evaluate(
                    spec(NormalizeCondition.CONTAINS, "abc", NormalizeAction.REMOVE_SUFFIX, "abc"),
                    Map.of("source", "abc-abc")
                )
                .record()
                .get("destination")
        );
        var sameField = new NormalizeSpec(
            "Same field",
            "",
            UUID.randomUUID(),
            "source",
            NormalizeCondition.STARTS_WITH,
            "+",
            NormalizeAction.REMOVE_PREFIX,
            "+",
            "source",
            ""
        );
        assertEquals(
            "123",
            evaluator.evaluate(sameField, Map.of("source", "+123")).record().get("source")
        );
        assertEquals(
            "",
            evaluator
                .evaluate(
                    spec(NormalizeCondition.EQUALS, "abc", NormalizeAction.REMOVE_PREFIX, "abc"),
                    Map.of("source", "abc")
                )
                .record()
                .get("destination")
        );
    }
}
