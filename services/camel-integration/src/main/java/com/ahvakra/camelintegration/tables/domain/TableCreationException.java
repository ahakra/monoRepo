package com.ahvakra.camelintegration.tables.domain;

public class TableCreationException extends RuntimeException {

    private final boolean conflict;

    public TableCreationException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean conflict() {
        return conflict;
    }
}
