package com.ahvakra.camelintegration.rules.infrastructure;

import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.rules.domain.ConditionalRuleStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConditionalFieldsUsage implements FieldDefinitionUsage {

    private final ConditionalRuleStore rules;

    public ConditionalFieldsUsage(ConditionalRuleStore rules) {
        this.rules = rules;
    }

    public void validateUpdate(FieldDefinition definition) {
        for (var rule : rules.findByFieldDefinitionId(definition.id())) {
            rule.referencedFields().forEach((key, type) -> {
                if (!type.equals(definition.fields().get(key))) throw new FieldsInUseException(
                    "Field '" +
                        key +
                        "' is used by Conditional rule '" +
                        rule.spec().name() +
                        "'. Edit or delete that rule before removing it or changing its type."
                );
            });
        }
    }

    public List<FieldReference> references(UUID id) {
        return rules
            .findByFieldDefinitionId(id)
            .stream()
            .map(rule ->
                new FieldReference(
                    rule.id(),
                    "Conditional rule: " + rule.spec().name(),
                    rule.referencedFields()
                )
            )
            .toList();
    }
}
