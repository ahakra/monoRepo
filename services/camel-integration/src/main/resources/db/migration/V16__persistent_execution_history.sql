CREATE SEQUENCE etl_execution_event_seq START WITH 1;
CREATE TABLE etl_execution_history_metadata (
    id INTEGER PRIMARY KEY,
    history_id UUID NOT NULL
);
CREATE TABLE etl_execution_runs (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    workflow_name VARCHAR(120) NOT NULL,
    owner_id UUID NOT NULL,
    start_sequence BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    successful_files BIGINT NOT NULL DEFAULT 0,
    failed_files BIGINT NOT NULL DEFAULT 0,
    last_error TEXT,
    plan_json TEXT NOT NULL,
    source_configuration_json TEXT
);
CREATE INDEX etl_execution_runs_workflow_idx ON etl_execution_runs(workflow_id, start_sequence);
CREATE TABLE etl_executions (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    run_id UUID NOT NULL REFERENCES etl_execution_runs(id),
    exchange_id VARCHAR(250) NOT NULL,
    start_sequence BIGINT NOT NULL,
    description TEXT NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    last_error TEXT,
    UNIQUE(run_id, exchange_id)
);
CREATE INDEX etl_executions_workflow_idx ON etl_executions(workflow_id, start_sequence);
CREATE TABLE etl_execution_stages (
    execution_id UUID NOT NULL REFERENCES etl_executions(id),
    node_id VARCHAR(120) NOT NULL,
    stage_order INTEGER NOT NULL,
    name VARCHAR(250) NOT NULL,
    type VARCHAR(40),
    status VARCHAR(30) NOT NULL,
    inputs_json TEXT,
    outputs_json TEXT,
    matched_count BIGINT,
    total_count BIGINT,
    byte_count BIGINT,
    record_count BIGINT,
    duration_ms BIGINT,
    chain_duration_ms BIGINT,
    output_path TEXT,
    message TEXT,
    error TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(execution_id, node_id)
);
CREATE TABLE etl_execution_events (
    sequence BIGINT PRIMARY KEY,
    workflow_id UUID NOT NULL,
    run_id UUID NOT NULL REFERENCES etl_execution_runs(id),
    execution_id UUID REFERENCES etl_executions(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    type VARCHAR(50) NOT NULL,
    level VARCHAR(10) NOT NULL,
    event_json TEXT NOT NULL
);
CREATE INDEX etl_execution_events_workflow_idx ON etl_execution_events(workflow_id, sequence);
CREATE INDEX etl_execution_events_batch_idx ON etl_execution_events(execution_id, sequence);
