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
public class NormalizeRuleService {

    private final NormalizeRuleStore store;
    private final FieldDefinitionStore fields;
    private final NormalizeEvaluator evaluator = new NormalizeEvaluator();

    public NormalizeRuleService(NormalizeRuleStore store, FieldDefinitionStore fields) {
        this.store = store;
        this.fields = fields;
    }

    public List<NormalizeRule> list() {
        return store.findAll();
    }

    @Transactional
    public NormalizeRule save(UUID id, NormalizeSpec spec) {
        var existing = id == null ? null : find(id);
        validate(spec);
        return fields.withLockedDefinition(spec.fieldDefinitionId(), definition -> {
            validateFields(spec, definition);
            var clean = new NormalizeSpec(
                spec.name().trim(),
                spec.description() == null ? "" : spec.description().trim(),
                spec.fieldDefinitionId(),
                spec.sourceField(),
                spec.condition(),
                spec.conditionValue(),
                spec.action(),
                needsActionValue(spec.action()) ? spec.actionValue() : "",
                spec.destinationField(),
                spec.action() == NormalizeAction.REPLACE ? spec.replacementValue() : ""
            );
            var now = Instant.now();
            var rule = new NormalizeRule(
                id == null ? UUID.randomUUID() : id,
                clean,
                existing == null ? now : existing.createdAt(),
                now
            );
            store.save(rule);
            return rule;
        });
    }

    public NormalizeEvaluator.Result test(NormalizeSpec spec, Map<String, Object> record) {
        validate(spec);
        validateFields(
            spec,
            fields.findById(spec.fieldDefinitionId()).orElseThrow(FieldsNotFoundException::new)
        );
        return evaluator.evaluate(spec, record);
    }

    public NormalizeEvaluator.Result apply(UUID id, Map<String, Object> record) {
        return test(find(id).spec(), record);
    }

    @Transactional
    public void delete(UUID id) {
        find(id);
        store.delete(id);
    }

    private NormalizeRule find(UUID id) {
        return store.findById(id).orElseThrow(RuleNotFoundException::new);
    }

    private void validate(NormalizeSpec spec) {
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
        if (spec.condition() == null || spec.action() == null) throw new InvalidRuleException(
            "Choose a condition and an action."
        );
        if (
            spec.conditionValue() == null || spec.conditionValue().length() > 2000
        ) throw new InvalidRuleException("Provide a condition value of up to 2000 characters.");
        if (spec.condition().name().startsWith("LENGTH_")) {
            try {
                if (
                    !spec.conditionValue().matches("[0-9]+") ||
                    Integer.parseInt(spec.conditionValue()) < 0
                ) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new InvalidRuleException(
                    "Length must be a non-negative whole number up to 2147483647."
                );
            }
        } else if (
            spec.condition() != NormalizeCondition.EQUALS && spec.conditionValue().isEmpty()
        ) {
            throw new InvalidRuleException("Enter text for the condition.");
        }
        if (
            needsActionValue(spec.action()) &&
            (spec.actionValue() == null ||
                spec.actionValue().isEmpty() ||
                spec.actionValue().length() > 2000)
        ) throw new InvalidRuleException(
            "Enter prefix, suffix, or find text of up to 2000 characters."
        );
        if (
            spec.action() == NormalizeAction.REPLACE &&
            (spec.replacementValue() == null || spec.replacementValue().length() > 2000)
        ) throw new InvalidRuleException(
            "Provide replacement text of up to 2000 characters. Use an empty string to remove the find text."
        );
    }

    private static boolean needsActionValue(NormalizeAction action) {
        return switch (action) {
            case ADD_PREFIX, ADD_SUFFIX, REMOVE_PREFIX, REMOVE_SUFFIX, REPLACE -> true;
            default -> false;
        };
    }

    private void validateFields(NormalizeSpec spec, FieldDefinition definition) {
        for (String key : new String[] { spec.sourceField(), spec.destinationField() }) {
            String type = key == null ? null : definition.fields().get(key);
            if (!"string".equals(type) && !"text".equals(type)) throw new InvalidRuleException(
                "Source and destination must be string/text fields from the selected Fields definition."
            );
        }
    }
}
