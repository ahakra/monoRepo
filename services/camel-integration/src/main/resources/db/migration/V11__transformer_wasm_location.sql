ALTER TABLE etl_transformers
    ADD COLUMN directory VARCHAR(2048) DEFAULT '' NOT NULL;

ALTER TABLE etl_transformers
    ADD COLUMN wasm_file_name VARCHAR(255) DEFAULT '' NOT NULL;
