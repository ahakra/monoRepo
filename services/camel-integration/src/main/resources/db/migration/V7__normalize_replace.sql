ALTER TABLE etl_normalize_rules
    ADD COLUMN replacement_value VARCHAR(2000) DEFAULT '' NOT NULL;
