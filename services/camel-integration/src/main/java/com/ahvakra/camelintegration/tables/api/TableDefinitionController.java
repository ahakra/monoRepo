package com.ahvakra.camelintegration.tables.api;

import com.ahvakra.camelintegration.tables.application.TableDefinitionService;
import com.ahvakra.camelintegration.tables.domain.SchemaChangeSelection;
import com.ahvakra.camelintegration.tables.domain.TableDefinition;
import com.ahvakra.camelintegration.tables.domain.TableSchemaPlan;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tables")
public class TableDefinitionController {

    private final TableDefinitionService service;

    public TableDefinitionController(TableDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<TableDefinitionResponse> list() {
        return service.list().stream().map(this::response).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableDefinitionResponse create(@RequestBody TableDefinitionRequest request) {
        return response(service.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public TableDefinitionResponse update(
        @PathVariable UUID id,
        @RequestBody TableDefinitionRequest request
    ) {
        return response(service.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/create")
    public Map<String, String> createPhysicalTable(@PathVariable UUID id) {
        service.createPhysicalTable(id);
        return Map.of("message", "PostgreSQL table created successfully.");
    }

    private TableDefinitionResponse response(TableDefinition definition) {
        return TableDefinitionResponse.from(definition, service.sql(definition));
    }

    @GetMapping("/{id}/schema-plan")
    public TableSchemaPlan previewSchema(@PathVariable UUID id) {
        return service.previewSchema(id);
    }

    @PostMapping("/{id}/schema-changes")
    public Map<String, String> applySchema(
        @PathVariable UUID id,
        @RequestBody SchemaChangeSelection selection
    ) {
        service.applySchema(id, selection);
        return Map.of("message", "Selected PostgreSQL column changes applied successfully.");
    }
}
