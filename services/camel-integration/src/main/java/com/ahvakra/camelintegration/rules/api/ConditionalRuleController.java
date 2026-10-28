package com.ahvakra.camelintegration.rules.api;

import com.ahvakra.camelintegration.rules.application.ConditionalRuleService;
import com.ahvakra.camelintegration.rules.domain.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rules/conditional")
public class ConditionalRuleController {

    private final ConditionalRuleService service;

    public ConditionalRuleController(ConditionalRuleService service) {
        this.service = service;
    }

    public record TestRequest(ConditionalSpec spec, Map<String, Object> record) {}

    @GetMapping
    public List<ConditionalRule> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConditionalRule create(@RequestBody ConditionalSpec spec) {
        return service.save(null, spec);
    }

    @PutMapping("/{id}")
    public ConditionalRule update(@PathVariable UUID id, @RequestBody ConditionalSpec spec) {
        return service.save(id, spec);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/test")
    public ConditionalEvaluator.Result test(@RequestBody TestRequest request) {
        return service.test(request.spec(), request.record());
    }

    @PostMapping("/{id}/apply")
    public ConditionalEvaluator.Result apply(
        @PathVariable UUID id,
        @RequestBody Map<String, Object> record
    ) {
        return service.apply(id, record);
    }
}
