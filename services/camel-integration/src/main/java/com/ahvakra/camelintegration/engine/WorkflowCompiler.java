package com.ahvakra.camelintegration.engine;

import static com.ahvakra.camelintegration.engine.EngineValues.*;
import static com.ahvakra.camelintegration.engine.ExecutionPlan.*;

import com.ahvakra.camelintegration.connections.domain.*;
import com.ahvakra.camelintegration.connections.infrastructure.file.FileConnectionHandler;
import com.ahvakra.camelintegration.fields.domain.FieldDefinitionStore;
import com.ahvakra.camelintegration.mappers.domain.MapperStore;
import com.ahvakra.camelintegration.rules.domain.*;
import com.ahvakra.camelintegration.transformers.domain.TransformerStore;
import com.ahvakra.camelintegration.workflows.application.*;
import com.ahvakra.camelintegration.workflows.domain.Workflow;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Resolves a saved DAG into an immutable executable snapshot before installing any routes. */
@Component
public class WorkflowCompiler {

    private final WorkflowService workflows;
    private final WorkflowValidator validator;
    private final ConnectionStore connections;
    private final FieldDefinitionStore fields;
    private final MapperStore mappers;
    private final TransformerStore transformers;
    private final NormalizeRuleStore normalizations;
    private final ConditionalRuleStore conditions;
    private final FileConnectionHandler files;
    private final WasmRuntime wasm;

    public WorkflowCompiler(
        WorkflowService workflows,
        WorkflowValidator validator,
        ConnectionStore connections,
        FieldDefinitionStore fields,
        MapperStore mappers,
        TransformerStore transformers,
        NormalizeRuleStore normalizations,
        ConditionalRuleStore conditions,
        FileConnectionHandler files,
        WasmRuntime wasm
    ) {
        this.workflows = workflows;
        this.validator = validator;
        this.connections = connections;
        this.fields = fields;
        this.mappers = mappers;
        this.transformers = transformers;
        this.normalizations = normalizations;
        this.conditions = conditions;
        this.files = files;
        this.wasm = wasm;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> describe(UUID id) {
        return compile(id).describe();
    }

    @Transactional(readOnly = true)
    public ExecutionPlan compile(UUID id) {
        Workflow workflow = workflows.get(id);
        var graph = validator.validate(
            new WorkflowCommand(
                id,
                workflow.name(),
                workflow.description(),
                workflow.nodes(),
                workflow.edges(),
                workflow.viewport()
            )
        );
        var nodes = new LinkedHashMap<String, JsonNode>();
        graph.nodes().forEach(node -> nodes.put(node.path("id").asText(), node));
        var edges = new ArrayList<Edge>();
        graph
            .edges()
            .forEach(e ->
                edges.add(
                    new Edge(
                        e.path("source").asText(),
                        e.path("sourceHandle").asText(),
                        e.path("target").asText(),
                        e.path("targetHandle").asText()
                    )
                )
            );
        var source = nodes
            .values()
            .stream()
            .filter(n -> type(n).equals("dataSource"))
            .findFirst()
            .orElseThrow();
        String sourceId = source.path("id").asText();
        var reachable = new HashSet<String>();
        visit(sourceId, edges, reachable);
        require(
            reachable.size() == nodes.size(),
            "All execution blocks must be reachable from the source"
        );
        require(
            nodes
                .values()
                .stream()
                .anyMatch(n -> type(n).equals("dataDestination")),
            "Add a data destination before starting"
        );
        for (var entry : nodes.entrySet()) {
            if (!type(entry.getValue()).equals("dataDestination")) require(
                outgoing(entry.getKey(), edges).size() > 0,
                "Block has no downstream destination: " + entry.getKey()
            );
        }
        var ordered = topological(nodes, edges);
        var stages = new ArrayList<Stage>();
        var grouped = new HashSet<String>();
        for (String nodeId : ordered) {
            var node = nodes.get(nodeId);
            if (nodeId.equals(sourceId) || grouped.contains(nodeId)) continue;
            Stage stage;
            switch (type(node)) {
                case "normalizeRule" -> {
                    var chain = new ArrayList<String>();
                    var rules = new ArrayList<NormalizeSpec>();
                    String current = nodeId;
                    UUID schemaId = reference(node, "fieldDefinitionId");
                    while (true) {
                        var ruleNode = nodes.get(current);
                        var spec = normalizations
                            .findById(reference(ruleNode, "ruleId"))
                            .orElseThrow(() -> invalid("Missing normalization rule"))
                            .spec();
                        require(
                            spec.fieldDefinitionId().equals(schemaId),
                            "Normalization Fields reference has changed"
                        );
                        require(
                            reference(ruleNode, "fieldDefinitionId").equals(schemaId),
                            "Normalization snapshot is stale"
                        );
                        chain.add(current);
                        grouped.add(current);
                        rules.add(spec);
                        var next = outgoing(current, edges);
                        if (next.size() != 1) break;
                        String target = next.getFirst().target();
                        if (
                            !type(nodes.get(target)).equals("normalizeRule") ||
                            incoming(target, edges).size() != 1
                        ) break;
                        current = target;
                    }
                    var schema = fieldPort("records", schemaId);
                    stage = new Stage(
                        nodeId,
                        current,
                        "normalizeChain",
                        List.copyOf(chain),
                        Map.of("in", schema),
                        Map.of("out", schema),
                        (input, observe) -> {
                            var output = JSON.createArrayNode();
                            var evaluator = new NormalizeEvaluator();
                            long[] matched = new long[rules.size()];
                            // One traversal of the record list, applying every rule to one working record.
                            for (var record : input.get("in")) {
                                var working = EngineValues.record(record);
                                for (int i = 0; i < rules.size(); i++) {
                                    if (evaluator.applyInPlace(rules.get(i), working)) matched[i]++;
                                }
                                output.add(JSON.valueToTree(working));
                            }
                            for (int i = 0; i < rules.size(); i++) observe.accept(
                                new RuleSummary(
                                    chain.get(i),
                                    rules.get(i).name(),
                                    output.size(),
                                    matched[i]
                                )
                            );
                            return Map.of("out", output);
                        },
                        null
                    );
                }
                case "conditionalRule" -> {
                    var rule = conditions
                        .findById(reference(node, "ruleId"))
                        .orElseThrow(() -> invalid("Missing conditional rule"));
                    require(
                        rule
                            .spec()
                            .fieldDefinitionId()
                            .equals(reference(node, "fieldDefinitionId")),
                        "Conditional Fields reference has changed"
                    );
                    var schema = fieldPort("records", rule.spec().fieldDefinitionId());
                    new ConditionalValidator().validate(rule.spec(), schema.fields());
                    stage = new Stage(
                        nodeId,
                        nodeId,
                        "conditionalRule",
                        List.of(nodeId),
                        Map.of("in", schema),
                        Map.of("matched", schema, "not-matched", schema),
                        (input, observe) -> {
                            var matched = JSON.createArrayNode();
                            var unmatched = JSON.createArrayNode();
                            var evaluator = new ConditionalEvaluator();
                            for (var record : input.get("in")) {
                                (evaluator.matches(
                                    rule.spec().expression(),
                                    schema.fields(),
                                    EngineValues.record(record)
                                )
                                    ? matched
                                    : unmatched
                                ).add(record);
                            }
                            return Map.of("matched", matched, "not-matched", unmatched);
                        },
                        null
                    );
                }
                case "mapper" -> {
                    var mapper = mappers
                        .findById(reference(node, "mapperId"))
                        .orElseThrow(() -> invalid("Missing mapper"));
                    var ports = new LinkedHashMap<String, Port>();
                    mapper
                        .outputs()
                        .forEach(p ->
                            ports.put(
                                "list:" + encode(p.name()),
                                new Port(p.name(), "FIELD_LIST", p.fieldDefinitionId(), p.fields())
                            )
                        );
                    validateSnapshot(node.path("data").path("outputs"), ports.values(), false);
                    var module = wasm.load(mapper.directory(), mapper.wasmFileName());
                    module.validate("mapper", List.of(), ports.values());
                    stage = new Stage(
                        nodeId,
                        nodeId,
                        "mapper",
                        List.of(nodeId),
                        Map.of("in", Port.raw()),
                        ports,
                        (input, observe) ->
                            mapOutputs(
                                module.invoke(input.get("in").binaryValue(), ports.values()),
                                ports
                            ),
                        null
                    );
                }
                case "transformer" -> {
                    var transformer = transformers
                        .findById(reference(node, "transformerId"))
                        .orElseThrow(() -> invalid("Missing transformer"));
                    var inputs = new LinkedHashMap<String, Port>();
                    var outputs = new LinkedHashMap<String, Port>();
                    transformer
                        .inputs()
                        .forEach(p ->
                            inputs.put(
                                "input:" + encode(p.name()),
                                new Port(
                                    p.name(),
                                    p.kind().name(),
                                    p.fieldDefinitionId(),
                                    p.fields()
                                )
                            )
                        );
                    transformer
                        .outputs()
                        .forEach(p ->
                            outputs.put(
                                "output:" + encode(p.name()),
                                new Port(
                                    p.name(),
                                    p.kind().name(),
                                    p.fieldDefinitionId(),
                                    p.fields()
                                )
                            )
                        );
                    validateSnapshot(node.path("data").path("inputs"), inputs.values(), true);
                    validateSnapshot(node.path("data").path("outputs"), outputs.values(), true);
                    var module = wasm.load(transformer.directory(), transformer.wasmFileName());
                    module.validate("transformer", inputs.values(), outputs.values());
                    stage = new Stage(
                        nodeId,
                        nodeId,
                        "transformer",
                        List.of(nodeId),
                        inputs,
                        outputs,
                        (input, observe) -> {
                            var named = JSON.createObjectNode();
                            inputs.forEach((handle, port) ->
                                named.set(port.name(), input.get(handle))
                            );
                            var request = JSON.createObjectNode().put("abiVersion", 1);
                            request.set("inputs", named);
                            return mapOutputs(
                                module.invoke(JSON.writeValueAsBytes(request), outputs.values()),
                                outputs
                            );
                        },
                        null
                    );
                }
                case "dataDestination" -> {
                    var incoming = incoming(nodeId, edges);
                    require(
                        incoming.size() == 1,
                        "A destination must have exactly one incoming connection"
                    );
                    var edge = incoming.getFirst();
                    var port = edge.source().equals(sourceId)
                        ? Port.raw()
                        : stages
                              .stream()
                              .filter(s -> s.outputNode().equals(edge.source()))
                              .findFirst()
                              .orElseThrow()
                              .outputs()
                              .get(edge.sourceHandle());
                    stage = new Stage(
                        nodeId,
                        nodeId,
                        "dataDestination",
                        List.of(nodeId),
                        Map.of("in", port),
                        Map.of(),
                        null,
                        connection(node)
                    );
                }
                default -> throw invalid("Unsupported execution block: " + type(node));
            }
            stages.add(stage);
        }
        // A port must be supplied within this source-file exchange. List fan-in concatenates in plan order.
        for (var stage : stages)
            for (var input : stage.inputs().entrySet()) {
                var supplied = incoming(stage.id(), edges)
                    .stream()
                    .filter(e -> e.targetHandle().equals(input.getKey()))
                    .count();
                require(
                    supplied > 0,
                    "Connect input '" + input.getValue().name() + "' on " + stage.id()
                );
                require(
                    supplied == 1 || input.getValue().kind().equals("FIELD_LIST"),
                    "Only list ports support multiple producers"
                );
            }
        var byNode = new HashMap<String, Stage>();
        for (var stage : stages) stage.nodes().forEach(nodeId -> byNode.put(nodeId, stage));
        for (var edge : edges) {
            var from = byNode.get(edge.source());
            var to = byNode.get(edge.target());
            if (from != null && from == to) continue; // Internal edge in a fused chain.
            var output = edge.source().equals(sourceId)
                ? Port.raw()
                : from.outputs().get(edge.sourceHandle());
            var input = to.inputs().get(edge.targetHandle());
            require(
                output != null &&
                    input != null &&
                    output.kind().equals(input.kind()) &&
                    Objects.equals(output.fieldDefinitionId(), input.fieldDefinitionId()) &&
                    output.fields().equals(input.fields()),
                "Execution schemas differ across an arrow; refresh the saved definitions and sync block ports"
            );
        }
        return new ExecutionPlan(
            id,
            workflow.name(),
            sourceId,
            connection(source),
            List.copyOf(stages),
            List.copyOf(edges),
            nodes
                .entrySet()
                .stream()
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry ->
                        entry.getValue().path("data").path("label").asText()
                    )
                ),
            nodes
                .entrySet()
                .stream()
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry ->
                        entry.getValue().path("type").asText()
                    )
                )
        );
    }

    private ConnectionDefinition connection(JsonNode node) {
        var connection = connections
            .findById(reference(node, "connectorId"))
            .orElseThrow(() -> invalid("Missing connector"));
        require(
            connection.type().equals("file"),
            "This engine version supports File connectors only"
        );
        var validated = files.validateSettings(connection.settings());
        // Startup checks do not create or consume files; actual source startup is explicit.
        return new ConnectionDefinition(
            connection.id(),
            connection.name(),
            connection.type(),
            validated,
            connection.password(),
            connection.createdAt(),
            connection.updatedAt()
        );
    }

    private Port fieldPort(String name, UUID id) {
        return new Port(
            name,
            "FIELD_LIST",
            id,
            fields
                .findById(id)
                .orElseThrow(() -> invalid("Missing Fields definition"))
                .fields()
        );
    }

    private void validateSnapshot(JsonNode snapshot, Collection<Port> ports, boolean transformer) {
        require(
            snapshot.size() == ports.size(),
            "Block ports changed; sync the saved block before starting"
        );
        var names = new HashSet<String>();
        for (var entry : snapshot) {
            String name = entry.path("name").asText();
            require(names.add(name), "Duplicate saved port");
            var port = ports
                .stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> invalid("Saved port no longer exists: " + name));
            require(
                !transformer || port.kind().equals(entry.path("kind").asText()),
                "Saved port kind changed"
            );
            require(
                JSON.valueToTree(port.fields()).equals(entry.path("fields")),
                "Saved port Fields snapshot changed"
            );
            require(
                port.fieldDefinitionId() == null
                    ? entry.path("fieldDefinitionId").isNull()
                    : port
                          .fieldDefinitionId()
                          .toString()
                          .equals(entry.path("fieldDefinitionId").asText()),
                "Saved Fields reference changed"
            );
        }
    }

    private static Map<String, JsonNode> mapOutputs(JsonNode result, Map<String, Port> ports) {
        var mapped = new LinkedHashMap<String, JsonNode>();
        ports.forEach((handle, port) -> mapped.put(handle, result.get(port.name())));
        return mapped;
    }

    private static String type(JsonNode node) {
        return node.path("type").asText();
    }

    private static UUID reference(JsonNode node, String key) {
        try {
            return UUID.fromString(node.path("data").path(key).asText());
        } catch (RuntimeException e) {
            throw invalid("Configure " + key + " on block " + node.path("id").asText());
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static String encode(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~");
    }

    private static List<Edge> incoming(String id, List<Edge> edges) {
        return edges
            .stream()
            .filter(e -> e.target().equals(id))
            .toList();
    }

    private static List<Edge> outgoing(String id, List<Edge> edges) {
        return edges
            .stream()
            .filter(e -> e.source().equals(id))
            .toList();
    }

    private static void visit(String id, List<Edge> edges, Set<String> visited) {
        if (!visited.add(id)) return;
        outgoing(id, edges).forEach(e -> visit(e.target(), edges, visited));
    }

    private static List<String> topological(Map<String, JsonNode> nodes, List<Edge> edges) {
        var counts = new LinkedHashMap<String, Integer>();
        nodes.keySet().forEach(id -> counts.put(id, incoming(id, edges).size()));
        var queue = new ArrayDeque<String>();
        counts.forEach((id, count) -> {
            if (count == 0) queue.add(id);
        });
        var result = new ArrayList<String>();
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            result.add(id);
            for (var edge : outgoing(id, edges))
                if (counts.merge(edge.target(), -1, Integer::sum) == 0) queue.add(edge.target());
        }
        require(result.size() == nodes.size(), "Workflow contains a cycle");
        return result;
    }
}
