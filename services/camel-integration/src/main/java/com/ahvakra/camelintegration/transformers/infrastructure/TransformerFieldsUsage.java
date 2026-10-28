package com.ahvakra.camelintegration.transformers.infrastructure;

import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.transformers.domain.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TransformerFieldsUsage implements FieldDefinitionUsage {

    private final TransformerStore transformers;

    public TransformerFieldsUsage(TransformerStore transformers) {
        this.transformers = transformers;
    }

    public void validateUpdate(FieldDefinition definition) {
        for (var transformer : transformers.findByFieldDefinitionId(definition.id())) {
            transformer
                .ports()
                .filter(port -> definition.id().equals(port.fieldDefinitionId()))
                .forEach(port ->
                    port.fields().forEach((key, type) -> {
                        if (
                            !type.equals(definition.fields().get(key))
                        ) throw new FieldsInUseException(
                            "Field '" +
                                key +
                                "' is used by transformer '" +
                                transformer.name() +
                                "', port '" +
                                port.name() +
                                "'. Edit or delete that port before removing the field or changing its type."
                        );
                    })
                );
        }
    }

    public List<FieldReference> references(UUID id) {
        return transformers
            .findByFieldDefinitionId(id)
            .stream()
            .flatMap(transformer ->
                transformer
                    .ports()
                    .filter(port -> id.equals(port.fieldDefinitionId()))
                    .map(port ->
                        new FieldReference(
                            transformer.id(),
                            "Transformer: " + transformer.name() + " / " + port.name(),
                            port.fields()
                        )
                    )
            )
            .toList();
    }
}
