package com.ahvakra.camelintegration.tables.infrastructure;

import com.ahvakra.camelintegration.fields.domain.*;
import com.ahvakra.camelintegration.tables.domain.TableDefinitionStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TableFieldsUsage implements FieldDefinitionUsage {

    private final TableDefinitionStore tables;

    public TableFieldsUsage(TableDefinitionStore tables) {
        this.tables = tables;
    }

    public void validateUpdate(FieldDefinition definition) {
        for (var table : tables.findByFieldDefinitionId(definition.id())) {
            table.columns().forEach((key, type) -> {
                if (!type.equals(definition.fields().get(key))) throw new FieldsInUseException(
                    "Field '" +
                        key +
                        "' is used by table configuration '" +
                        table.name() +
                        "'. Remove it from that table configuration before removing it or changing its type."
                );
            });
        }
    }

    public List<FieldReference> references(UUID id) {
        return tables
            .findByFieldDefinitionId(id)
            .stream()
            .map(table -> new FieldReference(table.id(), table.name(), table.columns()))
            .toList();
    }
}
