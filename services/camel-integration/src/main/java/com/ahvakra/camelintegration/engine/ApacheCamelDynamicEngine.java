package com.ahvakra.camelintegration.engine;

import static com.ahvakra.camelintegration.engine.EngineValues.*;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import com.ahvakra.camelintegration.workflows.domain.WorkflowExecutionState;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.*;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.PropertyBindingSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** File -> to(compiled stages) -> to(file destinations), installed only by explicit start. */
@Service
public class ApacheCamelDynamicEngine implements WorkflowExecutionState {

    private static final String STATE = ApacheCamelDynamicEngine.class.getName() + ".state";
    private final CamelContext camel;
    private final WorkflowCompiler compiler;
    private final WasmRuntime wasm;
    private final WorkflowTraceStore traces;
    private static final String CURRENT_STAGE = STATE + ".stage";
    private static final String STAGE_STARTED = STATE + ".stageStarted";
    private static final String FILE_STARTED = STATE + ".fileStarted";
    private final Map<UUID, Running> running = new ConcurrentHashMap<>();

    public ApacheCamelDynamicEngine(
        CamelContext camel,
        WorkflowCompiler compiler,
        WasmRuntime wasm,
        WorkflowTraceStore traces
    ) {
        this.camel = camel;
        this.compiler = compiler;
        this.wasm = wasm;
        this.traces = traces;
    }

    public synchronized Status start(UUID id) {
        if (running.containsKey(id) && running.get(id).active) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Workflow is already running; stop it before restarting"
        );
        UUID runId = UUID.randomUUID();
        traces.emit(
            id,
            runId,
            null,
            "INFO",
            "COMPILING",
            null,
            List.of(),
            null,
            "Validating workflow references, schemas and WASM modules",
            Map.of(),
            null
        );
        ExecutionPlan plan;
        try {
            plan = compiler.compile(id);
        } catch (RuntimeException error) {
            traces.emit(
                id,
                runId,
                null,
                "ERROR",
                "START_FAILED",
                null,
                List.of(),
                null,
                error.getMessage(),
                Map.of(),
                error
            );
            throw error;
        }
        String sourceDirectory = new File(plan.source().settings().get("directoryName"))
            .toPath()
            .toAbsolutePath()
            .normalize()
            .toString();
        if (
            running
                .values()
                .stream()
                .anyMatch(r -> r.active && r.sourceDirectory.equals(sourceDirectory))
        ) {
            traces.emit(
                id,
                runId,
                null,
                "ERROR",
                "START_FAILED",
                null,
                List.of(),
                null,
                "Another workflow is already consuming this source directory",
                Map.of("sourceDirectory", sourceDirectory),
                null
            );
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Another workflow is already consuming this source directory"
            );
        }
        var runtime = new Running(
            sourceDirectory,
            runId,
            new SourceConfiguration(
                plan.source().id(),
                plan.source().name(),
                plan.source().settings(),
                plan.source().updatedAt()
            )
        );
        emit(
            plan,
            runtime,
            null,
            "INFO",
            "PLAN_READY",
            null,
            "Execution plan ready",
            Map.of(
                "stages",
                plan.stages().size(),
                "sourceDirectory",
                sourceDirectory,
                "sourceSettings",
                plan.source().settings(),
                "sourceUpdatedAt",
                plan.source().updatedAt().toString(),
                "nodes",
                java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(plan.sourceNode()),
                    plan
                        .stages()
                        .stream()
                        .sorted(
                            java.util.Comparator.comparing(stage -> stage.destination() != null)
                        )
                        .flatMap(stage -> stage.nodes().stream())
                )
                    .map(nodeId ->
                        Map.of(
                            "id",
                            nodeId,
                            "name",
                            plan.labels().get(nodeId),
                            "type",
                            plan.nodeTypes().get(nodeId)
                        )
                    )
                    .toList(),
                "normalizationChains",
                plan
                    .stages()
                    .stream()
                    .filter(stage -> stage.kind().equals("normalizeChain"))
                    .count()
            ),
            null
        );
        try {
            camel.addRoutes(
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        errorHandler(defaultErrorHandler().maximumRedeliveries(0));
                        var endpoints = new LinkedHashMap<String, String>();
                        int index = 0;
                        for (var stage : plan.stages()) {
                            String route = "etl-" + id + "-stage-" + index++;
                            String endpoint = "direct:" + route;
                            runtime.routes.add(route);
                            endpoints.put(stage.id(), endpoint);
                            var step = from(endpoint).routeId(route).autoStartup(false);
                            if (stage.destination() != null) {
                                var destination = fileEndpoint(stage.destination(), false);
                                step.process(exchange -> {
                                    beginStage(plan, runtime, stage, exchange);
                                    var values = state(exchange).take(stage);
                                    var value = values.get("in");
                                    exchange.getMessage().setBody(value);
                                    if (empty(value)) emit(
                                        plan,
                                        runtime,
                                        exchange,
                                        "INFO",
                                        "STEP_SKIPPED",
                                        stage,
                                        "No records to write",
                                        Map.of(
                                            "inputs",
                                            WorkflowTraceStore.counts(values),
                                            "durationMs",
                                            stageDuration(exchange)
                                        ),
                                        null
                                    );
                                })
                                    .filter(
                                        exchange ->
                                            !empty(exchange.getMessage().getBody(JsonNode.class))
                                    )
                                    .process(exchange -> {
                                        var value = exchange.getMessage().getBody(JsonNode.class);
                                        byte[] output = serialize(
                                            value,
                                            stage.inputs().get("in"),
                                            Charset.forName(
                                                stage
                                                    .destination()
                                                    .settings()
                                                    .getOrDefault("charset", "UTF-8")
                                            )
                                        );
                                        exchange.setProperty(STATE + ".outputBytes", output.length);
                                        exchange.setProperty(
                                            STATE + ".outputRows",
                                            value.isArray() ? value.size() : 1
                                        );
                                        // Every destination evaluates its filename against the original input,
                                        // even after another destination has written a different filename.
                                        String originalName = exchange.getProperty(
                                            WorkflowTraceStore.INPUT_FILE,
                                            String.class
                                        );
                                        require(
                                            originalName != null,
                                            "Source filename is required for a File destination"
                                        );
                                        exchange
                                            .getMessage()
                                            .setHeader(Exchange.FILE_NAME, originalName);
                                        exchange
                                            .getMessage()
                                            .setHeader(
                                                Exchange.FILE_NAME_ONLY,
                                                new File(originalName).getName()
                                            );
                                        exchange
                                            .getMessage()
                                            .removeHeader(Exchange.OVERRULE_FILE_NAME);
                                        exchange
                                            .getMessage()
                                            .removeHeader(Exchange.FILE_NAME_PRODUCED);
                                        exchange.getMessage().setBody(output);
                                    })
                                    .to(destination)
                                    .process(exchange -> {
                                        String path = exchange
                                            .getMessage()
                                            .getHeader(Exchange.FILE_NAME_PRODUCED, String.class);
                                        var details = new LinkedHashMap<String, Object>();
                                        if (path != null) details.put(
                                            "outputPath",
                                            new File(path)
                                                .toPath()
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString()
                                        );
                                        details.put(
                                            "bytes",
                                            exchange.getProperty(STATE + ".outputBytes")
                                        );
                                        details.put(
                                            "records",
                                            exchange.getProperty(STATE + ".outputRows")
                                        );
                                        details.put("durationMs", stageDuration(exchange));
                                        emit(
                                            plan,
                                            runtime,
                                            exchange,
                                            "INFO",
                                            "OUTPUT_WRITTEN",
                                            stage,
                                            "Output file written",
                                            details,
                                            null
                                        );
                                        emit(
                                            plan,
                                            runtime,
                                            exchange,
                                            "INFO",
                                            "STEP_COMPLETED",
                                            stage,
                                            "Destination completed",
                                            details,
                                            null
                                        );
                                    })
                                    .end();
                            } else {
                                step.process(exchange -> {
                                    beginStage(plan, runtime, stage, exchange);
                                    try {
                                        var state = state(exchange);
                                        var inputs = state.take(stage);
                                        var outputs = stage
                                            .operation()
                                            .apply(inputs, summary ->
                                                traces.emit(
                                                    plan.id(),
                                                    runtime.runId,
                                                    exchange,
                                                    "INFO",
                                                    "NORMALIZATION_COMPLETED",
                                                    summary.nodeId(),
                                                    List.of(summary.nodeId()),
                                                    plan.labels().get(summary.nodeId()),
                                                    summary.name() +
                                                        ": " +
                                                        summary.matched() +
                                                        " of " +
                                                        summary.total() +
                                                        " records matched",
                                                    Map.of(
                                                        "matched",
                                                        summary.matched(),
                                                        "total",
                                                        summary.total(),
                                                        "stageType",
                                                        "normalizeRule",
                                                        "nodeLabels",
                                                        Map.of(
                                                            summary.nodeId(),
                                                            plan.labels().get(summary.nodeId())
                                                        )
                                                    ),
                                                    null
                                                )
                                            );
                                        stage
                                            .outputs()
                                            .forEach((handle, port) ->
                                                port.validate(outputs.get(handle))
                                            );
                                        state.forward(plan, stage.outputNode(), outputs);
                                        emit(
                                            plan,
                                            runtime,
                                            exchange,
                                            "INFO",
                                            "STEP_COMPLETED",
                                            stage,
                                            "Step completed",
                                            Map.of(
                                                "inputs",
                                                WorkflowTraceStore.counts(inputs),
                                                "outputs",
                                                WorkflowTraceStore.counts(outputs),
                                                "durationMs",
                                                stageDuration(exchange)
                                            ),
                                            null
                                        );
                                    } catch (RuntimeException error) {
                                        emit(
                                            plan,
                                            runtime,
                                            exchange,
                                            "ERROR",
                                            "STEP_FAILED",
                                            stage,
                                            error.getMessage(),
                                            Map.of("durationMs", stageDuration(exchange)),
                                            error
                                        );
                                        throw error;
                                    }
                                });
                            }
                        }
                        runtime.sourceRoute = "etl-" + id + "-source";
                        var root = from(fileEndpoint(plan.source(), true))
                            .routeId(runtime.sourceRoute)
                            .autoStartup(false)
                            .process(exchange -> {
                                exchange.setProperty(
                                    WorkflowTraceStore.INPUT_FILE,
                                    exchange
                                        .getMessage()
                                        .getHeader(Exchange.FILE_NAME, String.class)
                                );
                                exchange.setProperty(
                                    WorkflowTraceStore.BATCH_DESCRIPTION,
                                    exchange.getProperty(WorkflowTraceStore.INPUT_FILE)
                                );
                                exchange.setProperty(
                                    WorkflowTraceStore.BATCH_CREATED_AT,
                                    Instant.now().toString()
                                );
                                exchange.setProperty(
                                    WorkflowTraceStore.SOURCE_TYPE,
                                    plan.source().type()
                                );
                                exchange.setProperty(FILE_STARTED, System.nanoTime());
                                runtime.currentExchangeId = exchange.getExchangeId();
                                runtime.currentFile = exchange.getProperty(
                                    WorkflowTraceStore.INPUT_FILE,
                                    String.class
                                );
                                runtime.currentStep = plan.labels().get(plan.sourceNode());
                                runtime.currentNodeIds = List.of(plan.sourceNode());
                                runtime.phase = "PROCESSING";
                                emit(
                                    plan,
                                    runtime,
                                    exchange,
                                    "INFO",
                                    "FILE_PICKED",
                                    null,
                                    "Source file picked up",
                                    Map.of("sourceDirectory", sourceDirectory),
                                    null
                                );
                                exchange.getUnitOfWork().addSynchronization(
                                    new Synchronization() {
                                        public void onComplete(Exchange done) {
                                            emit(
                                                plan,
                                                runtime,
                                                done,
                                                "INFO",
                                                "FILE_COMPLETED",
                                                null,
                                                "File processing completed",
                                                Map.of(
                                                    "durationMs",
                                                    fileDuration(done),
                                                    "processedDirectory",
                                                    plan
                                                        .source()
                                                        .settings()
                                                        .getOrDefault("move", ".processed")
                                                ),
                                                null
                                            );
                                            clearCurrent(runtime);
                                            runtime.successful.incrementAndGet();
                                            runtime.lastCompletedAt = Instant.now();
                                        }

                                        public void onFailure(Exchange failed) {
                                            Throwable cause = failed.getException();
                                            if (cause == null) cause = failed.getProperty(
                                                Exchange.EXCEPTION_CAUGHT,
                                                Throwable.class
                                            );
                                            runtime.lastError =
                                                cause == null
                                                    ? "Exchange failed"
                                                    : cause.getMessage();
                                            var failedStage = failed.getProperty(
                                                CURRENT_STAGE,
                                                ExecutionPlan.Stage.class
                                            );
                                            emit(
                                                plan,
                                                runtime,
                                                failed,
                                                "ERROR",
                                                "FILE_FAILED",
                                                failedStage,
                                                runtime.lastError,
                                                Map.of(
                                                    "durationMs",
                                                    fileDuration(failed),
                                                    "failedDirectory",
                                                    plan
                                                        .source()
                                                        .settings()
                                                        .getOrDefault("moveFailed", ".invalid")
                                                ),
                                                cause
                                            );
                                            clearCurrent(runtime);
                                            runtime.failed.incrementAndGet();
                                            runtime.lastCompletedAt = Instant.now();
                                        }
                                    }
                                );
                                byte[] bytes;
                                try (
                                    var input = exchange
                                        .getMessage()
                                        .getMandatoryBody(InputStream.class)
                                ) {
                                    bytes = input.readNBytes(wasm.maxBytes() + 1);
                                }
                                require(
                                    bytes.length > 0 && bytes.length <= wasm.maxBytes(),
                                    "Source file is empty or exceeds engine size limit"
                                );
                                emit(
                                    plan,
                                    runtime,
                                    exchange,
                                    "INFO",
                                    "FILE_READ",
                                    null,
                                    "Source bytes read",
                                    Map.of(
                                        "bytes",
                                        bytes.length,
                                        "durationMs",
                                        fileDuration(exchange)
                                    ),
                                    null
                                );
                                var state = new ExecutionPlan.State();
                                exchange.setProperty(STATE, state);
                                state.forward(
                                    plan,
                                    plan.sourceNode(),
                                    Map.of(
                                        "out",
                                        tools.jackson.databind.node.BinaryNode.valueOf(bytes)
                                    )
                                );
                            });
                        // Complete every computation before writing any destination for this file.
                        for (var stage : plan.stages())
                            if (stage.destination() == null) root.to(endpoints.get(stage.id()));
                        for (var stage : plan.stages())
                            if (stage.destination() != null) root.to(endpoints.get(stage.id()));
                    }
                }
            );
            for (var route : runtime.routes) camel.getRouteController().startRoute(route);
            running.put(id, runtime); // Register before starting the source so first-file counters are visible.
            runtime.active = true;
            runtime.phase = "WAITING";
            emit(
                plan,
                runtime,
                null,
                "INFO",
                "WORKFLOW_STARTED",
                null,
                "Watching source directory; fileName=" +
                    plan.source().settings().getOrDefault("fileName", "(any)") +
                    "; include=" +
                    plan.source().settings().getOrDefault("include", "(any)"),
                Map.of(
                    "sourceConfiguration",
                    runtime.sourceConfiguration,
                    "sourceDirectory",
                    sourceDirectory,
                    "sourceSettings",
                    plan.source().settings(),
                    "sourceUpdatedAt",
                    plan.source().updatedAt().toString()
                ),
                null
            );
            camel.getRouteController().startRoute(runtime.sourceRoute);
            return status(id);
        } catch (Exception e) {
            runtime.active = false;
            emit(plan, runtime, null, "ERROR", "START_FAILED", null, e.getMessage(), Map.of(), e);
            cleanup(runtime);
            running.remove(id);
            throw new IllegalArgumentException("Cannot start workflow: " + e.getMessage(), e);
        }
    }

    public synchronized Status stop(UUID id) {
        var runtime = running.get(id);
        if (runtime == null) return status(id);
        if (!runtime.active) return status(id);
        runtime.phase = "STOPPING";
        traces.emit(
            id,
            runtime.runId,
            null,
            "INFO",
            "STOP_REQUESTED",
            null,
            List.of(),
            null,
            "Stopping source and waiting for the current file to finish",
            Map.of(),
            null
        );
        // Drain the source exchange before stopping any synchronous downstream consumer.
        cleanup(runtime);
        runtime.active = false;
        runtime.phase = "STOPPED";
        traces.emit(
            id,
            runtime.runId,
            null,
            "INFO",
            "WORKFLOW_STOPPED",
            null,
            List.of(),
            null,
            "Workflow stopped",
            Map.of(),
            null
        );
        return status(id);
    }

    @Override
    public boolean isRunning(UUID id) {
        var runtime = running.get(id);
        return runtime != null && runtime.active;
    }

    public Status status(UUID id) {
        var runtime = running.get(id);
        if (runtime == null) {
            var previous = traces.history().latestRun(id);
            if (previous.isPresent()) {
                var run = previous.get();
                return new Status(
                    id,
                    false,
                    run.successfulFiles(),
                    run.failedFiles(),
                    run.createdAt(),
                    run.completedAt(),
                    run.error(),
                    List.of(),
                    run.id(),
                    run.status(),
                    null,
                    null,
                    List.of(),
                    null,
                    run.sourceConfiguration()
                );
            }
            return new Status(
                id,
                false,
                0,
                0,
                null,
                null,
                null,
                List.of(),
                null,
                "STOPPED",
                null,
                null,
                List.of(),
                null,
                null
            );
        }
        return new Status(
            id,
            runtime.active,
            runtime.successful.get(),
            runtime.failed.get(),
            runtime.startedAt,
            runtime.lastCompletedAt,
            runtime.lastError,
            List.copyOf(runtime.routes),
            runtime.runId,
            runtime.phase,
            runtime.currentFile,
            runtime.currentStep,
            runtime.currentNodeIds,
            runtime.currentExchangeId,
            runtime.sourceConfiguration
        );
    }

    public record Status(
        UUID workflowId,
        boolean running,
        long successfulFiles,
        long failedFiles,
        Instant startedAt,
        Instant lastCompletedAt,
        String lastError,
        List<String> stageRouteIds,
        UUID runId,
        String phase,
        String currentFile,
        String currentStep,
        List<String> currentNodeIds,
        String currentExchangeId,
        SourceConfiguration sourceConfiguration
    ) {}

    public record SourceConfiguration(
        UUID connectorId,
        String name,
        Map<String, String> settings,
        Instant updatedAt
    ) {
        public SourceConfiguration {
            settings = Map.copyOf(settings);
        }
    }

    public WorkflowTraceStore.Page events(UUID id, long after, int limit) {
        return traces.read(id, after, limit);
    }

    private void beginStage(
        ExecutionPlan plan,
        Running runtime,
        ExecutionPlan.Stage stage,
        Exchange exchange
    ) {
        exchange.setProperty(CURRENT_STAGE, stage);
        exchange.setProperty(STAGE_STARTED, System.nanoTime());
        runtime.currentStep = plan.label(stage);
        runtime.currentNodeIds = stage.nodes();
        emit(
            plan,
            runtime,
            exchange,
            "INFO",
            "STEP_STARTED",
            stage,
            "Step started",
            Map.of("kind", stage.kind()),
            null
        );
    }

    private void emit(
        ExecutionPlan plan,
        Running runtime,
        Exchange exchange,
        String level,
        String type,
        ExecutionPlan.Stage stage,
        String message,
        Map<String, Object> details,
        Throwable failure
    ) {
        String node = stage == null ? (exchange == null ? null : plan.sourceNode()) : stage.id();
        var enriched = new LinkedHashMap<String, Object>(details);
        enriched.put(
            "stageType",
            stage == null ? (node == null ? "workflow" : "dataSource") : stage.kind()
        );
        var labels = new LinkedHashMap<String, String>();
        if (stage != null) stage.nodes().forEach(id -> labels.put(id, plan.labels().get(id)));
        else if (node != null) labels.put(node, plan.labels().get(node));
        enriched.put("nodeLabels", labels);
        traces.emit(
            plan.id(),
            runtime.runId,
            exchange,
            level,
            type,
            node,
            stage == null ? (node == null ? List.of() : List.of(node)) : stage.nodes(),
            stage == null ? (node == null ? null : plan.labels().get(node)) : plan.label(stage),
            message,
            enriched,
            failure
        );
    }

    private static long stageDuration(Exchange exchange) {
        return Math.max(
            0,
            (System.nanoTime() - exchange.getProperty(STAGE_STARTED, Long.class)) / 1_000_000
        );
    }

    private static long fileDuration(Exchange exchange) {
        return Math.max(
            0,
            (System.nanoTime() - exchange.getProperty(FILE_STARTED, Long.class)) / 1_000_000
        );
    }

    private static void clearCurrent(Running runtime) {
        runtime.currentExchangeId = null;
        runtime.currentStep = null;
        runtime.currentFile = null;
        runtime.currentNodeIds = List.of();
        if (!runtime.phase.equals("STOPPING")) runtime.phase = runtime.active
            ? "WAITING"
            : "STOPPED";
    }

    private FileEndpoint fileEndpoint(ConnectionDefinition connection, boolean consumer) {
        var settings = connection.settings();
        String directory = new File(settings.get("directoryName")).getAbsolutePath();
        // Construct the endpoint directly: paths and setting values never become executable URI query text.
        var endpoint = new FileEndpoint(
            "file:" + directory + "?connection=" + connection.id(),
            camel.getComponent("file")
        );
        endpoint.setFile(new File(directory));
        var options = new HashMap<String, Object>();
        settings.forEach((key, value) -> {
            if (
                !key.equals("directoryName") &&
                (consumer || Set.of("fileName", "charset", "autoCreate").contains(key))
            ) options.put(key, value);
        });
        PropertyBindingSupport.bindProperties(camel, endpoint, options);
        require(options.isEmpty(), "Unsupported File endpoint settings: " + options.keySet());
        if (!consumer) {
            endpoint.setFileExist(GenericFileExist.Fail);
            endpoint.setTempPrefix(".writing-");
            if (!settings.containsKey("fileName")) endpoint.setFileName("${file:onlyname}");
        }
        return endpoint;
    }

    private void cleanup(Running runtime) {
        var routes = new ArrayList<String>();
        if (runtime.sourceRoute != null) routes.add(runtime.sourceRoute);
        var stages = new ArrayList<>(runtime.routes);
        Collections.reverse(stages);
        routes.addAll(stages);
        for (var route : routes) {
            try {
                if (camel.getRoute(route) != null) {
                    camel.getRouteController().stopRoute(route, 10, TimeUnit.SECONDS);
                    require(camel.removeRoute(route), "Could not remove route " + route);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not stop workflow route " + route, e);
            }
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        for (var id : running.keySet()) if (isRunning(id)) stop(id);
    }

    private static ExecutionPlan.State state(Exchange exchange) {
        return exchange.getProperty(STATE, ExecutionPlan.State.class);
    }

    private static boolean empty(JsonNode value) {
        return value.isArray() && value.isEmpty();
    }

    private static byte[] serialize(JsonNode value, Port port, Charset charset) {
        if (port.kind().equals("BYTES")) return value.binaryValue();
        if (port.kind().equals("DYNAMIC_VARIABLE")) return (
            JSON.writeValueAsString(value) + "\n"
        ).getBytes(charset);
        var keys = new ArrayList<>(port.fields().keySet());
        Collections.sort(keys);
        var csv = new StringBuilder();
        csv.append(String.join(",", keys)).append('\n');
        for (var record : value) {
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) csv.append(',');
                var cell = record.get(keys.get(i));
                String text = cell.isNull()
                    ? ""
                    : cell.isString()
                      ? cell.asText()
                      : JSON.writeValueAsString(cell);
                if (
                    text.contains(",") ||
                    text.contains("\"") ||
                    text.contains("\r") ||
                    text.contains("\n")
                ) csv.append('"').append(text.replace("\"", "\"\"")).append('"');
                else csv.append(text);
            }
            csv.append('\n');
        }
        return csv.toString().getBytes(charset);
    }

    private static final class Running {

        final String sourceDirectory;
        final UUID runId;
        final SourceConfiguration sourceConfiguration;
        volatile String phase = "STARTING";
        volatile String currentFile;
        volatile String currentExchangeId;
        volatile String currentStep;
        volatile List<String> currentNodeIds = List.of();

        Running(String sourceDirectory, UUID runId, SourceConfiguration sourceConfiguration) {
            this.sourceDirectory = sourceDirectory;
            this.runId = runId;
            this.sourceConfiguration = sourceConfiguration;
        }

        final Instant startedAt = Instant.now();
        final AtomicLong successful = new AtomicLong();
        final AtomicLong failed = new AtomicLong();
        final List<String> routes = new ArrayList<>();
        volatile boolean active;
        volatile Instant lastCompletedAt;
        volatile String lastError;
        String sourceRoute;
    }
}
