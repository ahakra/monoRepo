package com.ahvakra.camelintegration.rules.infrastructure;

import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.rules.domain.NormalizeRuleStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RuleFieldsUsage implements FieldDefinitionUsage {

    private final NormalizeRuleStore rules;

    public RuleFieldsUsage(NormalizeRuleStore rules) {
        this.rules = rules;
    }

    public void validateUpdate(FieldDefinition definition) {
        for (var rule : rules.findByFieldDefinitionId(definition.id())) {
            for (String key : new String[] {
                rule.spec().sourceField(),
                rule.spec().destinationField(),
            }) {
                if (
                    !"string".equals(definition.fields().get(key)) &&
                    !"text".equals(definition.fields().get(key))
                ) throw new FieldsInUseException(
                    "Field '" +
                        key +
                        "' is used by Normalize rule '" +
                        rule.spec().name() +
                        "'. Edit or delete that rule before removing the field or changing its type."
                );
            }
        }
    }

    public List<FieldReference> references(UUID id) {
        return rules
            .findByFieldDefinitionId(id)
            .stream()
            .map(rule -> {
                var fields = new LinkedHashMap<String, String>();
                fields.put(rule.spec().sourceField(), "string");
                fields.put(rule.spec().destinationField(), "string");
                return new FieldReference(
                    rule.id(),
                    "Normalize rule: " + rule.spec().name(),
                    fields
                );
            })
            .toList();
    }
}
