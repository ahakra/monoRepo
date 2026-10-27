package com.ahvakra.camelintegration.tables.domain;

public record SchemaChange(
    String id,
    String action,
    String columnName,
    String currentType,
    String targetType,
    String sql,
    boolean destructive
) {}
