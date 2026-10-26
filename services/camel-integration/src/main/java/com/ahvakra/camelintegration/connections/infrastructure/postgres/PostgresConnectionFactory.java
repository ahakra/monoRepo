package com.ahvakra.camelintegration.connections.infrastructure.postgres;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class PostgresConnectionFactory {

    public Connection open(ConnectionDefinition definition, String database) throws SQLException {
        var settings = definition.settings();
        var properties = new Properties();
        properties.setProperty("user", settings.get("username"));
        properties.setProperty("password", definition.password());
        properties.setProperty("sslmode", settings.get("sslMode"));
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "10");
        return DriverManager.getConnection(
            "jdbc:postgresql://" +
                settings.get("host") +
                ":" +
                settings.get("port") +
                "/" +
                database,
            properties
        );
    }
}
