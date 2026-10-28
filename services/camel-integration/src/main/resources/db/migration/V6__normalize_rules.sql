CREATE TABLE etl_normalize_rules (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    field_definition_id UUID NOT NULL REFERENCES etl_field_definitions(id),
    source_field VARCHAR(63) NOT NULL,
    destination_field VARCHAR(63) NOT NULL,
    condition_type VARCHAR(40) NOT NULL,
    condition_value VARCHAR(2000) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    action_value VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX etl_normalize_rules_fields_idx ON etl_normalize_rules(field_definition_id);
