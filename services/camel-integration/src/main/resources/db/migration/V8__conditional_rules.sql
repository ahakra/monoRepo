CREATE TABLE etl_conditional_rules (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    field_definition_id UUID NOT NULL REFERENCES etl_field_definitions(id),
    expression_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX etl_conditional_rules_fields_idx ON etl_conditional_rules(field_definition_id);

CREATE TABLE etl_conditional_rule_fields (
    rule_id UUID NOT NULL REFERENCES etl_conditional_rules(id) ON DELETE CASCADE,
    field_key VARCHAR(63) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (rule_id, field_key)
);
