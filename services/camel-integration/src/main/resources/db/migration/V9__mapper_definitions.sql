CREATE TABLE etl_mappers (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    directory VARCHAR(2048) NOT NULL,
    wasm_file_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE etl_mapper_outputs (
    mapper_id UUID NOT NULL REFERENCES etl_mappers(id) ON DELETE CASCADE,
    output_order INTEGER NOT NULL,
    list_name VARCHAR(120) NOT NULL,
    field_definition_id UUID NOT NULL REFERENCES etl_field_definitions(id),
    fields_json TEXT NOT NULL,
    PRIMARY KEY (mapper_id, output_order),
    UNIQUE (mapper_id, list_name)
);

CREATE INDEX etl_mapper_outputs_fields_idx ON etl_mapper_outputs(field_definition_id);
