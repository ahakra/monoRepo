package com.ahvakra.camelintegration.engine;

import java.time.*;
import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Shared JSON boundary: decimal values must never pass through a Java double. */
final class EngineValues {

    static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build();

    record Port(String name, String kind, UUID fieldDefinitionId, Map<String, String> fields) {
        Port {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        static Port raw() {
            return new Port("in", "BYTES", null, Map.of());
        }

        void validate(JsonNode value) {
            require(value != null, "Missing input/output port: " + name);
            if (kind.equals("DYNAMIC_VARIABLE")) return;
            if (kind.equals("BYTES")) {
                require(value.isBinary(), "Expected raw bytes");
                return;
            }
            require(value.isArray(), "Port '" + name + "' requires a record list");
            for (var record : value) {
                require(
                    record.isObject() && record.size() == fields.size(),
                    "Record fields do not match port '" + name + "'"
                );
                fields.forEach((key, type) -> {
                    var cell = record.get(key);
                    require(cell != null, "Missing field: " + key);
                    if (cell.isNull()) return;
                    boolean valid = switch (type) {
                        case "string" -> cell.isString();
                        case "integer" -> cell.isIntegralNumber() && cell.canConvertToInt();
                        case "long" -> cell.isIntegralNumber() && cell.canConvertToLong();
                        case "decimal" -> cell.isNumber();
                        case "boolean" -> cell.isBoolean();
                        case "json" -> true;
                        case "uuid", "date", "datetime" -> temporalOrUuid(type, cell);
                        default -> false;
                    };
                    require(valid, "Invalid " + type + " field: " + key);
                });
            }
        }
    }

    private static boolean temporalOrUuid(String type, JsonNode value) {
        if (!value.isString()) return false;
        try {
            switch (type) {
                case "uuid" -> UUID.fromString(value.asText());
                case "date" -> LocalDate.parse(value.asText());
                default -> OffsetDateTime.parse(value.asText());
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static Map<String, Object> record(JsonNode node) {
        var result = new LinkedHashMap<String, Object>();
        node.properties().forEach(entry ->
            result.put(entry.getKey(), JSON.treeToValue(entry.getValue(), Object.class))
        );
        return result;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
