package com.ahvakra.camelintegration.rules.api;

import com.ahvakra.camelintegration.rules.application.NormalizeRuleService;
import com.ahvakra.camelintegration.rules.domain.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rules/normalize")
public class NormalizeRuleController {

    private final NormalizeRuleService service;

    public NormalizeRuleController(NormalizeRuleService service) {
        this.service = service;
    }

    public record TestRequest(NormalizeSpec spec, Map<String, Object> record) {}

    @GetMapping
    public List<NormalizeRule> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NormalizeRule create(@RequestBody NormalizeSpec spec) {
        return service.save(null, spec);
    }

    @PutMapping("/{id}")
    public NormalizeRule update(@PathVariable UUID id, @RequestBody NormalizeSpec spec) {
        return service.save(id, spec);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/test")
    public NormalizeEvaluator.Result test(@RequestBody TestRequest request) {
        return service.test(request.spec(), request.record());
    }

    @PostMapping("/{id}/apply")
    public NormalizeEvaluator.Result apply(
        @PathVariable UUID id,
        @RequestBody Map<String, Object> record
    ) {
        return service.apply(id, record);
    }
}
