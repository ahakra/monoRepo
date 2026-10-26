CREATE TABLE etl_connections (
 id UUID PRIMARY KEY,
 name VARCHAR(120) NOT NULL,
 type VARCHAR(40) NOT NULL,
 password TEXT NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE etl_connection_settings (
 connection_id UUID NOT NULL REFERENCES etl_connections(id) ON DELETE CASCADE,
 setting_key VARCHAR(80) NOT NULL,
 setting_value VARCHAR(2048) NOT NULL,
 PRIMARY KEY(connection_id, setting_key)
);
INSERT INTO etl_connections (id, name, type, password, updated_at)
SELECT id, name, 'postgres', password, updated_at FROM integration_connections;
INSERT INTO etl_connection_settings SELECT id, 'host', host FROM integration_connections;
INSERT INTO etl_connection_settings SELECT id, 'port', CAST(port AS VARCHAR) FROM integration_connections;
INSERT INTO etl_connection_settings SELECT id, 'database', database_name FROM integration_connections;
INSERT INTO etl_connection_settings SELECT id, 'username', username FROM integration_connections;
INSERT INTO etl_connection_settings SELECT id, 'sslMode', ssl_mode FROM integration_connections;
INSERT INTO etl_connection_settings SELECT id, 'createDatabaseIfNotExists', 'false' FROM integration_connections;
