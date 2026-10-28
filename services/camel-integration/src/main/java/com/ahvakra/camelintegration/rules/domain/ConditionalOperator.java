package com.ahvakra.camelintegration.rules.domain;

public enum ConditionalOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    STARTS_WITH,
    ENDS_WITH,
    CONTAINS,
    IS_NULL,
    IS_NOT_NULL;

    public boolean unary() {
        return this == IS_NULL || this == IS_NOT_NULL;
    }
}
