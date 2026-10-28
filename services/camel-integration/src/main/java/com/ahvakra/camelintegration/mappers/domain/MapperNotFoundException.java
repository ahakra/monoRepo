package com.ahvakra.camelintegration.mappers.domain;

public class MapperNotFoundException extends RuntimeException {

    public MapperNotFoundException() {
        super("Mapper not found.");
    }
}
