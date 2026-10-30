package com.ahvakra.camelintegration.engine;

import static com.ahvakra.camelintegration.engine.EngineValues.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ahvakra.camelintegration.connections.domain.*;
import com.ahvakra.camelintegration.workflows.application.*;
import com.ahvakra.camelintegration.workflows.domain.Workflow;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.apache.camel.CamelContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
class DynamicEngineTests {

    @Autowired
    ApacheCamelDynamicEngine engine;

    @Autowired
    WorkflowCompiler compiler;

    @Autowired
    WorkflowService workflows;

    @Autowired
    ConnectionStore connections;

    @Autowired
    WasmRuntime wasm;

    @Autowired
    com.ahvakra.camelintegration.fields.domain.FieldDefinitionStore fields;

    @Autowired
    CamelContext camel;

    @TempDir
    Path temp;

    private final List<UUID> workflowIds = new ArrayList<>();
    private final List<UUID> connectionIds = new ArrayList<>();

    private Workflow fixture(String csv) throws Exception {
        Files.createDirectories(temp.resolve("input"));
        if (csv != null) Files.writeString(temp.resolve("input/usage.csv"), csv);
        var seed = workflows.get(UUID.fromString("ce1e0000-0000-4000-8000-000000000070"));
        var nodes = seed.nodes().deepCopy();
        for (var node : nodes) {
            String type = node.path("type").asText();
            if (!Set.of("dataSource", "dataDestination").contains(type)) continue;
            var data = (ObjectNode) node.path("data");
            var saved = connections
                .findById(UUID.fromString(data.path("connectorId").asText()))
                .orElseThrow();
            UUID id = UUID.randomUUID();
            connectionIds.add(id);
            var settings = new HashMap<>(saved.settings());
            String directory = type.equals("dataSource") ? "input" : node.path("id").asText();
            settings.put("directoryName", temp.resolve(directory).toString());
            settings.put("readLock", "none");
            settings.put("delay", "50");
            settings.put("initialDelay", "0");
            var now = Instant.now();
            connections.save(
                new ConnectionDefinition(id, "Engine test " + id, "file", settings, "", now, now)
            );
            data.put("connectorId", id.toString());
        }
        UUID id = UUID.randomUUID();
        workflowIds.add(id);
        return workflows.save(
            null,
            new WorkflowCommand(
                id,
                "Engine test " + id,
                "Isolated file workflow",
                nodes,
                seed.edges(),
                seed.viewport()
            )
        );
    }

    private String sample() throws Exception {
        return Files.readString(Path.of("../../examples/telecom/input/usage.csv"));
    }

    private void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
        assertTrue(ready.getAsBoolean(), "Timed out waiting for file workflow");
    }

    @AfterEach
    void cleanup() {
        for (var id : workflowIds) {
            engine.stop(id);
            workflows.delete(id);
        }
        for (var id : connectionIds) connections.delete(id);
    }

    @Test
    void compilesTwelveNormalizationNodesIntoOneOrderedStageWithoutConsuming() throws Exception {
        var workflow = fixture(sample());
        var plan = compiler.compile(workflow.id());
        var chains = plan
            .stages()
            .stream()
            .filter(s -> s.kind().equals("normalizeChain"))
            .toList();
        assertEquals(1, chains.size());
        assertEquals(
            List.of(
                "calling-1",
                "calling-2",
                "calling-3",
                "calling-4",
                "calling-5",
                "calling-6",
                "called-1",
                "called-2",
                "called-3",
                "called-4",
                "called-5",
                "called-6"
            ),
            chains.getFirst().nodes()
        );
        assertFalse(engine.status(workflow.id()).running());
        assertTrue(Files.exists(temp.resolve("input/usage.csv")));
        assertFalse(Files.exists(temp.resolve("cdr-file")));
    }

    @Test
    void camelConsumesMapsNormalizesBranchesAndWritesTwoCdrTwoSmsThreeRejected() throws Exception {
        var workflow = fixture(sample().replace(",0.25,", ",0.12345678901234567890123456789,"));
        engine.start(workflow.id());
        await(
            () ->
                engine.status(workflow.id()).successfulFiles() +
                    engine.status(workflow.id()).failedFiles() >
                0
        );
        var status = engine.status(workflow.id());
        assertEquals(0, status.failedFiles(), status.lastError());
        assertEquals(1, status.successfulFiles());
        await(() -> Files.exists(temp.resolve("input/.processed/usage.csv")));
        assertCsv("cdr-file/cdr.csv", Set.of("CALL-001", "CALL-002"), true);
        assertTrue(
            Files.readString(temp.resolve("cdr-file/cdr.csv")).contains(
                "0.12345678901234567890123456789"
            )
        );
        assertCsv("sms-file/sms.csv", Set.of("SMS-001", "SMS-002"), true);
        assertCsv("rejected-file/rejected.csv", Set.of("BAD-001", "BAD-002", "BAD-003"), false);
        assertThrows(ResponseStatusException.class, () -> engine.start(workflow.id()));
        assertThrows(ResponseStatusException.class, () -> workflows.delete(workflow.id()));
        var routeIds = status.stageRouteIds();
        assertFalse(engine.stop(workflow.id()).running());
        for (var route : routeIds) assertNull(camel.getRoute(route));
        assertFalse(engine.stop(workflow.id()).running());
    }

    private void assertCsv(String file, Set<String> expected, boolean normalized) throws Exception {
        var lines = Files.readAllLines(temp.resolve(file));
        assertEquals(expected.size() + 1, lines.size());
        var header = Arrays.asList(lines.getFirst().split(","));
        int recordId = header.indexOf("record_id");
        var actual = new HashSet<String>();
        for (var line : lines.subList(1, lines.size())) {
            var values = line.split(",", -1);
            actual.add(values[recordId]);
            if (normalized) {
                assertEquals("+96171123456", values[header.indexOf("normalizedcalling")]);
                assertEquals("+96176234567", values[header.indexOf("normalizedcalled")]);
            }
        }
        assertEquals(expected, actual);
    }

    @Test
    void invalidCsvFailsTheExchangeMovesInputToInvalidAndWritesNoOutput() throws Exception {
        var workflow = fixture(sample().replace(",125,", ",broken,"));
        engine.start(workflow.id());
        await(() -> engine.status(workflow.id()).failedFiles() > 0);
        await(() -> Files.exists(temp.resolve("input/.invalid/usage.csv")));
        assertEquals(0, engine.status(workflow.id()).successfulFiles());
        assertTrue(engine.status(workflow.id()).lastError().contains("integer"));
        assertFalse(Files.exists(temp.resolve("cdr-file/cdr.csv")));
        assertFalse(Files.exists(temp.resolve("sms-file/sms.csv")));
    }

    @Test
    void compilerEndsNormalizationFusionAtFanOut() throws Exception {
        var workflow = fixture(null);
        var nodes = (tools.jackson.databind.node.ArrayNode) workflow.nodes().deepCopy();
        for (var node : workflow.nodes())
            if (node.path("id").asText().equals("rejected-file")) {
                var extra = (ObjectNode) node.deepCopy();
                extra.put("id", "early-output");
                nodes.add(extra);
            }
        var edges = (tools.jackson.databind.node.ArrayNode) workflow.edges().deepCopy();
        edges
            .addObject()
            .put("id", "early-output")
            .put("source", "calling-3")
            .put("target", "early-output");
        workflows.save(
            workflow.id(),
            new WorkflowCommand(
                workflow.id(),
                workflow.name(),
                workflow.description(),
                nodes,
                edges,
                workflow.viewport()
            )
        );
        var chains = compiler
            .compile(workflow.id())
            .stages()
            .stream()
            .filter(stage -> stage.kind().equals("normalizeChain"))
            .toList();
        assertEquals(2, chains.size());
        assertEquals(3, chains.get(0).nodes().size());
        assertEquals(9, chains.get(1).nodes().size());
    }

    @Test
    void rawFilePathCopiesBytesWithLiteralDirectoryNames() throws Exception {
        temp = temp.resolve("literal path & values");
        var workflow = fixture("raw bytes, no mapping\n");
        var nodes = JSON.createArrayNode();
        for (var node : workflow.nodes())
            if (Set.of("source", "cdr-file").contains(node.path("id").asText())) nodes.add(node);
        var edges = JSON.createArrayNode()
            .addObject()
            .put("id", "raw")
            .put("source", "source")
            .put("target", "cdr-file");
        workflows.save(
            workflow.id(),
            new WorkflowCommand(
                workflow.id(),
                workflow.name(),
                workflow.description(),
                nodes,
                JSON.createArrayNode().add(edges),
                workflow.viewport()
            )
        );
        engine.start(workflow.id());
        await(
            () ->
                engine.status(workflow.id()).successfulFiles() +
                    engine.status(workflow.id()).failedFiles() >
                0
        );
        assertEquals(
            0,
            engine.status(workflow.id()).failedFiles(),
            engine.status(workflow.id()).lastError()
        );
        assertEquals("raw bytes, no mapping\n", Files.readString(temp.resolve("cdr-file/cdr.csv")));
    }

    @Test
    void apiStartsStopsAndRejectsIncompletePlans() throws Exception {
        var controller = new EngineController(engine, compiler, workflows);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            controller
        ).build();
        var workflow = fixture(null);
        String base = "/api/workflows/" + workflow.id() + "/engine";
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base + "/plan")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()
        );
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                base + "/start"
            )
        )
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()
            )
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.running"
                ).value(true)
            );
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                base + "/start"
            )
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict()
        );
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base + "/stop")
        )
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()
            )
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.running"
                ).value(false)
            );
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/workflows/" + UUID.randomUUID() + "/engine"
            )
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound()
        );
        var source = workflow.nodes().get(0);
        workflows.save(
            workflow.id(),
            new WorkflowCommand(
                workflow.id(),
                workflow.name(),
                workflow.description(),
                JSON.createArrayNode().add(source),
                JSON.createArrayNode(),
                workflow.viewport()
            )
        );
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                base + "/start"
            )
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest()
        );
        assertFalse(engine.status(workflow.id()).running());
    }

    @Test
    void existingDestinationIsPreservedAndSourceFails() throws Exception {
        var workflow = fixture(sample());
        Files.createDirectories(temp.resolve("cdr-file"));
        Files.writeString(temp.resolve("cdr-file/cdr.csv"), "existing data");
        engine.start(workflow.id());
        await(() -> engine.status(workflow.id()).failedFiles() > 0);
        await(() -> Files.exists(temp.resolve("input/.invalid/usage.csv")));
        assertEquals("existing data", Files.readString(temp.resolve("cdr-file/cdr.csv")));
        assertEquals(0, engine.status(workflow.id()).successfulFiles());
    }

    @Test
    void wasmExecutesMultipleNamedInputsAndOutputsAndRejectsMismatchedManifests() throws Exception {
        var mapper = wasm.load("../../examples/telecom/wasm", "parse-usage.wasm");
        var mixed = new Port("usage", "FIELD_LIST", null, schema(1));
        mapper.validate("mapper", List.of(), List.of(mixed));
        var records = mapper.invoke(
            sample().getBytes(java.nio.charset.StandardCharsets.UTF_8),
            List.of(mixed)
        );
        var module = wasm.load("../../examples/telecom/wasm", "summarize-usage.wasm");
        var projection = wasm.load("../../examples/telecom/wasm", "project-cdr.wasm");
        assertThrows(IllegalArgumentException.class, () ->
            projection.validate("mapper", List.of(), List.of(mixed))
        );
        var cdr = new Port("cdr", "FIELD_LIST", null, schema(2));
        var sms = new Port("sms", "FIELD_LIST", null, schema(3));
        var rejected = new Port("rejected", "FIELD_LIST", null, mixed.fields());
        var batch = new Port("batch", "DYNAMIC_VARIABLE", null, Map.of());
        var summary = new Port("summary", "DYNAMIC_VARIABLE", null, Map.of());
        module.validate("transformer", List.of(mixed, batch), List.of(cdr, sms, rejected, summary));
        var request = JSON.createObjectNode().put("abiVersion", 1);
        request.set("inputs", JSON.createObjectNode().set("usage", records.get("usage")));
        ((ObjectNode) request.path("inputs")).put("batch", "java-batch");
        var result = module.invoke(
            JSON.writeValueAsBytes(request),
            List.of(cdr, sms, rejected, summary)
        );
        assertEquals("java-batch", result.path("summary").path("batch").asText());
        assertEquals(3, result.path("cdr").size());
        assertEquals(3, result.path("sms").size());
        assertEquals(1, result.path("rejected").size());
    }

    private Map<String, String> schema(int suffix) {
        return fields
            .findById(UUID.fromString("ce1e0000-0000-4000-8000-%012d".formatted(suffix)))
            .orElseThrow()
            .fields();
    }

    @Test
    void wasmLimitsAndDecimalPrecisionAreEnforced() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
            new WasmRuntime(67108864, 1, 1000000).load(
                "../../examples/telecom/wasm",
                "parse-usage.wasm"
            )
        );
        assertThrows(IllegalArgumentException.class, () ->
            new WasmRuntime(67108864, 4096, 1).load(
                "../../examples/telecom/wasm",
                "parse-usage.wasm"
            )
        );
        var mapper = wasm.load("../../examples/telecom/wasm", "parse-usage.wasm");
        var port = new Port("usage", "FIELD_LIST", null, schema(1));
        var result = mapper.invoke(
            sample()
                .replace(",0.25,", ",0.12345678901234567890123456789,")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            List.of(port)
        );
        assertEquals(
            new java.math.BigDecimal("0.12345678901234567890123456789"),
            result.path("usage").get(0).path("charge_amount").decimalValue()
        );
    }
}
