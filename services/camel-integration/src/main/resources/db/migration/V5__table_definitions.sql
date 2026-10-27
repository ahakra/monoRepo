CREATE TABLE etl_table_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    connection_id UUID NOT NULL REFERENCES etl_connections(id),
    field_definition_id UUID NOT NULL REFERENCES etl_field_definitions(id),
    schema_name VARCHAR(63) NOT NULL,
    table_name VARCHAR(63) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (connection_id, schema_name, table_name)
);

CREATE TABLE etl_table_columns (
    table_id UUID NOT NULL REFERENCES etl_table_definitions(id) ON DELETE CASCADE,
    column_key VARCHAR(63) NOT NULL,
    column_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (table_id, column_key)
);
