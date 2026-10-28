package com.ahvakra.camelintegration.rules.infrastructure.persistence;

import com.ahvakra.camelintegration.rules.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "etl_normalize_rules")
public class NormalizeRuleEntity {

    @Id
    private UUID id;

    @Column(name = "field_definition_id", nullable = false)
    private UUID fieldDefinitionId;

    @Column(name = "source_field", nullable = false, length = 63)
    private String sourceField;

    @Column(name = "destination_field", nullable = false, length = 63)
    private String destinationField;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "condition_value", nullable = false, length = 2000)
    private String conditionValue;

    @Column(name = "action_value", nullable = false, length = 2000)
    private String actionValue;

    @Column(name = "replacement_value", nullable = false, length = 2000)
    private String replacementValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 40)
    private NormalizeCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private NormalizeAction action;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NormalizeRuleEntity() {}

    public static NormalizeRuleEntity from(NormalizeRule rule) {
        var entity = new NormalizeRuleEntity();
        entity.id = rule.id();
        entity.createdAt = rule.createdAt();
        entity.updatedAt = rule.updatedAt();
        entity.fieldDefinitionId = rule.spec().fieldDefinitionId();
        entity.sourceField = rule.spec().sourceField();
        entity.destinationField = rule.spec().destinationField();
        entity.name = rule.spec().name();
        entity.description = rule.spec().description();
        entity.conditionValue = rule.spec().conditionValue();
        entity.actionValue = rule.spec().actionValue();
        entity.replacementValue = rule.spec().replacementValue();
        entity.condition = rule.spec().condition();
        entity.action = rule.spec().action();
        return entity;
    }

    public NormalizeRule toDomain() {
        return new NormalizeRule(
            id,
            new NormalizeSpec(
                name,
                description,
                fieldDefinitionId,
                sourceField,
                condition,
                conditionValue,
                action,
                actionValue,
                destinationField,
                replacementValue
            ),
            createdAt,
            updatedAt
        );
    }
}
