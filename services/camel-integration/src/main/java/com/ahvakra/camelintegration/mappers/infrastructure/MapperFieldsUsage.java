package com.ahvakra.camelintegration.mappers.infrastructure;

import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.mappers.domain.MapperStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MapperFieldsUsage implements FieldDefinitionUsage {

    private final MapperStore mappers;

    public MapperFieldsUsage(MapperStore mappers) {
        this.mappers = mappers;
    }

    public void validateUpdate(FieldDefinition definition) {
        for (var mapper : mappers.findByFieldDefinitionId(definition.id())) {
            mapper
                .outputs()
                .stream()
                .filter(output -> output.fieldDefinitionId().equals(definition.id()))
                .forEach(output ->
                    output.fields().forEach((key, type) -> {
                        if (
                            !type.equals(definition.fields().get(key))
                        ) throw new FieldsInUseException(
                            "Field '" +
                                key +
                                "' is used by mapper '" +
                                mapper.name() +
                                "', output '" +
                                output.name() +
                                "'. Edit or delete that mapper output before removing the field or changing its type."
                        );
                    })
                );
        }
    }

    public List<FieldReference> references(UUID id) {
        return mappers
            .findByFieldDefinitionId(id)
            .stream()
            .flatMap(mapper ->
                mapper
                    .outputs()
                    .stream()
                    .filter(output -> output.fieldDefinitionId().equals(id))
                    .map(output ->
                        new FieldReference(
                            mapper.id(),
                            "Mapper: " + mapper.name() + " / " + output.name(),
                            output.fields()
                        )
                    )
            )
            .toList();
    }
}
