package com.ahvakra.camelintegration.transformers.api;

import com.ahvakra.camelintegration.transformers.application.*;
import com.ahvakra.camelintegration.transformers.domain.TransformerDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transformers")
public class TransformerController {

    private final TransformerService service;

    public TransformerController(TransformerService service) {
        this.service = service;
    }

    @GetMapping
    public List<TransformerDefinition> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransformerDefinition create(@RequestBody TransformerCommand request) {
        return service.save(null, request);
    }

    @PutMapping("/{id}")
    public TransformerDefinition update(
        @PathVariable UUID id,
        @RequestBody TransformerCommand request
    ) {
        return service.save(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
