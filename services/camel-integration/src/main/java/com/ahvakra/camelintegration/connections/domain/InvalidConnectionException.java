package com.ahvakra.camelintegration.connections.domain;

public class InvalidConnectionException extends RuntimeException {

    public InvalidConnectionException(String message) {
        super(message);
    }
}
