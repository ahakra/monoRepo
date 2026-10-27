-- A reusable sample definition, seeded once. No call records or target tables are created.
INSERT INTO etl_field_definitions (id, name, created_at, updated_at)
VALUES ('cd000000-0000-4000-8000-000000000001', 'CDR (Call Detail Record)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_field_entries (definition_id, field_key, field_type) VALUES
('cd000000-0000-4000-8000-000000000001', 'id', 'uuid'),
('cd000000-0000-4000-8000-000000000001', 'datetime', 'datetime'),
('cd000000-0000-4000-8000-000000000001', 'call_id', 'string'),
('cd000000-0000-4000-8000-000000000001', 'caller_number', 'string'),
('cd000000-0000-4000-8000-000000000001', 'called_number', 'string'),
('cd000000-0000-4000-8000-000000000001', 'start_time', 'datetime'),
('cd000000-0000-4000-8000-000000000001', 'end_time', 'datetime'),
('cd000000-0000-4000-8000-000000000001', 'duration_seconds', 'integer'),
('cd000000-0000-4000-8000-000000000001', 'direction', 'string'),
('cd000000-0000-4000-8000-000000000001', 'call_status', 'string'),
('cd000000-0000-4000-8000-000000000001', 'charge_amount', 'decimal'),
('cd000000-0000-4000-8000-000000000001', 'currency', 'string');
