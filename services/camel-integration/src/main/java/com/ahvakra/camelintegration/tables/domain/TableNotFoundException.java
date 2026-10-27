package com.ahvakra.camelintegration.tables.domain;

public class TableNotFoundException extends RuntimeException {

    public TableNotFoundException() {
        super("Table configuration not found.");
    }
}
