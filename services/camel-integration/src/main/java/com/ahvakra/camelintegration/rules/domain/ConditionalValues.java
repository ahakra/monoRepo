package com.ahvakra.camelintegration.rules.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/** Typed comparison semantics, independent of HTTP and persistence. */
public final class ConditionalValues {

    private ConditionalValues() {}

    public static boolean numeric(String type) {
        return Set.of("integer", "long", "decimal").contains(type);
    }

    public static boolean text(String type) {
        return "string".equals(type) || "text".equals(type);
    }

    public static boolean compatible(String left, String right) {
        return (
            left.equals(right) || (numeric(left) && numeric(right)) || (text(left) && text(right))
        );
    }

    public static boolean supports(String type, ConditionalOperator operator) {
        return switch (operator) {
            case EQUALS, NOT_EQUALS, IS_NULL, IS_NOT_NULL -> true;
            case STARTS_WITH, ENDS_WITH, CONTAINS -> text(type);
            case GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL -> numeric(type) ||
                text(type) ||
                Set.of("date", "datetime").contains(type);
        };
    }

    public static Object convert(String type, Object value) {
        if (value == null) throw new IllegalArgumentException(
            "Use a null operator to check null values."
        );
        return switch (type) {
            case "string", "text" -> {
                if (!(value instanceof String)) throw new IllegalArgumentException(
                    "Expected text."
                );
                yield value;
            }
            case "integer", "long", "decimal" -> {
                if (
                    !(value instanceof Number) && !(value instanceof String)
                ) throw new IllegalArgumentException("Expected a number.");
                var number = new BigDecimal(value.toString());
                if (type.equals("integer")) number.intValueExact();
                if (type.equals("long")) number.longValueExact();
                yield number.stripTrailingZeros();
            }
            case "boolean" -> {
                if (value instanceof Boolean) yield value;
                if ("true".equals(value)) yield true;
                if ("false".equals(value)) yield false;
                throw new IllegalArgumentException("Expected true or false.");
            }
            case "date" -> LocalDate.parse(value.toString());
            // Offset is required so comparisons never depend on the machine timezone.
            case "datetime" -> OffsetDateTime.parse(value.toString()).toInstant();
            case "uuid" -> {
                String text = value.toString();
                var uuid = UUID.fromString(text);
                if (!uuid.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException(
                    "Expected a UUID."
                );
                yield uuid;
            }
            case "json" -> canonicalJson(value, 0);
            default -> throw new IllegalArgumentException("Unsupported field type: " + type);
        };
    }

    private static Object canonicalJson(Object value, int depth) {
        if (depth > 32) throw new IllegalArgumentException(
            "JSON values may have up to 32 nested levels."
        );
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number) return new BigDecimal(value.toString()).stripTrailingZeros();
        if (value instanceof List<?> list) return list
            .stream()
            .map(item -> canonicalJson(item, depth + 1))
            .toList();
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> {
                if (!(key instanceof String)) throw new IllegalArgumentException(
                    "JSON object keys must be text."
                );
                result.put((String) key, canonicalJson(item, depth + 1));
            });
            return result;
        }
        throw new IllegalArgumentException("Expected a JSON value.");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static boolean compare(ConditionalOperator operator, Object left, Object right) {
        return switch (operator) {
            case EQUALS -> left.equals(right);
            case NOT_EQUALS -> !left.equals(right);
            case GREATER_THAN -> ((Comparable) left).compareTo(right) > 0;
            case GREATER_OR_EQUAL -> ((Comparable) left).compareTo(right) >= 0;
            case LESS_THAN -> ((Comparable) left).compareTo(right) < 0;
            case LESS_OR_EQUAL -> ((Comparable) left).compareTo(right) <= 0;
            case STARTS_WITH -> ((String) left).startsWith((String) right);
            case ENDS_WITH -> ((String) left).endsWith((String) right);
            case CONTAINS -> ((String) left).contains((String) right);
            case IS_NULL, IS_NOT_NULL -> throw new IllegalArgumentException(
                "Null operators do not compare two values."
            );
        };
    }
}
