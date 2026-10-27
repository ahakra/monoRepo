CREATE TABLE etl_field_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE etl_field_entries (
    definition_id UUID NOT NULL REFERENCES etl_field_definitions(id) ON DELETE CASCADE,
    field_key VARCHAR(63) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (definition_id, field_key),
    CHECK (field_type IN ('string', 'integer', 'long', 'decimal', 'boolean', 'date', 'datetime', 'uuid', 'json'))
);
