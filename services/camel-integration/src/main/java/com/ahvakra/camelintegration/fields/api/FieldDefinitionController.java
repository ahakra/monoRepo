package com.ahvakra.camelintegration.fields.api;

import com.ahvakra.camelintegration.fields.application.FieldDefinitionService;
import com.ahvakra.camelintegration.fields.domain.FieldDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fields")
public class FieldDefinitionController {

    private final FieldDefinitionService service;

    public FieldDefinitionController(FieldDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<FieldDefinitionResponse> list() {
        return service.list().stream().map(this::response).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FieldDefinitionResponse create(@RequestBody FieldDefinitionRequest request) {
        return response(service.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public FieldDefinitionResponse update(
        @PathVariable UUID id,
        @RequestBody FieldDefinitionRequest request
    ) {
        return response(service.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    private FieldDefinitionResponse response(FieldDefinition definition) {
        return FieldDefinitionResponse.from(definition, service.references(definition.id()));
    }
}
