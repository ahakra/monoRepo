package com.ahvakra.camelintegration.rules.domain;

import java.util.UUID;

public record NormalizeSpec(
    String name,
    String description,
    UUID fieldDefinitionId,
    String sourceField,
    NormalizeCondition condition,
    String conditionValue,
    NormalizeAction action,
    String actionValue,
    String destinationField,
    String replacementValue
) {}
