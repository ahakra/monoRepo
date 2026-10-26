package com.ahvakra.camelintegration.connections.infrastructure;

import com.ahvakra.camelintegration.connections.application.ConnectionService;
import com.ahvakra.camelintegration.connections.domain.*;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConnectionConfiguration {

    @Bean
    ConnectionService connectionService(
        ConnectionStore store,
        List<ConnectionTypeHandler> handlers
    ) {
        return new ConnectionService(store, handlers);
    }
}
