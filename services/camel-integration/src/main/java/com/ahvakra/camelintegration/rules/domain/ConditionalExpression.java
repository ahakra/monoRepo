package com.ahvakra.camelintegration.rules.domain;

import java.util.List;

public record ConditionalExpression(
    ConditionalNodeType type,
    ConditionalJunction junction,
    List<ConditionalExpression> children,
    String field,
    ConditionalOperator operator,
    ConditionalOperandType operandType,
    Object value,
    String otherField
) {}
