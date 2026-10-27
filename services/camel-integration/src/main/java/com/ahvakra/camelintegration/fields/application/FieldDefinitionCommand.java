package com.ahvakra.camelintegration.fields.application;

import java.util.Map;

public record FieldDefinitionCommand(String name, Map<String, String> fields) {}
