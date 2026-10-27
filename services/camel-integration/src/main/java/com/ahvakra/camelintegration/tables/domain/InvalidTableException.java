package com.ahvakra.camelintegration.tables.domain;

public class InvalidTableException extends RuntimeException {

    public InvalidTableException(String message) {
        super(message);
    }
}
