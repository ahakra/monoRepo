package com.ahvakra.camelintegration.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.ahvakra.camelintegration.rules.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ConditionalEvaluatorTests {

    private final ConditionalEvaluator evaluator = new ConditionalEvaluator();
    private final ConditionalValidator validator = new ConditionalValidator();
    private final Map<String, String> types = Map.ofEntries(
        Map.entry("start", "datetime"),
        Map.entry("end", "datetime"),
        Map.entry("date", "date"),
        Map.entry("count", "integer"),
        Map.entry("total", "long"),
        Map.entry("cost", "decimal"),
        Map.entry("status", "string"),
        Map.entry("active", "boolean"),
        Map.entry("id", "uuid"),
        Map.entry("data", "json")
    );

    private ConditionalExpression value(String field, ConditionalOperator op, Object value) {
        return new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            field,
            op,
            ConditionalOperandType.VALUE,
            value,
            null
        );
    }

    private ConditionalExpression field(String left, ConditionalOperator op, String right) {
        return new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            left,
            op,
            ConditionalOperandType.FIELD,
            null,
            right
        );
    }

    private ConditionalExpression group(
        ConditionalJunction mode,
        ConditionalExpression... children
    ) {
        return new ConditionalExpression(
            ConditionalNodeType.GROUP,
            mode,
            List.of(children),
            null,
            null,
            null,
            null,
            null
        );
    }

    private boolean matches(ConditionalExpression node, Map<String, Object> record) {
        validator.validate(new ConditionalSpec("Rule", "", UUID.randomUUID(), node), types);
        return evaluator.evaluate(node, types, record).matched();
    }

    @Test
    void combinesNestedAndOrGroupsAndLeavesInputUnchanged() {
        var expression = group(
            ConditionalJunction.AND,
            field("end", ConditionalOperator.GREATER_THAN, "start"),
            group(
                ConditionalJunction.OR,
                value("status", ConditionalOperator.EQUALS, "completed"),
                group(
                    ConditionalJunction.AND,
                    value("count", ConditionalOperator.GREATER_OR_EQUAL, "10"),
                    value("active", ConditionalOperator.EQUALS, true)
                )
            )
        );
        var record = new LinkedHashMap<String, Object>(
            Map.of(
                "start",
                "2026-10-28T12:00:00Z",
                "end",
                "2026-10-28T15:00:01+02:00",
                "status",
                "pending",
                "count",
                11,
                "active",
                true
            )
        );
        record.put("untouched", null);
        var copy = new LinkedHashMap<>(record);
        assertTrue(matches(expression, record));
        var result = evaluator.evaluate(expression, types, record);
        assertEquals(copy, result.record());
        assertEquals(copy, record);
        assertThrows(UnsupportedOperationException.class, () ->
            result.record().put("new", "value")
        );
        record.put("active", false);
        assertFalse(matches(expression, record));
        record.put("status", "completed");
        assertTrue(matches(expression, record));
        record.put("end", "2026-10-28T11:59:59Z");
        assertFalse(matches(expression, record));
    }

    @Test
    void comparesTemporalValuesAsDatesAndInstantsInsteadOfStrings() {
        assertTrue(
            matches(
                value("end", ConditionalOperator.GREATER_THAN, "2026-10-28T12:00:00Z"),
                Map.of("end", "2026-10-28T15:00:00+02:00")
            )
        );
        assertTrue(
            matches(
                field("end", ConditionalOperator.EQUALS, "start"),
                Map.of("start", "2026-10-28T12:00:00Z", "end", "2026-10-28T14:00:00+02:00")
            )
        );
        assertTrue(
            matches(
                value("date", ConditionalOperator.LESS_THAN, "2026-11-01"),
                Map.of("date", "2026-10-28")
            )
        );
        assertFalse(
            matches(
                value("date", ConditionalOperator.EQUALS, "2026-10-28"),
                Map.of("date", "2026-02-30")
            )
        );
    }

    @Test
    void supportsEveryFieldTypeAndNumericPrecision() {
        assertTrue(
            matches(
                value("total", ConditionalOperator.EQUALS, "9007199254740993"),
                Map.of("total", 9007199254740993L)
            )
        );
        assertTrue(
            matches(
                field("cost", ConditionalOperator.GREATER_THAN, "count"),
                Map.of("cost", "10.00000000000000001", "count", 10)
            )
        );
        assertTrue(matches(value("cost", ConditionalOperator.EQUALS, "10.00"), Map.of("cost", 10)));
        assertTrue(
            matches(value("active", ConditionalOperator.EQUALS, false), Map.of("active", "false"))
        );
        var id = UUID.randomUUID().toString();
        assertTrue(
            matches(
                value("id", ConditionalOperator.EQUALS, id),
                Map.of("id", id.toUpperCase(Locale.ROOT))
            )
        );
        assertTrue(
            matches(
                value("data", ConditionalOperator.EQUALS, Map.of("items", List.of(1, 2))),
                Map.of("data", Map.of("items", List.of(1.0, 2.0)))
            )
        );
        assertTrue(matches(value("status", ConditionalOperator.EQUALS, ""), Map.of("status", "")));
        for (var op : new ConditionalOperator[] {
            ConditionalOperator.GREATER_THAN,
            ConditionalOperator.GREATER_OR_EQUAL,
            ConditionalOperator.NOT_EQUALS,
        })
            assertTrue(matches(value("count", op, "1"), Map.of("count", 2)));
        for (var op : new ConditionalOperator[] {
            ConditionalOperator.LESS_THAN,
            ConditionalOperator.LESS_OR_EQUAL,
        })
            assertTrue(matches(value("count", op, "3"), Map.of("count", 2)));
        assertTrue(
            matches(value("count", ConditionalOperator.GREATER_OR_EQUAL, "2"), Map.of("count", 2))
        );
        assertTrue(
            matches(value("count", ConditionalOperator.LESS_OR_EQUAL, "2"), Map.of("count", 2))
        );
        for (var op : new ConditionalOperator[] {
            ConditionalOperator.STARTS_WITH,
            ConditionalOperator.ENDS_WITH,
            ConditionalOperator.CONTAINS,
        }) {
            assertTrue(matches(value("status", op, "abc"), Map.of("status", "abc")));
            assertFalse(matches(value("status", op, "abc"), Map.of("status", "ABC")));
        }
        assertTrue(
            matches(value("status", ConditionalOperator.GREATER_THAN, "a"), Map.of("status", "b"))
        );
    }

    @Test
    void handlesNullMissingAndInvalidDataWithoutCoercingThemIntoMatches() {
        var isNull = new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            "count",
            ConditionalOperator.IS_NULL,
            null,
            null,
            null
        );
        var notNull = new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            "count",
            ConditionalOperator.IS_NOT_NULL,
            null,
            null,
            null
        );
        assertTrue(matches(isNull, Map.of()));
        assertFalse(matches(notNull, Map.of()));
        assertTrue(matches(notNull, Map.of("count", 0)));
        var nullRecord = new HashMap<String, Object>();
        nullRecord.put("count", null);
        assertTrue(matches(isNull, nullRecord));
        for (var op : new ConditionalOperator[] {
            ConditionalOperator.EQUALS,
            ConditionalOperator.NOT_EQUALS,
            ConditionalOperator.GREATER_THAN,
        }) {
            var expression = value("count", op, "1");
            assertFalse(matches(expression, Map.of()));
            assertFalse(matches(expression, nullRecord));
            assertFalse(matches(expression, Map.of("count", "invalid")));
            assertFalse(matches(expression, Map.of("count", true)));
            assertFalse(matches(expression, Map.of("count", "2147483648")));
        }
        assertFalse(
            matches(field("count", ConditionalOperator.NOT_EQUALS, "total"), Map.of("count", 1))
        );
        assertThrows(InvalidRuleException.class, () -> evaluator.evaluate(isNull, types, null));
    }

    @Test
    void rejectsInvalidTreesOperatorsTypesLiteralsAndOversizedGroups() {
        for (var expression : List.of(
            group(ConditionalJunction.AND),
            value("unknown", ConditionalOperator.EQUALS, "x"),
            value("active", ConditionalOperator.GREATER_THAN, true),
            value("data", ConditionalOperator.CONTAINS, "x"),
            field("start", ConditionalOperator.GREATER_THAN, "count"),
            field("count", ConditionalOperator.EQUALS, "unknown"),
            value("start", ConditionalOperator.GREATER_THAN, "2026-10-28T12:00:00"),
            value("date", ConditionalOperator.EQUALS, "2026-02-30"),
            value("count", ConditionalOperator.EQUALS, "1.5"),
            value("total", ConditionalOperator.EQUALS, "9223372036854775808"),
            value("active", ConditionalOperator.EQUALS, "yes"),
            value("id", ConditionalOperator.EQUALS, "not-a-uuid"),
            value("status", ConditionalOperator.EQUALS, 42),
            value("count", ConditionalOperator.EQUALS, null)
        ))
            assertThrows(InvalidRuleException.class, () -> matches(expression, Map.of()));
        var deep = value("status", ConditionalOperator.EQUALS, "ok");
        for (int i = 0; i < 8; i++) deep = group(ConditionalJunction.AND, deep);
        var tooDeep = deep;
        assertThrows(InvalidRuleException.class, () -> matches(tooDeep, Map.of()));
        var tooWide = new ConditionalExpression(
            ConditionalNodeType.GROUP,
            ConditionalJunction.AND,
            Collections.nCopies(100, value("status", ConditionalOperator.EQUALS, "ok")),
            null,
            null,
            null,
            null,
            null
        );
        assertThrows(InvalidRuleException.class, () -> matches(tooWide, Map.of()));
        var refs = validator.validate(
            new ConditionalSpec(
                "R",
                "",
                UUID.randomUUID(),
                field("start", ConditionalOperator.LESS_THAN, "end")
            ),
            types
        );
        assertEquals(Map.of("start", "datetime", "end", "datetime"), refs);
    }
}
