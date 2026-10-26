package com.ahvakra.camelintegration.connections.domain;

public class ConnectionNotFoundException extends RuntimeException {

    public ConnectionNotFoundException() {
        super("Connection not found.");
    }
}
