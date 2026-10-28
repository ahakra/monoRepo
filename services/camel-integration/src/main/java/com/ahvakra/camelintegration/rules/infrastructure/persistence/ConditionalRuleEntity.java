package com.ahvakra.camelintegration.rules.infrastructure.persistence;

import com.ahvakra.camelintegration.rules.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@Entity
@Table(name = "etl_conditional_rules")
public class ConditionalRuleEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "field_definition_id", nullable = false)
    private UUID fieldDefinitionId;

    @Column(name = "expression_json", nullable = false, columnDefinition = "TEXT")
    private String expressionJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(
        name = "etl_conditional_rule_fields",
        joinColumns = @JoinColumn(name = "rule_id")
    )
    @MapKeyColumn(name = "field_key", length = 63)
    @Column(name = "field_type", nullable = false, length = 20)
    private Map<String, String> referencedFields = new HashMap<>();

    protected ConditionalRuleEntity() {}

    public static ConditionalRuleEntity from(ConditionalRule rule, JsonMapper mapper) {
        var entity = new ConditionalRuleEntity();
        entity.id = rule.id();
        entity.name = rule.spec().name();
        entity.description = rule.spec().description();
        entity.fieldDefinitionId = rule.spec().fieldDefinitionId();
        entity.expressionJson = mapper.writeValueAsString(rule.spec().expression());
        entity.referencedFields = new HashMap<>(rule.referencedFields());
        entity.createdAt = rule.createdAt();
        entity.updatedAt = rule.updatedAt();
        return entity;
    }

    public ConditionalRule toDomain(JsonMapper mapper) {
        return new ConditionalRule(
            id,
            new ConditionalSpec(
                name,
                description,
                fieldDefinitionId,
                mapper.readValue(expressionJson, ConditionalExpression.class)
            ),
            referencedFields,
            createdAt,
            updatedAt
        );
    }
}
