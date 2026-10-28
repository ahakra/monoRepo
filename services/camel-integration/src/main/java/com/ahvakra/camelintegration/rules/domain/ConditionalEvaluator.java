package com.ahvakra.camelintegration.rules.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConditionalEvaluator {

    public record Result(boolean matched, Map<String, Object> record) {
        public Result {
            record = Collections.unmodifiableMap(new LinkedHashMap<>(record));
        }
    }

    public Result evaluate(
        ConditionalExpression expression,
        Map<String, String> types,
        Map<String, Object> record
    ) {
        if (record == null) throw new InvalidRuleException("Provide a record to test.");
        return new Result(matches(expression, types, record), record);
    }

    private boolean matches(
        ConditionalExpression node,
        Map<String, String> types,
        Map<String, Object> record
    ) {
        if (node.type() == ConditionalNodeType.GROUP) {
            return node.junction() == ConditionalJunction.AND
                ? node
                      .children()
                      .stream()
                      .allMatch(child -> matches(child, types, record))
                : node
                      .children()
                      .stream()
                      .anyMatch(child -> matches(child, types, record));
        }
        Object left = record.get(node.field());
        if (node.operator() == ConditionalOperator.IS_NULL) return left == null;
        if (node.operator() == ConditionalOperator.IS_NOT_NULL) return left != null;
        Object right =
            node.operandType() == ConditionalOperandType.FIELD
                ? record.get(node.otherField())
                : node.value();
        // A missing/null or invalid runtime operand never satisfies a binary comparison, even NOT_EQUALS.
        if (left == null || right == null) return false;
        try {
            Object convertedLeft = ConditionalValues.convert(types.get(node.field()), left);
            Object convertedRight = ConditionalValues.convert(
                node.operandType() == ConditionalOperandType.FIELD
                    ? types.get(node.otherField())
                    : types.get(node.field()),
                right
            );
            return ConditionalValues.compare(node.operator(), convertedLeft, convertedRight);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
