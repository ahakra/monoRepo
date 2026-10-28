package com.ahvakra.camelintegration.rules.domain;

public class ConditionalRuleNotFoundException extends RuntimeException {

    public ConditionalRuleNotFoundException() {
        super("Conditional rule not found.");
    }
}
