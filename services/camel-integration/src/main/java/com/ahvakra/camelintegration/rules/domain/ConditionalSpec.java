package com.ahvakra.camelintegration.rules.domain;

import java.util.UUID;

public record ConditionalSpec(
    String name,
    String description,
    UUID fieldDefinitionId,
    ConditionalExpression expression
) {}
