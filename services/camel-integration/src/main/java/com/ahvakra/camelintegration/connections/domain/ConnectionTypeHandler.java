package com.ahvakra.camelintegration.connections.domain;

import java.util.Map;

public interface ConnectionTypeHandler {
    String type();
    Map<String, String> validateSettings(Map<String, String> settings);
    ConnectionTestResult test(ConnectionDefinition connection);
}
