package com.ahvakra.camelintegration.rules.application;

import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.rules.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConditionalRuleService {

    private final ConditionalRuleStore store;
    private final FieldDefinitionStore fields;
    private final ConditionalValidator validator = new ConditionalValidator();
    private final ConditionalEvaluator evaluator = new ConditionalEvaluator();

    public ConditionalRuleService(ConditionalRuleStore store, FieldDefinitionStore fields) {
        this.store = store;
        this.fields = fields;
    }

    public List<ConditionalRule> list() {
        return store.findAll();
    }

    @Transactional
    public ConditionalRule save(UUID id, ConditionalSpec spec) {
        var existing = id == null ? null : find(id);
        validateMetadata(spec);
        return fields.withLockedDefinition(spec.fieldDefinitionId(), definition -> {
            var references = validator.validate(spec, definition.fields());
            var clean = new ConditionalSpec(
                spec.name().trim(),
                spec.description() == null ? "" : spec.description().trim(),
                spec.fieldDefinitionId(),
                spec.expression()
            );
            var now = Instant.now();
            var rule = new ConditionalRule(
                id == null ? UUID.randomUUID() : id,
                clean,
                references,
                existing == null ? now : existing.createdAt(),
                now
            );
            store.save(rule);
            return rule;
        });
    }

    public ConditionalEvaluator.Result test(ConditionalSpec spec, Map<String, Object> record) {
        validateMetadata(spec);
        var definition = fields
            .findById(spec.fieldDefinitionId())
            .orElseThrow(FieldsNotFoundException::new);
        validator.validate(spec, definition.fields());
        return evaluator.evaluate(spec.expression(), definition.fields(), record);
    }

    public ConditionalEvaluator.Result apply(UUID id, Map<String, Object> record) {
        return test(find(id).spec(), record);
    }

    @Transactional
    public void delete(UUID id) {
        find(id);
        store.delete(id);
    }

    private ConditionalRule find(UUID id) {
        return store.findById(id).orElseThrow(ConditionalRuleNotFoundException::new);
    }

    private void validateMetadata(ConditionalSpec spec) {
        if (
            spec == null ||
            spec.name() == null ||
            spec.name().isBlank() ||
            spec.name().length() > 120
        ) throw new InvalidRuleException("Enter a rule name of up to 120 characters.");
        if (
            spec.description() != null && spec.description().length() > 2000
        ) throw new InvalidRuleException("Description must have up to 2000 characters.");
        if (spec.fieldDefinitionId() == null) throw new InvalidRuleException(
            "Choose a Fields definition."
        );
    }
}
