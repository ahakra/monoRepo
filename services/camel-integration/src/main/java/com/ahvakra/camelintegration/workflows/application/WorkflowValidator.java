package com.ahvakra.camelintegration.workflows.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Validates saved design snapshots without starting routes or resolving connector credentials. */
@Component
public class WorkflowValidator {

    private static final Set<String> TYPES = Set.of(
        "dataSource",
        "dataDestination",
        "step",
        "mapper",
        "transformer",
        "conditionalRule",
        "normalizeRule"
    );
    private final JsonMapper json;

    public WorkflowValidator(JsonMapper json) {
        this.json = json;
    }

    public record Graph(JsonNode nodes, JsonNode edges, JsonNode viewport) {}

    public Graph validate(WorkflowCommand command) {
        require(command != null, "Enter a workflow definition.");
        text(command.name(), 120, true);
        text(command.description(), 2000, false);
        var nodes = command.nodes();
        var edges = command.edges();
        require(
            nodes != null && nodes.isArray() && nodes.size() <= 1000,
            "Provide up to 1000 blocks."
        );
        require(
            edges != null && edges.isArray() && edges.size() <= 5000,
            "Provide up to 5000 arrows."
        );
        var cleanNodes = json.createArrayNode();
        var byId = new LinkedHashMap<String, JsonNode>();
        int sources = 0;
        for (var node : nodes) {
            String id = string(node, "id", 120, true);
            String type = string(node, "type", 40, true);
            require(TYPES.contains(type) && !byId.containsKey(id), "Invalid or duplicate block.");
            if (type.equals("dataSource")) sources++;
            var position = node.path("position");
            var cleanPosition = json.createObjectNode();
            for (var key : List.of("x", "y")) cleanPosition.put(key, number(position, key));
            var data = node.path("data");
            var cleanData = json.createObjectNode();
            cleanData.put("label", string(data, "label", 120, true));
            if (type.equals("dataSource") || type.equals("dataDestination")) {
                copyStrings(data, cleanData, "connectorId", "connectorName", "connectorType");
            } else if (type.equals("mapper")) {
                copyStrings(data, cleanData, "mapperId", "mapperName");
                cleanData.set("outputs", ports(data.path("outputs"), false));
                require(
                    !data.path("mapperId").asText("").isEmpty() == data.path("outputs").size() > 0,
                    "Select a mapper with output lists or leave it unconfigured."
                );
            } else if (type.equals("transformer")) {
                copyStrings(data, cleanData, "transformerId", "transformerName");
                for (var direction : List.of("inputs", "outputs")) {
                    cleanData.set(direction, ports(data.path(direction), true));
                    require(
                        !data.path("transformerId").asText("").isEmpty() ==
                            data.path(direction).size() > 0,
                        "Select a transformer with inputs and outputs or leave it unconfigured."
                    );
                }
            } else if (isRule(type)) {
                copyStrings(data, cleanData, "ruleId", "ruleName", "fieldDefinitionId");
                boolean configured = !cleanData.path("ruleId").asText().isEmpty();
                require(
                    configured == !cleanData.path("ruleName").asText().isEmpty() &&
                        configured == !cleanData.path("fieldDefinitionId").asText().isEmpty(),
                    "Invalid saved rule reference."
                );
            }
            var clean = json.createObjectNode();
            clean.put("id", id).put("type", type);
            clean.set("position", cleanPosition);
            clean.set("data", cleanData);
            if (type.equals("dataSource")) clean.put("deletable", false);
            cleanNodes.add(clean);
            byId.put(id, clean);
        }
        require(sources == 1, "A workflow must contain exactly one Data source block.");
        var cleanEdges = json.createArrayNode();
        var edgeIds = new HashSet<String>();
        var connections = new HashSet<List<String>>();
        var adjacency = new HashMap<String, List<String>>();
        for (var edge : edges) {
            String id = string(edge, "id", 120, true);
            String source = string(edge, "source", 120, true);
            String target = string(edge, "target", 120, true);
            require(
                edgeIds.add(id) &&
                    !source.equals(target) &&
                    byId.containsKey(source) &&
                    byId.containsKey(target),
                "Invalid arrow endpoints or duplicate arrow ID."
            );
            var from = byId.get(source);
            var to = byId.get(target);
            String sourceHandle = handle(edge, "sourceHandle", defaultOutput(from));
            String targetHandle = handle(
                edge,
                "targetHandle",
                to.path("type").asText().equals("transformer") ? null : "in"
            );
            var outputs = handles(from, false);
            require(
                sourceHandle != null &&
                    targetHandle != null &&
                    outputs.containsKey(sourceHandle) &&
                    handles(to, true).containsKey(targetHandle),
                "An arrow references an invalid port."
            );
            require(
                connections.add(List.of(source, sourceHandle, target, targetHandle)),
                "Duplicate arrow."
            );
            adjacency.computeIfAbsent(source, unused -> new ArrayList<>()).add(target);
            var clean = json.createObjectNode();
            clean
                .put("id", id)
                .put("source", source)
                .put("target", target)
                .put("sourceHandle", sourceHandle)
                .put("targetHandle", targetHandle)
                .put("type", "smoothstep");
            if (!outputs.get(sourceHandle).isEmpty()) clean.put("label", outputs.get(sourceHandle));
            cleanEdges.add(clean);
        }
        var visited = new HashSet<String>();
        for (var id : byId.keySet()) acyclic(id, adjacency, visited, new HashSet<>());
        for (var edge : cleanEdges) {
            var types = new HashSet<String>();
            types.addAll(
                portTypes(
                    edge.path("source").asText(),
                    edge.path("sourceHandle").asText(),
                    false,
                    byId,
                    cleanEdges,
                    new HashSet<>()
                )
            );
            types.addAll(
                portTypes(
                    edge.path("target").asText(),
                    edge.path("targetHandle").asText(),
                    true,
                    byId,
                    cleanEdges,
                    new HashSet<>()
                )
            );
            require(
                !types.contains("") && types.size() <= 1,
                "Arrows require matching Fields types or port kinds."
            );
        }
        var viewport = json.createObjectNode();
        require(command.viewport() != null, "Provide a viewport.");
        for (var key : List.of("x", "y", "zoom"))
            viewport.put(key, number(command.viewport(), key));
        require(
            viewport.path("zoom").asDouble() >= 0.2 && viewport.path("zoom").asDouble() <= 2,
            "Invalid viewport zoom."
        );
        return new Graph(cleanNodes, cleanEdges, viewport);
    }

    private JsonNode ports(JsonNode ports, boolean transformer) {
        require(ports.isArray() && ports.size() <= 100, "Provide up to 100 ports per direction.");
        var result = json.createArrayNode();
        var names = new HashSet<String>();
        for (var port : ports) {
            String name = string(port, "name", 120, true);
            require(
                name
                    .codePoints()
                    .noneMatch(code -> whitespace(code) || Character.isISOControl(code)) &&
                    names.add(name),
                "Port names must be unique and contain no whitespace."
            );
            String kind = transformer ? string(port, "kind", 30, true) : "FIELD_LIST";
            require(Set.of("FIELD_LIST", "DYNAMIC_VARIABLE").contains(kind), "Invalid port kind.");
            var clean = json.createObjectNode();
            clean.put("name", name);
            if (transformer) clean.put("kind", kind);
            var fields = json.createObjectNode();
            if (kind.equals("FIELD_LIST")) {
                clean.put("fieldDefinitionId", string(port, "fieldDefinitionId", 120, true));
                require(
                    port.path("fields").isObject() && port.path("fields").size() <= 1000,
                    "Invalid Fields snapshot."
                );
                for (var field : port.path("fields").properties()) {
                    text(field.getKey(), 120, true);
                    require(field.getValue().isString(), "Field types must be strings.");
                    text(field.getValue().asText(), 120, true);
                    fields.put(field.getKey(), field.getValue().asText());
                }
            } else clean.putNull("fieldDefinitionId");
            clean.set("fields", fields);
            result.add(clean);
        }
        return result;
    }

    private Map<String, String> handles(JsonNode node, boolean input) {
        String type = node.path("type").asText();
        if (
            (input && type.equals("dataSource")) || (!input && type.equals("dataDestination"))
        ) return Map.of();
        var result = new HashMap<String, String>();
        if (type.equals("transformer") || (!input && type.equals("mapper"))) {
            for (var port : node.path("data").path(input ? "inputs" : "outputs")) {
                String name = port.path("name").asText();
                result.put(portHandle(type, input, name), name);
            }
        } else if (!input && type.equals("conditionalRule")) {
            result.put("matched", "Matched");
            result.put("not-matched", "Not matched");
        } else result.put(
            input ? "in" : "out",
            !input && type.equals("normalizeRule") ? "Output" : ""
        );
        return result;
    }

    private Set<String> portTypes(
        String id,
        String handle,
        boolean input,
        Map<String, JsonNode> nodes,
        JsonNode edges,
        Set<String> visited
    ) {
        var node = nodes.get(id);
        String type = node.path("type").asText();
        var data = node.path("data");
        if (type.equals("dataSource") || (type.equals("mapper") && input)) return Set.of("raw");
        if (type.equals("dataDestination")) return Set.of();
        if (isRule(type)) return Set.of(
            data.path("fieldDefinitionId").asText().isEmpty()
                ? ""
                : "fields:" + data.path("fieldDefinitionId").asText()
        );
        if (type.equals("mapper") || type.equals("transformer")) {
            for (var port : data.path(input ? "inputs" : "outputs")) {
                if (portHandle(type, input, port.path("name").asText()).equals(handle)) {
                    return Set.of(
                        port.path("kind").asText().equals("DYNAMIC_VARIABLE")
                            ? "dynamic"
                            : "fields:" + port.path("fieldDefinitionId").asText()
                    );
                }
            }
            return Set.of("");
        }
        if (!visited.add(id)) return Set.of();
        var result = new HashSet<String>();
        for (var edge : edges) {
            if (edge.path("source").asText().equals(id)) result.addAll(
                portTypes(
                    edge.path("target").asText(),
                    edge.path("targetHandle").asText(),
                    true,
                    nodes,
                    edges,
                    visited
                )
            );
            else if (edge.path("target").asText().equals(id)) result.addAll(
                portTypes(
                    edge.path("source").asText(),
                    edge.path("sourceHandle").asText(),
                    false,
                    nodes,
                    edges,
                    visited
                )
            );
        }
        return result;
    }

    private void acyclic(
        String id,
        Map<String, List<String>> adjacency,
        Set<String> visited,
        Set<String> path
    ) {
        require(!path.contains(id), "Workflow arrows cannot form a cycle.");
        if (!visited.add(id)) return;
        path.add(id);
        for (var target : adjacency.getOrDefault(id, List.of()))
            acyclic(target, adjacency, visited, path);
        path.remove(id);
    }

    private String portHandle(String type, boolean input, String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~");
        return (type.equals("mapper") ? "list:" : input ? "input:" : "output:") + encoded;
    }

    private String defaultOutput(JsonNode node) {
        return Set.of("dataSource", "step", "normalizeRule").contains(node.path("type").asText())
            ? "out"
            : null;
    }

    private String handle(JsonNode edge, String key, String fallback) {
        var value = edge.path(key);
        return value.isMissingNode() || value.isNull() ? fallback : string(edge, key, 1500, true);
    }

    private boolean isRule(String type) {
        return type.equals("conditionalRule") || type.equals("normalizeRule");
    }

    private void copyStrings(JsonNode from, ObjectNode to, String... keys) {
        for (var key : keys) to.put(key, string(from, key, 120, false));
    }

    private String string(JsonNode node, String key, int max, boolean required) {
        require(node.path(key).isString(), "Invalid " + key + ".");
        String value = node.path(key).asText();
        text(value, max, required);
        return value;
    }

    private void text(String value, int max, boolean required) {
        require(
            value != null &&
                value.length() <= max &&
                (!required || value.codePoints().anyMatch(code -> !whitespace(code))),
            "Invalid text value (maximum " + max + " characters)."
        );
    }

    private double number(JsonNode node, String key) {
        require(
            node.path(key).isNumber() && Double.isFinite(node.path(key).asDouble()),
            "Invalid " + key + " coordinate."
        );
        return node.path(key).asDouble();
    }

    private boolean whitespace(int code) {
        return Character.isWhitespace(code) || Character.isSpaceChar(code) || code == 0xFEFF;
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
