package com.ahvakra.camelintegration.tables.infrastructure;

import com.ahvakra.camelintegration.connections.domain.ConnectionStore;
import com.ahvakra.camelintegration.fields.domain.FieldDefinitionStore;
import com.ahvakra.camelintegration.tables.application.TableDefinitionService;
import com.ahvakra.camelintegration.tables.domain.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TablesConfiguration {

    @Bean
    TableDefinitionService tableDefinitionService(
        TableDefinitionStore store,
        FieldDefinitionStore fields,
        ConnectionStore connections,
        TableCreator creator,
        TableSchemaManager schema
    ) {
        return new TableDefinitionService(store, fields, connections, creator, schema);
    }
}
