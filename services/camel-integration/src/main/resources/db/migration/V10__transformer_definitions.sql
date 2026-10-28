CREATE TABLE etl_transformers (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE etl_transformer_inputs (
    transformer_id UUID NOT NULL REFERENCES etl_transformers(id) ON DELETE CASCADE,
    port_order INTEGER NOT NULL,
    port_name VARCHAR(120) NOT NULL,
    port_kind VARCHAR(30) NOT NULL,
    field_definition_id UUID REFERENCES etl_field_definitions(id),
    fields_json TEXT NOT NULL,
    PRIMARY KEY (transformer_id, port_order),
    UNIQUE (transformer_id, port_name),
    CHECK ((port_kind = 'FIELD_LIST' AND field_definition_id IS NOT NULL)
        OR (port_kind = 'DYNAMIC_VARIABLE' AND field_definition_id IS NULL))
);

CREATE TABLE etl_transformer_outputs (
    transformer_id UUID NOT NULL REFERENCES etl_transformers(id) ON DELETE CASCADE,
    port_order INTEGER NOT NULL,
    port_name VARCHAR(120) NOT NULL,
    port_kind VARCHAR(30) NOT NULL,
    field_definition_id UUID REFERENCES etl_field_definitions(id),
    fields_json TEXT NOT NULL,
    PRIMARY KEY (transformer_id, port_order),
    UNIQUE (transformer_id, port_name),
    CHECK ((port_kind = 'FIELD_LIST' AND field_definition_id IS NOT NULL)
        OR (port_kind = 'DYNAMIC_VARIABLE' AND field_definition_id IS NULL))
);

CREATE INDEX etl_transformer_inputs_fields_idx ON etl_transformer_inputs(field_definition_id);
CREATE INDEX etl_transformer_outputs_fields_idx ON etl_transformer_outputs(field_definition_id);
