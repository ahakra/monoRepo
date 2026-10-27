package com.ahvakra.camelintegration.fields.domain;

public class FieldsNotFoundException extends RuntimeException {

    public FieldsNotFoundException() {
        super("Field definition not found.");
    }
}
