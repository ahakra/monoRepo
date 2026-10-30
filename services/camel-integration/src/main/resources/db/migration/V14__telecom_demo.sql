-- Telecom design demo, seeded once without changing existing user definitions.
-- Mapper/transformer WASM paths describe future modules; no execution is enabled.

INSERT INTO etl_field_definitions (id, name, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000001', 'Telecom - mixed usage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_field_entries (definition_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000001', 'id', 'uuid'),
('ce1e0000-0000-4000-8000-000000000001', 'datetime', 'datetime'),
('ce1e0000-0000-4000-8000-000000000001', 'record_id', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'record_type', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'calling', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'called', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'normalizedcalling', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'normalizedcalled', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'start_time', 'datetime'),
('ce1e0000-0000-4000-8000-000000000001', 'end_time', 'datetime'),
('ce1e0000-0000-4000-8000-000000000001', 'duration_seconds', 'integer'),
('ce1e0000-0000-4000-8000-000000000001', 'sms_count', 'integer'),
('ce1e0000-0000-4000-8000-000000000001', 'direction', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'status', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'charge_amount', 'decimal'),
('ce1e0000-0000-4000-8000-000000000001', 'currency', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'imsi', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'imei', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'cell_id', 'string'),
('ce1e0000-0000-4000-8000-000000000001', 'operator_code', 'string');

INSERT INTO etl_field_definitions (id, name, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000002', 'Telecom - CDR output', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_field_entries (definition_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000002', 'id', 'uuid'),
('ce1e0000-0000-4000-8000-000000000002', 'datetime', 'datetime'),
('ce1e0000-0000-4000-8000-000000000002', 'record_id', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'calling', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'called', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'normalizedcalling', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'normalizedcalled', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'start_time', 'datetime'),
('ce1e0000-0000-4000-8000-000000000002', 'end_time', 'datetime'),
('ce1e0000-0000-4000-8000-000000000002', 'duration_seconds', 'integer'),
('ce1e0000-0000-4000-8000-000000000002', 'direction', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'status', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'charge_amount', 'decimal'),
('ce1e0000-0000-4000-8000-000000000002', 'currency', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'imsi', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'imei', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'cell_id', 'string'),
('ce1e0000-0000-4000-8000-000000000002', 'operator_code', 'string');

INSERT INTO etl_field_definitions (id, name, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000003', 'Telecom - SMS output', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_field_entries (definition_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000003', 'id', 'uuid'),
('ce1e0000-0000-4000-8000-000000000003', 'datetime', 'datetime'),
('ce1e0000-0000-4000-8000-000000000003', 'record_id', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'calling', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'called', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'normalizedcalling', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'normalizedcalled', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'start_time', 'datetime'),
('ce1e0000-0000-4000-8000-000000000003', 'sms_count', 'integer'),
('ce1e0000-0000-4000-8000-000000000003', 'direction', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'status', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'charge_amount', 'decimal'),
('ce1e0000-0000-4000-8000-000000000003', 'currency', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'imsi', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'imei', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'cell_id', 'string'),
('ce1e0000-0000-4000-8000-000000000003', 'operator_code', 'string');

INSERT INTO etl_connections (id, name, type, password, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000010', 'Telecom - input CSV', 'file', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_connection_settings (connection_id, setting_key, setting_value) VALUES
('ce1e0000-0000-4000-8000-000000000010', 'directoryName', '../../examples/telecom/input'),
('ce1e0000-0000-4000-8000-000000000010', 'fileName', 'usage.csv'),
('ce1e0000-0000-4000-8000-000000000010', 'charset', 'UTF-8'),
('ce1e0000-0000-4000-8000-000000000010', 'move', '.processed'),
('ce1e0000-0000-4000-8000-000000000010', 'moveFailed', '.invalid'),
('ce1e0000-0000-4000-8000-000000000010', 'autoCreate', 'true'),
('ce1e0000-0000-4000-8000-000000000010', 'recursive', 'false'),
('ce1e0000-0000-4000-8000-000000000010', 'readLock', 'changed'),
('ce1e0000-0000-4000-8000-000000000010', 'delay', '1000'),
('ce1e0000-0000-4000-8000-000000000010', 'initialDelay', '1000'),
('ce1e0000-0000-4000-8000-000000000010', 'readLockTimeout', '10000'),
('ce1e0000-0000-4000-8000-000000000010', 'readLockCheckInterval', '1000'),
('ce1e0000-0000-4000-8000-000000000010', 'readLockMinAge', '0'),
('ce1e0000-0000-4000-8000-000000000010', 'readLockMinLength', '1'),
('ce1e0000-0000-4000-8000-000000000010', 'maxMessagesPerPoll', '1');

INSERT INTO etl_connections (id, name, type, password, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000011', 'Telecom - CDR files', 'file', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_connection_settings (connection_id, setting_key, setting_value) VALUES
('ce1e0000-0000-4000-8000-000000000011', 'directoryName', '../../examples/telecom/output/cdr'),
('ce1e0000-0000-4000-8000-000000000011', 'fileName', 'cdr.csv'),
('ce1e0000-0000-4000-8000-000000000011', 'charset', 'UTF-8'),
('ce1e0000-0000-4000-8000-000000000011', 'move', '.processed'),
('ce1e0000-0000-4000-8000-000000000011', 'moveFailed', '.invalid'),
('ce1e0000-0000-4000-8000-000000000011', 'autoCreate', 'true'),
('ce1e0000-0000-4000-8000-000000000011', 'recursive', 'false'),
('ce1e0000-0000-4000-8000-000000000011', 'readLock', 'changed'),
('ce1e0000-0000-4000-8000-000000000011', 'delay', '1000'),
('ce1e0000-0000-4000-8000-000000000011', 'initialDelay', '1000'),
('ce1e0000-0000-4000-8000-000000000011', 'readLockTimeout', '10000'),
('ce1e0000-0000-4000-8000-000000000011', 'readLockCheckInterval', '1000'),
('ce1e0000-0000-4000-8000-000000000011', 'readLockMinAge', '0'),
('ce1e0000-0000-4000-8000-000000000011', 'readLockMinLength', '1'),
('ce1e0000-0000-4000-8000-000000000011', 'maxMessagesPerPoll', '1');

INSERT INTO etl_connections (id, name, type, password, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000012', 'Telecom - SMS files', 'file', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_connection_settings (connection_id, setting_key, setting_value) VALUES
('ce1e0000-0000-4000-8000-000000000012', 'directoryName', '../../examples/telecom/output/sms'),
('ce1e0000-0000-4000-8000-000000000012', 'fileName', 'sms.csv'),
('ce1e0000-0000-4000-8000-000000000012', 'charset', 'UTF-8'),
('ce1e0000-0000-4000-8000-000000000012', 'move', '.processed'),
('ce1e0000-0000-4000-8000-000000000012', 'moveFailed', '.invalid'),
('ce1e0000-0000-4000-8000-000000000012', 'autoCreate', 'true'),
('ce1e0000-0000-4000-8000-000000000012', 'recursive', 'false'),
('ce1e0000-0000-4000-8000-000000000012', 'readLock', 'changed'),
('ce1e0000-0000-4000-8000-000000000012', 'delay', '1000'),
('ce1e0000-0000-4000-8000-000000000012', 'initialDelay', '1000'),
('ce1e0000-0000-4000-8000-000000000012', 'readLockTimeout', '10000'),
('ce1e0000-0000-4000-8000-000000000012', 'readLockCheckInterval', '1000'),
('ce1e0000-0000-4000-8000-000000000012', 'readLockMinAge', '0'),
('ce1e0000-0000-4000-8000-000000000012', 'readLockMinLength', '1'),
('ce1e0000-0000-4000-8000-000000000012', 'maxMessagesPerPoll', '1');

INSERT INTO etl_connections (id, name, type, password, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000013', 'Telecom - rejected files', 'file', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_connection_settings (connection_id, setting_key, setting_value) VALUES
('ce1e0000-0000-4000-8000-000000000013', 'directoryName', '../../examples/telecom/output/rejected'),
('ce1e0000-0000-4000-8000-000000000013', 'fileName', 'rejected.csv'),
('ce1e0000-0000-4000-8000-000000000013', 'charset', 'UTF-8'),
('ce1e0000-0000-4000-8000-000000000013', 'move', '.processed'),
('ce1e0000-0000-4000-8000-000000000013', 'moveFailed', '.invalid'),
('ce1e0000-0000-4000-8000-000000000013', 'autoCreate', 'true'),
('ce1e0000-0000-4000-8000-000000000013', 'recursive', 'false'),
('ce1e0000-0000-4000-8000-000000000013', 'readLock', 'changed'),
('ce1e0000-0000-4000-8000-000000000013', 'delay', '1000'),
('ce1e0000-0000-4000-8000-000000000013', 'initialDelay', '1000'),
('ce1e0000-0000-4000-8000-000000000013', 'readLockTimeout', '10000'),
('ce1e0000-0000-4000-8000-000000000013', 'readLockCheckInterval', '1000'),
('ce1e0000-0000-4000-8000-000000000013', 'readLockMinAge', '0'),
('ce1e0000-0000-4000-8000-000000000013', 'readLockMinLength', '1'),
('ce1e0000-0000-4000-8000-000000000013', 'maxMessagesPerPoll', '1');

INSERT INTO etl_mappers (id, name, description, directory, wasm_file_name, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000020', 'Telecom - parse usage CSV', 'Design contract: parse UTF-8 CSV with headers; empty values become null; cast numeric and datetime fields; generate id; preserve calling/called; initialize normalized fields to null. WASM execution is not implemented.', '../../examples/telecom/wasm', 'parse-usage.wasm', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_mapper_outputs (mapper_id, output_order, list_name, field_definition_id, fields_json) VALUES
('ce1e0000-0000-4000-8000-000000000020', '0', 'usage', 'ce1e0000-0000-4000-8000-000000000001', '{"id":"uuid","datetime":"datetime","record_id":"string","record_type":"string","calling":"string","called":"string","normalizedcalling":"string","normalizedcalled":"string","start_time":"datetime","end_time":"datetime","duration_seconds":"integer","sms_count":"integer","direction":"string","status":"string","charge_amount":"decimal","currency":"string","imsi":"string","imei":"string","cell_id":"string","operator_code":"string"}');

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000030', 'Telecom - calling - 1 Trim', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'calling', 'normalizedcalling', 'LENGTH_GREATER_THAN', '0', 'TRIM', '', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000031', 'Telecom - calling - 2 Remove spaces', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalling', 'normalizedcalling', 'CONTAINS', ' ', 'REPLACE', ' ', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000032', 'Telecom - calling - 3 Remove hyphens', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalling', 'normalizedcalling', 'CONTAINS', '-', 'REPLACE', '-', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000033', 'Telecom - calling - 4 Remove 00', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalling', 'normalizedcalling', 'STARTS_WITH', '00', 'REMOVE_PREFIX', '00', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000034', 'Telecom - calling - 5 Local mobile prefix', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalling', 'normalizedcalling', 'LENGTH_EQUALS', '8', 'ADD_PREFIX', '+961', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000035', 'Telecom - calling - 6 International prefix', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalling', 'normalizedcalling', 'STARTS_WITH', '961', 'ADD_PREFIX', '+', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000040', 'Telecom - called - 1 Trim', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'called', 'normalizedcalled', 'LENGTH_GREATER_THAN', '0', 'TRIM', '', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000041', 'Telecom - called - 2 Remove spaces', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalled', 'normalizedcalled', 'CONTAINS', ' ', 'REPLACE', ' ', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000042', 'Telecom - called - 3 Remove hyphens', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalled', 'normalizedcalled', 'CONTAINS', '-', 'REPLACE', '-', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000043', 'Telecom - called - 4 Remove 00', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalled', 'normalizedcalled', 'STARTS_WITH', '00', 'REMOVE_PREFIX', '00', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000044', 'Telecom - called - 5 Local mobile prefix', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalled', 'normalizedcalled', 'LENGTH_EQUALS', '8', 'ADD_PREFIX', '+961', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_normalize_rules (id, name, description, field_definition_id, source_field, destination_field, condition_type, condition_value, action_type, action_value, replacement_value, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000045', 'Telecom - called - 6 International prefix', 'Lebanon demo: preserve original number; apply in numbered order. Local format is an eight-digit mobile number without a trunk zero.', 'ce1e0000-0000-4000-8000-000000000001', 'normalizedcalled', 'normalizedcalled', 'STARTS_WITH', '961', 'ADD_PREFIX', '+', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_conditional_rules (id, name, description, field_definition_id, expression_json, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000050', 'Telecom - match valid CDR', 'Demo routing predicate: type, normalized country prefixes, nonnegative charge and valid usage metric. This is not full phone-number validation.', 'ce1e0000-0000-4000-8000-000000000001', '{"type":"GROUP","junction":"AND","children":[{"type":"COMPARISON","field":"record_type","operator":"EQUALS","operandType":"VALUE","value":"CDR"},{"type":"COMPARISON","field":"normalizedcalling","operator":"STARTS_WITH","operandType":"VALUE","value":"+961"},{"type":"COMPARISON","field":"normalizedcalled","operator":"STARTS_WITH","operandType":"VALUE","value":"+961"},{"type":"COMPARISON","field":"duration_seconds","operator":"GREATER_OR_EQUAL","operandType":"VALUE","value":0},{"type":"COMPARISON","field":"charge_amount","operator":"GREATER_OR_EQUAL","operandType":"VALUE","value":0}]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000050', 'record_type', 'string');

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000050', 'normalizedcalling', 'string');

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000050', 'normalizedcalled', 'string');

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000050', 'duration_seconds', 'integer');

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000050', 'charge_amount', 'decimal');

INSERT INTO etl_conditional_rules (id, name, description, field_definition_id, expression_json, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000051', 'Telecom - match valid SMS', 'Demo routing predicate: type, normalized country prefixes, nonnegative charge and valid usage metric. This is not full phone-number validation.', 'ce1e0000-0000-4000-8000-000000000001', '{"type":"GROUP","junction":"AND","children":[{"type":"COMPARISON","field":"record_type","operator":"EQUALS","operandType":"VALUE","value":"SMS"},{"type":"COMPARISON","field":"normalizedcalling","operator":"STARTS_WITH","operandType":"VALUE","value":"+961"},{"type":"COMPARISON","field":"normalizedcalled","operator":"STARTS_WITH","operandType":"VALUE","value":"+961"},{"type":"COMPARISON","field":"sms_count","operator":"GREATER_THAN","operandType":"VALUE","value":0},{"type":"COMPARISON","field":"charge_amount","operator":"GREATER_OR_EQUAL","operandType":"VALUE","value":0}]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000051', 'record_type', 'string');

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000051', 'normalizedcalling', 'string');

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000051', 'normalizedcalled', 'string');

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000051', 'sms_count', 'integer');

INSERT INTO etl_conditional_rule_fields (rule_id, field_key, field_type) VALUES
('ce1e0000-0000-4000-8000-000000000051', 'charge_amount', 'decimal');

INSERT INTO etl_transformers (id, name, description, directory, wasm_file_name, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000060', 'Telecom - project CDR', 'Design contract: project matching usage records onto the CDR output schema, preserving normalized numbers, typed values and identifiers. Drop fields absent from the output schema. WASM execution is not implemented.', '../../examples/telecom/wasm', 'project-cdr.wasm', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_transformer_inputs (transformer_id, port_order, port_name, port_kind, field_definition_id, fields_json) VALUES
('ce1e0000-0000-4000-8000-000000000060', '0', 'usage', 'FIELD_LIST', 'ce1e0000-0000-4000-8000-000000000001', '{"id":"uuid","datetime":"datetime","record_id":"string","record_type":"string","calling":"string","called":"string","normalizedcalling":"string","normalizedcalled":"string","start_time":"datetime","end_time":"datetime","duration_seconds":"integer","sms_count":"integer","direction":"string","status":"string","charge_amount":"decimal","currency":"string","imsi":"string","imei":"string","cell_id":"string","operator_code":"string"}');

INSERT INTO etl_transformer_outputs (transformer_id, port_order, port_name, port_kind, field_definition_id, fields_json) VALUES
('ce1e0000-0000-4000-8000-000000000060', '0', 'cdr', 'FIELD_LIST', 'ce1e0000-0000-4000-8000-000000000002', '{"id":"uuid","datetime":"datetime","record_id":"string","calling":"string","called":"string","normalizedcalling":"string","normalizedcalled":"string","start_time":"datetime","end_time":"datetime","duration_seconds":"integer","direction":"string","status":"string","charge_amount":"decimal","currency":"string","imsi":"string","imei":"string","cell_id":"string","operator_code":"string"}');

INSERT INTO etl_transformers (id, name, description, directory, wasm_file_name, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000061', 'Telecom - project SMS', 'Design contract: project matching usage records onto the SMS output schema, preserving normalized numbers, typed values and identifiers. Drop fields absent from the output schema. WASM execution is not implemented.', '../../examples/telecom/wasm', 'project-sms.wasm', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO etl_transformer_inputs (transformer_id, port_order, port_name, port_kind, field_definition_id, fields_json) VALUES
('ce1e0000-0000-4000-8000-000000000061', '0', 'usage', 'FIELD_LIST', 'ce1e0000-0000-4000-8000-000000000001', '{"id":"uuid","datetime":"datetime","record_id":"string","record_type":"string","calling":"string","called":"string","normalizedcalling":"string","normalizedcalled":"string","start_time":"datetime","end_time":"datetime","duration_seconds":"integer","sms_count":"integer","direction":"string","status":"string","charge_amount":"decimal","currency":"string","imsi":"string","imei":"string","cell_id":"string","operator_code":"string"}');

INSERT INTO etl_transformer_outputs (transformer_id, port_order, port_name, port_kind, field_definition_id, fields_json) VALUES
('ce1e0000-0000-4000-8000-000000000061', '0', 'sms', 'FIELD_LIST', 'ce1e0000-0000-4000-8000-000000000003', '{"id":"uuid","datetime":"datetime","record_id":"string","calling":"string","called":"string","normalizedcalling":"string","normalizedcalled":"string","start_time":"datetime","sms_count":"integer","direction":"string","status":"string","charge_amount":"decimal","currency":"string","imsi":"string","imei":"string","cell_id":"string","operator_code":"string"}');

INSERT INTO etl_workflows (id, name, description, nodes_json, edges_json, viewport_json, created_at, updated_at) VALUES
('ce1e0000-0000-4000-8000-000000000070', 'Telecom - CSV to CDR and SMS', 'Design demo: file -> CSV mapper -> calling/called normalization -> CDR/SMS matching -> schema projection -> separate files. Unmatched records go to rejected. Saved configuration only; mapper/transformer execution is not implemented.', '[
  {
    "id": "source",
    "type": "dataSource",
    "position": {
      "x": 0,
      "y": 0
    },
    "data": {
      "label": "Telecom - input CSV",
      "connectorId": "ce1e0000-0000-4000-8000-000000000010",
      "connectorName": "Telecom - input CSV",
      "connectorType": "file"
    },
    "deletable": false
  },
  {
    "id": "parse",
    "type": "mapper",
    "position": {
      "x": 300,
      "y": 0
    },
    "data": {
      "label": "Parse usage CSV",
      "mapperId": "ce1e0000-0000-4000-8000-000000000020",
      "mapperName": "Telecom - parse usage CSV",
      "outputs": [
        {
          "name": "usage",
          "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001",
          "fields": {
            "id": "uuid",
            "datetime": "datetime",
            "record_id": "string",
            "record_type": "string",
            "calling": "string",
            "called": "string",
            "normalizedcalling": "string",
            "normalizedcalled": "string",
            "start_time": "datetime",
            "end_time": "datetime",
            "duration_seconds": "integer",
            "sms_count": "integer",
            "direction": "string",
            "status": "string",
            "charge_amount": "decimal",
            "currency": "string",
            "imsi": "string",
            "imei": "string",
            "cell_id": "string",
            "operator_code": "string"
          }
        }
      ]
    }
  },
  {
    "id": "calling-1",
    "type": "normalizeRule",
    "position": {
      "x": 600,
      "y": 0
    },
    "data": {
      "label": "calling: Trim",
      "ruleId": "ce1e0000-0000-4000-8000-000000000030",
      "ruleName": "Telecom - calling - 1 Trim",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "calling-2",
    "type": "normalizeRule",
    "position": {
      "x": 900,
      "y": 0
    },
    "data": {
      "label": "calling: Remove spaces",
      "ruleId": "ce1e0000-0000-4000-8000-000000000031",
      "ruleName": "Telecom - calling - 2 Remove spaces",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "calling-3",
    "type": "normalizeRule",
    "position": {
      "x": 1200,
      "y": 0
    },
    "data": {
      "label": "calling: Remove hyphens",
      "ruleId": "ce1e0000-0000-4000-8000-000000000032",
      "ruleName": "Telecom - calling - 3 Remove hyphens",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "calling-4",
    "type": "normalizeRule",
    "position": {
      "x": 1500,
      "y": 0
    },
    "data": {
      "label": "calling: Remove 00",
      "ruleId": "ce1e0000-0000-4000-8000-000000000033",
      "ruleName": "Telecom - calling - 4 Remove 00",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "calling-5",
    "type": "normalizeRule",
    "position": {
      "x": 1800,
      "y": 0
    },
    "data": {
      "label": "calling: Local mobile prefix",
      "ruleId": "ce1e0000-0000-4000-8000-000000000034",
      "ruleName": "Telecom - calling - 5 Local mobile prefix",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "calling-6",
    "type": "normalizeRule",
    "position": {
      "x": 2100,
      "y": 0
    },
    "data": {
      "label": "calling: International prefix",
      "ruleId": "ce1e0000-0000-4000-8000-000000000035",
      "ruleName": "Telecom - calling - 6 International prefix",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "called-1",
    "type": "normalizeRule",
    "position": {
      "x": 600,
      "y": 260
    },
    "data": {
      "label": "called: Trim",
      "ruleId": "ce1e0000-0000-4000-8000-000000000040",
      "ruleName": "Telecom - called - 1 Trim",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "called-2",
    "type": "normalizeRule",
    "position": {
      "x": 900,
      "y": 260
    },
    "data": {
      "label": "called: Remove spaces",
      "ruleId": "ce1e0000-0000-4000-8000-000000000041",
      "ruleName": "Telecom - called - 2 Remove spaces",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "called-3",
    "type": "normalizeRule",
    "position": {
      "x": 1200,
      "y": 260
    },
    "data": {
      "label": "called: Remove hyphens",
      "ruleId": "ce1e0000-0000-4000-8000-000000000042",
      "ruleName": "Telecom - called - 3 Remove hyphens",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "called-4",
    "type": "normalizeRule",
    "position": {
      "x": 1500,
      "y": 260
    },
    "data": {
      "label": "called: Remove 00",
      "ruleId": "ce1e0000-0000-4000-8000-000000000043",
      "ruleName": "Telecom - called - 4 Remove 00",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "called-5",
    "type": "normalizeRule",
    "position": {
      "x": 1800,
      "y": 260
    },
    "data": {
      "label": "called: Local mobile prefix",
      "ruleId": "ce1e0000-0000-4000-8000-000000000044",
      "ruleName": "Telecom - called - 5 Local mobile prefix",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "called-6",
    "type": "normalizeRule",
    "position": {
      "x": 2100,
      "y": 260
    },
    "data": {
      "label": "called: International prefix",
      "ruleId": "ce1e0000-0000-4000-8000-000000000045",
      "ruleName": "Telecom - called - 6 International prefix",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "match-cdr",
    "type": "conditionalRule",
    "position": {
      "x": 600,
      "y": 520
    },
    "data": {
      "label": "Match valid CDR",
      "ruleId": "ce1e0000-0000-4000-8000-000000000050",
      "ruleName": "Telecom - match valid CDR",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "match-sms",
    "type": "conditionalRule",
    "position": {
      "x": 600,
      "y": 820
    },
    "data": {
      "label": "Match valid SMS",
      "ruleId": "ce1e0000-0000-4000-8000-000000000051",
      "ruleName": "Telecom - match valid SMS",
      "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001"
    }
  },
  {
    "id": "project-cdr",
    "type": "transformer",
    "position": {
      "x": 1000,
      "y": 520
    },
    "data": {
      "label": "Project CDR fields",
      "transformerId": "ce1e0000-0000-4000-8000-000000000060",
      "transformerName": "Telecom - project CDR",
      "inputs": [
        {
          "name": "usage",
          "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001",
          "fields": {
            "id": "uuid",
            "datetime": "datetime",
            "record_id": "string",
            "record_type": "string",
            "calling": "string",
            "called": "string",
            "normalizedcalling": "string",
            "normalizedcalled": "string",
            "start_time": "datetime",
            "end_time": "datetime",
            "duration_seconds": "integer",
            "sms_count": "integer",
            "direction": "string",
            "status": "string",
            "charge_amount": "decimal",
            "currency": "string",
            "imsi": "string",
            "imei": "string",
            "cell_id": "string",
            "operator_code": "string"
          },
          "kind": "FIELD_LIST"
        }
      ],
      "outputs": [
        {
          "name": "cdr",
          "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000002",
          "fields": {
            "id": "uuid",
            "datetime": "datetime",
            "record_id": "string",
            "calling": "string",
            "called": "string",
            "normalizedcalling": "string",
            "normalizedcalled": "string",
            "start_time": "datetime",
            "end_time": "datetime",
            "duration_seconds": "integer",
            "direction": "string",
            "status": "string",
            "charge_amount": "decimal",
            "currency": "string",
            "imsi": "string",
            "imei": "string",
            "cell_id": "string",
            "operator_code": "string"
          },
          "kind": "FIELD_LIST"
        }
      ]
    }
  },
  {
    "id": "cdr-file",
    "type": "dataDestination",
    "position": {
      "x": 1400,
      "y": 520
    },
    "data": {
      "label": "Telecom - CDR files",
      "connectorId": "ce1e0000-0000-4000-8000-000000000011",
      "connectorName": "Telecom - CDR files",
      "connectorType": "file"
    }
  },
  {
    "id": "project-sms",
    "type": "transformer",
    "position": {
      "x": 1000,
      "y": 820
    },
    "data": {
      "label": "Project SMS fields",
      "transformerId": "ce1e0000-0000-4000-8000-000000000061",
      "transformerName": "Telecom - project SMS",
      "inputs": [
        {
          "name": "usage",
          "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000001",
          "fields": {
            "id": "uuid",
            "datetime": "datetime",
            "record_id": "string",
            "record_type": "string",
            "calling": "string",
            "called": "string",
            "normalizedcalling": "string",
            "normalizedcalled": "string",
            "start_time": "datetime",
            "end_time": "datetime",
            "duration_seconds": "integer",
            "sms_count": "integer",
            "direction": "string",
            "status": "string",
            "charge_amount": "decimal",
            "currency": "string",
            "imsi": "string",
            "imei": "string",
            "cell_id": "string",
            "operator_code": "string"
          },
          "kind": "FIELD_LIST"
        }
      ],
      "outputs": [
        {
          "name": "sms",
          "fieldDefinitionId": "ce1e0000-0000-4000-8000-000000000003",
          "fields": {
            "id": "uuid",
            "datetime": "datetime",
            "record_id": "string",
            "calling": "string",
            "called": "string",
            "normalizedcalling": "string",
            "normalizedcalled": "string",
            "start_time": "datetime",
            "sms_count": "integer",
            "direction": "string",
            "status": "string",
            "charge_amount": "decimal",
            "currency": "string",
            "imsi": "string",
            "imei": "string",
            "cell_id": "string",
            "operator_code": "string"
          },
          "kind": "FIELD_LIST"
        }
      ]
    }
  },
  {
    "id": "sms-file",
    "type": "dataDestination",
    "position": {
      "x": 1400,
      "y": 820
    },
    "data": {
      "label": "Telecom - SMS files",
      "connectorId": "ce1e0000-0000-4000-8000-000000000012",
      "connectorName": "Telecom - SMS files",
      "connectorType": "file"
    }
  },
  {
    "id": "rejected-file",
    "type": "dataDestination",
    "position": {
      "x": 1000,
      "y": 1120
    },
    "data": {
      "label": "Telecom - rejected files",
      "connectorId": "ce1e0000-0000-4000-8000-000000000013",
      "connectorName": "Telecom - rejected files",
      "connectorType": "file"
    }
  }
]', '[
  {
    "id": "e1",
    "source": "source",
    "target": "parse",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e2",
    "source": "parse",
    "target": "calling-1",
    "sourceHandle": "list:usage",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e3",
    "source": "calling-1",
    "target": "calling-2",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e4",
    "source": "calling-2",
    "target": "calling-3",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e5",
    "source": "calling-3",
    "target": "calling-4",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e6",
    "source": "calling-4",
    "target": "calling-5",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e7",
    "source": "calling-5",
    "target": "calling-6",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e8",
    "source": "calling-6",
    "target": "called-1",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e9",
    "source": "called-1",
    "target": "called-2",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e10",
    "source": "called-2",
    "target": "called-3",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e11",
    "source": "called-3",
    "target": "called-4",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e12",
    "source": "called-4",
    "target": "called-5",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e13",
    "source": "called-5",
    "target": "called-6",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e14",
    "source": "called-6",
    "target": "match-cdr",
    "sourceHandle": "out",
    "targetHandle": "in",
    "type": "smoothstep"
  },
  {
    "id": "e15",
    "source": "match-cdr",
    "target": "match-sms",
    "sourceHandle": "not-matched",
    "targetHandle": "in",
    "type": "smoothstep",
    "label": "Not matched"
  },
  {
    "id": "e16",
    "source": "match-cdr",
    "target": "project-cdr",
    "sourceHandle": "matched",
    "targetHandle": "input:usage",
    "type": "smoothstep",
    "label": "Matched"
  },
  {
    "id": "e17",
    "source": "project-cdr",
    "target": "cdr-file",
    "sourceHandle": "output:cdr",
    "targetHandle": "in",
    "type": "smoothstep",
    "label": "cdr"
  },
  {
    "id": "e18",
    "source": "match-sms",
    "target": "project-sms",
    "sourceHandle": "matched",
    "targetHandle": "input:usage",
    "type": "smoothstep",
    "label": "Matched"
  },
  {
    "id": "e19",
    "source": "project-sms",
    "target": "sms-file",
    "sourceHandle": "output:sms",
    "targetHandle": "in",
    "type": "smoothstep",
    "label": "sms"
  },
  {
    "id": "e20",
    "source": "match-sms",
    "target": "rejected-file",
    "sourceHandle": "not-matched",
    "targetHandle": "in",
    "type": "smoothstep",
    "label": "Not matched"
  }
]', '{"x":30,"y":30,"zoom":0.4}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
