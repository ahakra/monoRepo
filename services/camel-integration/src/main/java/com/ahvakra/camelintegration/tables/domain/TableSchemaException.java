package com.ahvakra.camelintegration.tables.domain;

public class TableSchemaException extends RuntimeException {

    private final boolean conflict;

    public TableSchemaException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean conflict() {
        return conflict;
    }
}
