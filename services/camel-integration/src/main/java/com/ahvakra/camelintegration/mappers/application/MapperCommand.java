package com.ahvakra.camelintegration.mappers.application;

import java.util.List;
import java.util.UUID;

public record MapperCommand(
    String name,
    String description,
    String directory,
    String wasmFileName,
    List<Output> outputs
) {
    public record Output(String name, UUID fieldDefinitionId) {}
}
