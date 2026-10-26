package com.ahvakra.camelintegration.connections.api;

import com.ahvakra.camelintegration.connections.application.ConnectionService;
import com.ahvakra.camelintegration.connections.domain.ConnectionTestResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService service;

    public ConnectionController(ConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConnectionResponse> list() {
        return service.list().stream().map(ConnectionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(@RequestBody ConnectionRequest request) {
        return Map.of("id", service.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID id, @RequestBody ConnectionRequest request) {
        service.update(id, request.toCommand());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/test")
    public ConnectionTestResult test(@RequestBody ConnectionRequest request) {
        return service.test(null, request.toCommand());
    }

    @PostMapping("/{id}/test")
    public ConnectionTestResult testSaved(
        @PathVariable UUID id,
        @RequestBody ConnectionRequest request
    ) {
        return service.test(id, request.toCommand());
    }
}
