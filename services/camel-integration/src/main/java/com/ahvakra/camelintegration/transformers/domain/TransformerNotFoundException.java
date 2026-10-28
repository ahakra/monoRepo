package com.ahvakra.camelintegration.transformers.domain;

public class TransformerNotFoundException extends RuntimeException {

    public TransformerNotFoundException() {
        super("Transformer not found.");
    }
}
