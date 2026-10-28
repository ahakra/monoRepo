package com.ahvakra.camelintegration.transformers.application;

import com.ahvakra.camelintegration.transformers.domain.TransformerPortKind;
import java.util.List;
import java.util.UUID;

public record TransformerCommand(
    String name,
    String description,
    String directory,
    String wasmFileName,
    List<Port> inputs,
    List<Port> outputs
) {
    public record Port(String name, TransformerPortKind kind, UUID fieldDefinitionId) {}
}
