package com.ahvakra.camelintegration.connections.infrastructure.persistence;

import com.ahvakra.camelintegration.connections.domain.ConnectionDefinition;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "etl_connections")
public class ConnectionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String password;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(
        name = "etl_connection_settings",
        joinColumns = @JoinColumn(name = "connection_id")
    )
    @MapKeyColumn(name = "setting_key", length = 80)
    @Column(name = "setting_value", nullable = false, length = 2048)
    private Map<String, String> settings = new HashMap<>();

    protected ConnectionEntity() {}

    public static ConnectionEntity from(ConnectionDefinition connection) {
        var entity = new ConnectionEntity();
        entity.id = connection.id();
        entity.name = connection.name();
        entity.type = connection.type();
        entity.password = connection.password();
        entity.settings = new HashMap<>(connection.settings());
        entity.createdAt = connection.createdAt();
        entity.updatedAt = connection.updatedAt();
        return entity;
    }

    public ConnectionDefinition toDomain() {
        return new ConnectionDefinition(id, name, type, settings, password, createdAt, updatedAt);
    }
}
