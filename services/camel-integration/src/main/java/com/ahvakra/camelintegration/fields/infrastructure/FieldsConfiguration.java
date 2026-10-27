package com.ahvakra.camelintegration.fields.infrastructure;

import com.ahvakra.camelintegration.fields.application.FieldDefinitionService;
import com.ahvakra.camelintegration.fields.domain.FieldDefinitionStore;
import com.ahvakra.camelintegration.fields.domain.FieldDefinitionUsage;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FieldsConfiguration {

    @Bean
    FieldDefinitionService fieldDefinitionService(
        FieldDefinitionStore store,
        List<FieldDefinitionUsage> usages
    ) {
        return new FieldDefinitionService(store, usages);
    }
}
