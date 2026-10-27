package com.ahvakra.camelintegration.fields.api;

import com.ahvakra.camelintegration.fields.application.FieldDefinitionCommand;
import java.util.Map;

public record FieldDefinitionRequest(String name, Map<String, String> fields) {
    public FieldDefinitionCommand toCommand() {
        return new FieldDefinitionCommand(name, fields);
    }
}
