package com.ahvakra.camelintegration.engine;

import static com.ahvakra.camelintegration.engine.EngineValues.*;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import java.util.*;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

record ExecutionPlan(
    UUID id,
    String name,
    String sourceNode,
    ConnectionDefinition source,
    List<Stage> stages,
    List<Edge> edges
) {
    record Edge(String source, String sourceHandle, String target, String targetHandle) {}

    record Stage(
        String id,
        String outputNode,
        String kind,
        List<String> nodes,
        Map<String, Port> inputs,
        Map<String, Port> outputs,
        Function<Map<String, JsonNode>, Map<String, JsonNode>> operation,
        ConnectionDefinition destination
    ) {}

    Map<String, Object> describe() {
        return Map.of(
            "workflowId",
            id,
            "name",
            name,
            "sourceNode",
            sourceNode,
            "stages",
            stages
                .stream()
                .map(stage ->
                    Map.of(
                        "id",
                        stage.id(),
                        "kind",
                        stage.kind(),
                        "nodeIds",
                        stage.nodes(),
                        "inputs",
                        stage.inputs().keySet(),
                        "outputs",
                        stage.outputs().keySet()
                    )
                )
                .toList()
        );
    }

    /** All per-file state belongs to the exchange, never a singleton processor. */
    static final class State {

        private final Map<String, Map<String, JsonNode>> pending = new HashMap<>();

        void forward(ExecutionPlan plan, String source, Map<String, JsonNode> outputs) {
            for (var edge : plan.edges()) {
                if (!edge.source().equals(source)) continue;
                var value = outputs.get(edge.sourceHandle());
                require(value != null, "Stage did not emit handle: " + edge.sourceHandle());
                var target = pending.computeIfAbsent(edge.target(), unused ->
                    new LinkedHashMap<>()
                );
                var copy = value.deepCopy(); // Fan-out cannot mutate another branch's records.
                var existing = target.putIfAbsent(edge.targetHandle(), copy);
                if (existing != null) {
                    require(
                        existing.isArray() && copy.isArray(),
                        "Only record lists may merge into one input"
                    );
                    ((tools.jackson.databind.node.ArrayNode) existing).addAll(
                        (tools.jackson.databind.node.ArrayNode) copy
                    );
                }
            }
        }

        Map<String, JsonNode> take(Stage stage) {
            var values = pending.remove(stage.id());
            require(
                values != null && values.keySet().equals(stage.inputs().keySet()),
                "Missing inputs for stage " + stage.id()
            );
            stage.inputs().forEach((handle, port) -> port.validate(values.get(handle)));
            return values;
        }
    }
}
