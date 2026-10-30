package com.ahvakra.camelintegration.workflows.api;

import com.ahvakra.camelintegration.workflows.application.*;
import com.ahvakra.camelintegration.workflows.domain.Workflow;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @GetMapping
    public List<Workflow> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Workflow get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Workflow create(@RequestBody WorkflowCommand command) {
        return service.save(null, command);
    }

    @PutMapping("/{id}")
    public Workflow update(@PathVariable UUID id, @RequestBody WorkflowCommand command) {
        return service.save(id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
