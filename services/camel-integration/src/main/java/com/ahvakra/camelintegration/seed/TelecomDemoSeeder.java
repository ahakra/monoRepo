package com.ahvakra.camelintegration.seed;

import com.ahvakra.camelintegration.connections.domain.*;
import com.ahvakra.camelintegration.connections.infrastructure.file.FileConnectionHandler;
import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.mappers.domain.*;
import com.ahvakra.camelintegration.rules.domain.*;
import com.ahvakra.camelintegration.transformers.domain.*;
import com.ahvakra.camelintegration.workflows.application.*;
import com.ahvakra.camelintegration.workflows.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Seeds a complete design example atomically, preserving existing definitions and user edits. */
@Component
@ConditionalOnProperty(
    name = "app.seed.telecom-demo.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class TelecomDemoSeeder implements ApplicationRunner {

    private final FieldDefinitionStore fields;
    private final ConnectionStore connections;
    private final MapperStore mappers;
    private final NormalizeRuleStore normalizations;
    private final ConditionalRuleStore conditions;
    private final TransformerStore transformers;
    private final WorkflowStore workflows;
    private final FileConnectionHandler fileHandler;
    private final WorkflowValidator validator;
    private final JsonMapper json;

    public TelecomDemoSeeder(
        FieldDefinitionStore fields,
        ConnectionStore connections,
        MapperStore mappers,
        NormalizeRuleStore normalizations,
        ConditionalRuleStore conditions,
        TransformerStore transformers,
        WorkflowStore workflows,
        FileConnectionHandler fileHandler,
        WorkflowValidator validator,
        JsonMapper json
    ) {
        this.fields = fields;
        this.connections = connections;
        this.mappers = mappers;
        this.normalizations = normalizations;
        this.conditions = conditions;
        this.transformers = transformers;
        this.workflows = workflows;
        this.fileHandler = fileHandler;
        this.validator = validator;
        this.json = json;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        seed();
    }

    @Transactional
    public void seed() {
        // V14 installations already contain this workflow. Never reset a saved demo on startup.
        if (workflows.findById(id(70)).isPresent()) return;
        var now = Instant.now();
        var mixed = mixedFields();
        var cdr = new LinkedHashMap<>(mixed);
        cdr.keySet().removeAll(Set.of("record_type", "sms_count"));
        var sms = new LinkedHashMap<>(mixed);
        sms.keySet().removeAll(Set.of("record_type", "end_time", "duration_seconds"));
        var usageFields = seedFields(1, "Telecom - mixed usage", mixed, now);
        var cdrFields = seedFields(2, "Telecom - CDR output", cdr, now);
        var smsFields = seedFields(3, "Telecom - SMS output", sms, now);
        var source = seedConnection(10, "Telecom - input CSV", "input", "usage.csv", now);
        var cdrFile = seedConnection(11, "Telecom - CDR files", "output/cdr", "cdr.csv", now);
        var smsFile = seedConnection(12, "Telecom - SMS files", "output/sms", "sms.csv", now);
        var rejectedFile = seedConnection(
            13,
            "Telecom - rejected files",
            "output/rejected",
            "rejected.csv",
            now
        );
        var mapper = mappers.findById(id(20)).orElseGet(() -> {
            var definition = new MapperDefinition(
                id(20),
                "Telecom - parse usage CSV",
                "Design contract: parse UTF-8 CSV headers; empty cells become null; cast numeric and datetime fields; generate id; preserve calling/called; initialize normalized fields to null. WASM execution is not implemented.",
                "../../examples/telecom/wasm",
                "parse-usage.wasm",
                List.of(new MapperOutput("usage", usageFields.id(), usageFields.fields())),
                now,
                now
            );
            mappers.save(definition);
            return definition;
        });
        var nodes = json.createArrayNode();
        var edges = json.createArrayNode();
        connector(nodes, "source", "dataSource", source, 0, 0).put("deletable", false);
        var parse = block(nodes, "parse", "mapper", "Parse usage CSV", 300, 0);
        parse.put("mapperId", mapper.id().toString()).put("mapperName", mapper.name());
        parse.set("outputs", json.valueToTree(mapper.outputs()));
        edge(edges, "source", "parse", "out", "in");
        String previous = "parse";
        String handle = "list:usage";
        for (String party : List.of("calling", "called")) {
            var rules = seedNumberRules(party, party.equals("calling") ? 30 : 40, usageFields, now);
            for (int i = 0; i < rules.size(); i++) {
                var rule = rules.get(i);
                String nodeId = party + "-" + (i + 1);
                var data = block(
                    nodes,
                    nodeId,
                    "normalizeRule",
                    rule.spec().name().replace("Telecom - ", ""),
                    600 + i * 300,
                    party.equals("calling") ? 0 : 260
                );
                ruleReference(data, rule.id(), rule.spec().name(), rule.spec().fieldDefinitionId());
                edge(edges, previous, nodeId, handle, "in");
                previous = nodeId;
                handle = "out";
            }
        }
        for (String kind : List.of("CDR", "SMS")) {
            boolean isCdr = kind.equals("CDR");
            String suffix = kind.toLowerCase(Locale.ROOT);
            int y = isCdr ? 520 : 820;
            var match = seedMatch(isCdr ? 50 : 51, kind, usageFields, now);
            ruleReference(
                block(nodes, "match-" + suffix, "conditionalRule", "Match valid " + kind, 600, y),
                match.id(),
                match.spec().name(),
                match.spec().fieldDefinitionId()
            );
            var transformer = seedProjection(
                isCdr ? 60 : 61,
                kind,
                usageFields,
                isCdr ? cdrFields : smsFields,
                now
            );
            var data = block(
                nodes,
                "project-" + suffix,
                "transformer",
                "Project " + kind + " fields",
                1000,
                y
            );
            data.put("transformerId", transformer.id().toString()).put(
                "transformerName",
                transformer.name()
            );
            data.set("inputs", json.valueToTree(transformer.inputs()));
            data.set("outputs", json.valueToTree(transformer.outputs()));
            connector(
                nodes,
                suffix + "-file",
                "dataDestination",
                isCdr ? cdrFile : smsFile,
                1400,
                y
            );
            edge(edges, "match-" + suffix, "project-" + suffix, "matched", "input:usage");
            edge(edges, "project-" + suffix, suffix + "-file", "output:" + suffix, "in");
        }
        edge(edges, previous, "match-cdr", "out", "in");
        edge(edges, "match-cdr", "match-sms", "not-matched", "in");
        connector(nodes, "rejected-file", "dataDestination", rejectedFile, 1000, 1120);
        edge(edges, "match-sms", "rejected-file", "not-matched", "in");
        var viewport = json.createObjectNode().put("x", 30).put("y", 30).put("zoom", 0.4);
        String name = "Telecom - CSV to CDR and SMS";
        String description =
            "Design demo: file -> CSV mapper -> calling/called normalization -> CDR/SMS matching -> schema projection -> separate files. Unmatched records go to rejected. Saved configuration only; mapper/transformer execution is not implemented.";
        var graph = validator.validate(
            new WorkflowCommand(id(70), name, description, nodes, edges, viewport)
        );
        workflows.save(
            new Workflow(
                id(70),
                name,
                description,
                graph.nodes(),
                graph.edges(),
                graph.viewport(),
                now,
                now
            )
        );
    }

    private FieldDefinition seedFields(
        int suffix,
        String name,
        Map<String, String> schema,
        Instant now
    ) {
        return fields.findById(id(suffix)).orElseGet(() -> {
            var definition = new FieldDefinition(id(suffix), name, schema, now, now);
            fields.save(definition);
            return definition;
        });
    }

    private ConnectionDefinition seedConnection(
        int suffix,
        String name,
        String directory,
        String filename,
        Instant now
    ) {
        return connections.findById(id(suffix)).orElseGet(() -> {
            var settings = fileHandler.validateSettings(
                Map.of(
                    "directoryName",
                    "../../examples/telecom/" + directory,
                    "fileName",
                    filename,
                    "charset",
                    "UTF-8",
                    "readLock",
                    "changed",
                    "delay",
                    "1000",
                    "maxMessagesPerPoll",
                    "1"
                )
            );
            var connection = new ConnectionDefinition(
                id(suffix),
                name,
                "file",
                settings,
                "",
                now,
                now
            );
            connections.save(connection);
            return connection;
        });
    }

    private record NumberStep(
        String title,
        NormalizeCondition condition,
        String conditionValue,
        NormalizeAction action,
        String actionValue
    ) {}

    private List<NormalizeRule> seedNumberRules(
        String party,
        int base,
        FieldDefinition schema,
        Instant now
    ) {
        var steps = List.of(
            new NumberStep(
                "Trim",
                NormalizeCondition.LENGTH_GREATER_THAN,
                "0",
                NormalizeAction.TRIM,
                ""
            ),
            new NumberStep(
                "Remove spaces",
                NormalizeCondition.CONTAINS,
                " ",
                NormalizeAction.REPLACE,
                " "
            ),
            new NumberStep(
                "Remove hyphens",
                NormalizeCondition.CONTAINS,
                "-",
                NormalizeAction.REPLACE,
                "-"
            ),
            new NumberStep(
                "Remove 00",
                NormalizeCondition.STARTS_WITH,
                "00",
                NormalizeAction.REMOVE_PREFIX,
                "00"
            ),
            new NumberStep(
                "Local mobile prefix",
                NormalizeCondition.LENGTH_EQUALS,
                "8",
                NormalizeAction.ADD_PREFIX,
                "+961"
            ),
            new NumberStep(
                "International prefix",
                NormalizeCondition.STARTS_WITH,
                "961",
                NormalizeAction.ADD_PREFIX,
                "+"
            )
        );
        var rules = new ArrayList<NormalizeRule>();
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            var ruleId = id(base + i);
            String normalized = "normalized" + party;
            String name = "Telecom - " + party + " - " + (i + 1) + " " + step.title();
            var spec = new NormalizeSpec(
                name,
                "Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.",
                schema.id(),
                i == 0 ? party : normalized,
                step.condition(),
                step.conditionValue(),
                step.action(),
                step.actionValue(),
                normalized,
                ""
            );
            var rule = normalizations.findById(ruleId).orElseGet(() -> {
                var created = new NormalizeRule(ruleId, spec, now, now);
                normalizations.save(created);
                return created;
            });
            rules.add(rule);
        }
        return rules;
    }

    private ConditionalRule seedMatch(
        int suffix,
        String kind,
        FieldDefinition schema,
        Instant now
    ) {
        return conditions.findById(id(suffix)).orElseGet(() -> {
            boolean cdr = kind.equals("CDR");
            var expression = new ConditionalExpression(
                ConditionalNodeType.GROUP,
                ConditionalJunction.AND,
                List.of(
                    compare("record_type", ConditionalOperator.EQUALS, kind),
                    compare("normalizedcalling", ConditionalOperator.STARTS_WITH, "+961"),
                    compare("normalizedcalled", ConditionalOperator.STARTS_WITH, "+961"),
                    compare(
                        cdr ? "duration_seconds" : "sms_count",
                        cdr
                            ? ConditionalOperator.GREATER_OR_EQUAL
                            : ConditionalOperator.GREATER_THAN,
                        0
                    ),
                    compare("charge_amount", ConditionalOperator.GREATER_OR_EQUAL, 0)
                ),
                null,
                null,
                null,
                null,
                null
            );
            var spec = new ConditionalSpec(
                "Telecom - match valid " + kind,
                "Demo routing predicate: type, normalized country prefixes, nonnegative charge and valid usage metric. This is not full phone-number validation.",
                schema.id(),
                expression
            );
            var references = new ConditionalValidator().validate(spec, schema.fields());
            var rule = new ConditionalRule(id(suffix), spec, references, now, now);
            conditions.save(rule);
            return rule;
        });
    }

    private ConditionalExpression compare(
        String field,
        ConditionalOperator operator,
        Object value
    ) {
        return new ConditionalExpression(
            ConditionalNodeType.COMPARISON,
            null,
            null,
            field,
            operator,
            ConditionalOperandType.VALUE,
            value,
            null
        );
    }

    private TransformerDefinition seedProjection(
        int suffix,
        String kind,
        FieldDefinition input,
        FieldDefinition output,
        Instant now
    ) {
        return transformers.findById(id(suffix)).orElseGet(() -> {
            String lower = kind.toLowerCase(Locale.ROOT);
            var definition = new TransformerDefinition(
                id(suffix),
                "Telecom - project " + kind,
                "Design contract: project matching usage records onto the " +
                    kind +
                    " output schema, preserving normalized numbers, typed values and identifiers. Drop fields absent from the output schema. WASM execution is not implemented.",
                "../../examples/telecom/wasm",
                "project-" + lower + ".wasm",
                List.of(
                    new TransformerPort(
                        "usage",
                        TransformerPortKind.FIELD_LIST,
                        input.id(),
                        input.fields()
                    )
                ),
                List.of(
                    new TransformerPort(
                        lower,
                        TransformerPortKind.FIELD_LIST,
                        output.id(),
                        output.fields()
                    )
                ),
                now,
                now
            );
            transformers.save(definition);
            return definition;
        });
    }

    private ObjectNode block(ArrayNode nodes, String id, String type, String label, int x, int y) {
        var node = nodes.addObject().put("id", id).put("type", type);
        node.set("position", json.createObjectNode().put("x", x).put("y", y));
        var data = json.createObjectNode().put("label", label);
        node.set("data", data);
        return data;
    }

    private ObjectNode connector(
        ArrayNode nodes,
        String id,
        String type,
        ConnectionDefinition connection,
        int x,
        int y
    ) {
        var data = block(nodes, id, type, connection.name(), x, y);
        data.put("connectorId", connection.id().toString())
            .put("connectorName", connection.name())
            .put("connectorType", connection.type());
        return (ObjectNode) nodes.get(nodes.size() - 1);
    }

    private void ruleReference(ObjectNode data, UUID ruleId, String name, UUID fieldId) {
        data.put("ruleId", ruleId.toString())
            .put("ruleName", name)
            .put("fieldDefinitionId", fieldId.toString());
    }

    private void edge(
        ArrayNode edges,
        String source,
        String target,
        String sourceHandle,
        String targetHandle
    ) {
        edges
            .addObject()
            .put("id", "e" + (edges.size() + 1))
            .put("source", source)
            .put("target", target)
            .put("sourceHandle", sourceHandle)
            .put("targetHandle", targetHandle);
    }

    private Map<String, String> mixedFields() {
        var schema = new LinkedHashMap<String, String>();
        schema.put("id", "uuid");
        schema.put("datetime", "datetime");
        for (String field : List.of(
            "record_id",
            "record_type",
            "calling",
            "called",
            "normalizedcalling",
            "normalizedcalled"
        ))
            schema.put(field, "string");
        schema.put("start_time", "datetime");
        schema.put("end_time", "datetime");
        schema.put("duration_seconds", "integer");
        schema.put("sms_count", "integer");
        schema.put("direction", "string");
        schema.put("status", "string");
        schema.put("charge_amount", "decimal");
        for (String field : List.of("currency", "imsi", "imei", "cell_id", "operator_code"))
            schema.put(field, "string");
        return schema;
    }

    private UUID id(int suffix) {
        return UUID.fromString("ce1e0000-0000-4000-8000-%012d".formatted(suffix));
    }
}
