-- Original creation times were not recorded for legacy connectors; leave them unknown.
ALTER TABLE etl_connections ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
