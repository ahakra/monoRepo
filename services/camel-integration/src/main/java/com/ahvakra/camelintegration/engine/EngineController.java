package com.ahvakra.camelintegration.engine;

import com.ahvakra.camelintegration.workflows.application.WorkflowService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workflows/{id}/engine")
public class EngineController {

    private final ApacheCamelDynamicEngine engine;
    private final WorkflowCompiler compiler;
    private final WorkflowService workflows;
    private final ExecutionHistoryRepository history;

    public EngineController(
        ApacheCamelDynamicEngine engine,
        WorkflowCompiler compiler,
        WorkflowService workflows,
        ExecutionHistoryRepository history
    ) {
        this.engine = engine;
        this.compiler = compiler;
        this.workflows = workflows;
        this.history = history;
    }

    @GetMapping("/plan")
    public Map<String, Object> plan(@PathVariable UUID id) {
        return compiler.describe(id);
    }

    @GetMapping
    public ApacheCamelDynamicEngine.Status status(@PathVariable UUID id) {
        workflows.get(id);
        return engine.status(id);
    }

    @GetMapping("/events")
    public WorkflowTraceStore.Page events(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") long after,
        @RequestParam(defaultValue = "2000") int limit
    ) {
        workflows.get(id);
        return engine.events(id, after, limit);
    }

    @GetMapping("/batches")
    public ExecutionHistoryRepository.BatchPage batches(
        @PathVariable UUID id,
        @RequestParam(required = false) Long before,
        @RequestParam(defaultValue = "50") int limit
    ) {
        workflows.get(id);
        return history.batches(id, before, limit);
    }

    @GetMapping("/batches/{executionId}")
    public ExecutionHistoryRepository.Detail batch(
        @PathVariable UUID id,
        @PathVariable UUID executionId
    ) {
        workflows.get(id);
        return history.detail(id, executionId);
    }

    @PostMapping("/start")
    public ApacheCamelDynamicEngine.Status start(@PathVariable UUID id) {
        return engine.start(id);
    }

    @PostMapping("/stop")
    public ApacheCamelDynamicEngine.Status stop(@PathVariable UUID id) {
        workflows.get(id);
        return engine.stop(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail status(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason());
    }
}
