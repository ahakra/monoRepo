package com.ahvakra.camelintegration.rules.domain;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException() {
        super("Normalize rule not found.");
    }
}
