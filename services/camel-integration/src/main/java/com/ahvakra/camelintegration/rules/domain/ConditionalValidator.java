package com.ahvakra.camelintegration.rules.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConditionalValidator {

    public Map<String, String> validate(ConditionalSpec spec, Map<String, String> fields) {
        var references = new LinkedHashMap<String, String>();
        visit(spec.expression(), fields, references, 1, new int[] { 0 });
        return references;
    }

    private void visit(
        ConditionalExpression node,
        Map<String, String> fields,
        Map<String, String> references,
        int depth,
        int[] count
    ) {
        if (node == null || node.type() == null) throw new InvalidRuleException(
            "Provide a condition or group."
        );
        if (depth > 8 || ++count[0] > 100) throw new InvalidRuleException(
            "A rule can have up to 8 levels and 100 conditions/groups."
        );
        if (node.type() == ConditionalNodeType.GROUP) {
            if (
                node.junction() == null || node.children() == null || node.children().isEmpty()
            ) throw new InvalidRuleException(
                "Each AND/OR group must contain at least one condition or group."
            );
            if (
                node.field() != null ||
                node.operator() != null ||
                node.operandType() != null ||
                node.value() != null ||
                node.otherField() != null
            ) throw new InvalidRuleException("Groups must only contain a junction and children.");
            for (var child : node.children()) visit(child, fields, references, depth + 1, count);
            return;
        }
        if (node.junction() != null || node.children() != null) throw new InvalidRuleException(
            "A comparison cannot contain child groups."
        );
        String type = fieldType(node.field(), fields, references);
        if (
            node.operator() == null || !ConditionalValues.supports(type, node.operator())
        ) throw new InvalidRuleException(
            "Choose an operator supported by field '" + node.field() + "' (" + type + ")."
        );
        if (node.operator().unary()) {
            if (
                node.operandType() != null || node.value() != null || node.otherField() != null
            ) throw new InvalidRuleException(
                "Null checks do not accept a value or comparison field."
            );
            return;
        }
        if (node.operandType() == ConditionalOperandType.FIELD) {
            String right = fieldType(node.otherField(), fields, references);
            if (
                node.value() != null || !ConditionalValues.compatible(type, right)
            ) throw new InvalidRuleException(
                "Choose a compatible comparison field; numeric types may be compared with each other."
            );
        } else if (node.operandType() == ConditionalOperandType.VALUE) {
            if (node.otherField() != null) throw new InvalidRuleException(
                "Choose either a value or another field."
            );
            if (
                node.value() instanceof String text && text.length() > 10000
            ) throw new InvalidRuleException("Comparison values may have up to 10000 characters.");
            try {
                ConditionalValues.convert(type, node.value());
            } catch (RuntimeException e) {
                throw new InvalidRuleException(
                    "Invalid " +
                        type +
                        " value for '" +
                        node.field() +
                        "'. Use ISO dates and datetimes with a timezone offset (for example 2026-10-28T12:00:00Z)."
                );
            }
        } else throw new InvalidRuleException("Choose a comparison value or another field.");
    }

    private String fieldType(
        String key,
        Map<String, String> fields,
        Map<String, String> references
    ) {
        String type = key == null ? null : fields.get(key);
        if (type == null) throw new InvalidRuleException(
            "Choose a field from the selected Fields definition."
        );
        references.put(key, type);
        return type;
    }
}
