package com.ahvakra.camelintegration.transformers.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public record TransformerDefinition(
    UUID id,
    String name,
    String description,
    String directory,
    String wasmFileName,
    List<TransformerPort> inputs,
    List<TransformerPort> outputs,
    Instant createdAt,
    Instant updatedAt
) {
    public TransformerDefinition {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public Stream<TransformerPort> ports() {
        return Stream.concat(inputs.stream(), outputs.stream());
    }
}
