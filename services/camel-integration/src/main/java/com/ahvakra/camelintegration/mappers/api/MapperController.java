package com.ahvakra.camelintegration.mappers.api;

import com.ahvakra.camelintegration.mappers.application.*;
import com.ahvakra.camelintegration.mappers.domain.MapperDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mappers")
public class MapperController {

    private final MapperService service;

    public MapperController(MapperService service) {
        this.service = service;
    }

    @GetMapping
    public List<MapperDefinition> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MapperDefinition create(@RequestBody MapperCommand request) {
        return service.save(null, request);
    }

    @PutMapping("/{id}")
    public MapperDefinition update(@PathVariable UUID id, @RequestBody MapperCommand request) {
        return service.save(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
