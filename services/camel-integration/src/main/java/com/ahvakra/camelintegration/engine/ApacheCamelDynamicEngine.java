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
    private final Map<UUID, Running> running = new ConcurrentHashMap<>();

    public ApacheCamelDynamicEngine(
        CamelContext camel,
        WorkflowCompiler compiler,
        WasmRuntime wasm
    ) {
        this.camel = camel;
        this.compiler = compiler;
        this.wasm = wasm;
    }

    public synchronized Status start(UUID id) {
        if (running.containsKey(id) && running.get(id).active) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Workflow is already running; stop it before restarting"
        );
        var plan = compiler.compile(id);
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
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Another workflow is already consuming this source directory"
            );
        }
        var runtime = new Running(sourceDirectory);
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
                                step.process(exchange ->
                                    exchange
                                        .getMessage()
                                        .setBody(state(exchange).take(stage).get("in"))
                                )
                                    .filter(
                                        exchange ->
                                            !empty(exchange.getMessage().getBody(JsonNode.class))
                                    )
                                    .process(exchange -> {
                                        var value = exchange.getMessage().getBody(JsonNode.class);
                                        exchange.getMessage().removeHeader(Exchange.FILE_NAME);
                                        exchange
                                            .getMessage()
                                            .removeHeader(Exchange.OVERRULE_FILE_NAME);
                                        exchange
                                            .getMessage()
                                            .setBody(
                                                serialize(
                                                    value,
                                                    stage.inputs().get("in"),
                                                    Charset.forName(
                                                        stage
                                                            .destination()
                                                            .settings()
                                                            .getOrDefault("charset", "UTF-8")
                                                    )
                                                )
                                            );
                                    })
                                    .to(destination)
                                    .end();
                            } else {
                                step.process(exchange -> {
                                    var state = state(exchange);
                                    var outputs = stage.operation().apply(state.take(stage));
                                    stage
                                        .outputs()
                                        .forEach((handle, port) ->
                                            port.validate(outputs.get(handle))
                                        );
                                    state.forward(plan, stage.outputNode(), outputs);
                                });
                            }
                        }
                        runtime.sourceRoute = "etl-" + id + "-source";
                        var root = from(fileEndpoint(plan.source(), true))
                            .routeId(runtime.sourceRoute)
                            .autoStartup(false)
                            .process(exchange -> {
                                exchange.getUnitOfWork().addSynchronization(
                                    new Synchronization() {
                                        public void onComplete(Exchange done) {
                                            runtime.successful.incrementAndGet();
                                            runtime.lastCompletedAt = Instant.now();
                                        }

                                        public void onFailure(Exchange failed) {
                                            runtime.failed.incrementAndGet();
                                            runtime.lastCompletedAt = Instant.now();
                                            Throwable cause = failed.getException();
                                            if (cause == null) cause = failed.getProperty(
                                                Exchange.EXCEPTION_CAUGHT,
                                                Throwable.class
                                            );
                                            runtime.lastError =
                                                cause == null
                                                    ? "Exchange failed"
                                                    : cause.getMessage();
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
            camel.getRouteController().startRoute(runtime.sourceRoute);
            return status(id);
        } catch (Exception e) {
            runtime.active = false;
            cleanup(runtime);
            running.remove(id);
            throw new IllegalArgumentException("Cannot start workflow: " + e.getMessage(), e);
        }
    }

    public synchronized Status stop(UUID id) {
        var runtime = running.get(id);
        if (runtime == null) return status(id);
        // Drain the source exchange before stopping any synchronous downstream consumer.
        cleanup(runtime);
        runtime.active = false;
        return status(id);
    }

    @Override
    public boolean isRunning(UUID id) {
        var runtime = running.get(id);
        return runtime != null && runtime.active;
    }

    public Status status(UUID id) {
        var runtime = running.get(id);
        if (runtime == null) return new Status(id, false, 0, 0, null, null, null, List.of());
        return new Status(
            id,
            runtime.active,
            runtime.successful.get(),
            runtime.failed.get(),
            runtime.startedAt,
            runtime.lastCompletedAt,
            runtime.lastError,
            List.copyOf(runtime.routes)
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
        List<String> stageRouteIds
    ) {}

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
        for (var runtime : running.values())
            if (runtime.active) {
                cleanup(runtime);
                runtime.active = false;
            }
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

        Running(String sourceDirectory) {
            this.sourceDirectory = sourceDirectory;
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
