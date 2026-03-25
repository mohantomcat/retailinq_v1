CREATE TABLE IF NOT EXISTS recon.integration_connector_definition (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    connector_key varchar(120) NOT NULL,
    connector_label varchar(160) NOT NULL,
    connector_type varchar(80) NOT NULL,
    source_system varchar(60) NOT NULL,
    target_system varchar(60),
    module_key varchar(120),
    enabled boolean NOT NULL DEFAULT true,
    runtime_mode varchar(60) NOT NULL DEFAULT 'POLLING',
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_connector_definition UNIQUE (tenant_id, connector_key)
);

CREATE TABLE IF NOT EXISTS recon.integration_flow_definition (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    connector_definition_id uuid REFERENCES recon.integration_connector_definition(id) ON DELETE CASCADE,
    flow_key varchar(120) NOT NULL,
    flow_label varchar(180) NOT NULL,
    message_type varchar(80) NOT NULL,
    source_system varchar(60) NOT NULL,
    target_system varchar(60),
    business_object varchar(80),
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_flow_definition UNIQUE (tenant_id, flow_key)
);

CREATE TABLE IF NOT EXISTS recon.integration_mapping_definition (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    flow_key varchar(120) NOT NULL,
    mapping_name varchar(160) NOT NULL,
    schema_version integer NOT NULL DEFAULT 1,
    mapping_status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    source_schema_key varchar(120),
    target_schema_key varchar(120),
    mapping_rules jsonb,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_mapping_definition UNIQUE (tenant_id, flow_key, mapping_name)
);

CREATE TABLE IF NOT EXISTS recon.integration_run (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    connector_key varchar(120) NOT NULL,
    flow_key varchar(120) NOT NULL,
    source_system varchar(60) NOT NULL,
    target_system varchar(60),
    trigger_type varchar(40) NOT NULL,
    run_status varchar(40) NOT NULL,
    started_at timestamp NOT NULL,
    completed_at timestamp,
    source_record_count integer NOT NULL DEFAULT 0,
    published_record_count integer NOT NULL DEFAULT 0,
    error_count integer NOT NULL DEFAULT 0,
    run_summary varchar(300),
    run_metadata jsonb,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS recon.integration_run_step (
    id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES recon.integration_run(id) ON DELETE CASCADE,
    step_key varchar(80) NOT NULL,
    step_label varchar(180) NOT NULL,
    step_order integer NOT NULL DEFAULT 0,
    step_status varchar(40) NOT NULL,
    started_at timestamp,
    completed_at timestamp,
    processed_count integer NOT NULL DEFAULT 0,
    error_count integer NOT NULL DEFAULT 0,
    detail_message varchar(300),
    step_metadata jsonb,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS recon.integration_message (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    run_id uuid REFERENCES recon.integration_run(id) ON DELETE SET NULL,
    connector_key varchar(120) NOT NULL,
    flow_key varchar(120) NOT NULL,
    message_id varchar(160) NOT NULL,
    trace_id varchar(160),
    business_key varchar(200),
    document_id varchar(160),
    message_type varchar(80) NOT NULL,
    source_system varchar(60) NOT NULL,
    target_system varchar(60),
    message_status varchar(40) NOT NULL,
    retry_count integer NOT NULL DEFAULT 0,
    payload_ref varchar(300),
    payload_snapshot jsonb,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_message UNIQUE (tenant_id, message_id)
);

CREATE TABLE IF NOT EXISTS recon.integration_error_queue (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    run_id uuid REFERENCES recon.integration_run(id) ON DELETE SET NULL,
    integration_message_id uuid REFERENCES recon.integration_message(id) ON DELETE SET NULL,
    connector_key varchar(120) NOT NULL,
    flow_key varchar(120) NOT NULL,
    business_key varchar(200),
    document_id varchar(160),
    error_type varchar(80) NOT NULL,
    error_code varchar(80),
    error_message text NOT NULL,
    retryable boolean NOT NULL DEFAULT true,
    error_status varchar(40) NOT NULL DEFAULT 'OPEN',
    created_at timestamp NOT NULL DEFAULT now(),
    resolved_at timestamp,
    resolution_notes varchar(300)
);

CREATE TABLE IF NOT EXISTS recon.integration_replay_request (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    connector_key varchar(120) NOT NULL,
    flow_key varchar(120) NOT NULL,
    requested_by varchar(120) NOT NULL,
    requested_at timestamp NOT NULL DEFAULT now(),
    replay_status varchar(40) NOT NULL DEFAULT 'REQUESTED',
    business_key varchar(200),
    document_id varchar(160),
    requested_from timestamp,
    requested_to timestamp,
    reason varchar(300),
    request_metadata jsonb,
    processed_at timestamp,
    processed_by varchar(120),
    resolution_message varchar(300)
);

CREATE INDEX IF NOT EXISTS idx_integration_run_tenant_started
    ON recon.integration_run (tenant_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_integration_run_connector_status
    ON recon.integration_run (tenant_id, connector_key, run_status);

CREATE INDEX IF NOT EXISTS idx_integration_message_tenant_created
    ON recon.integration_message (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_integration_message_flow_status
    ON recon.integration_message (tenant_id, flow_key, message_status);

CREATE INDEX IF NOT EXISTS idx_integration_error_queue_tenant_status
    ON recon.integration_error_queue (tenant_id, error_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_integration_replay_request_tenant_status
    ON recon.integration_replay_request (tenant_id, replay_status, requested_at DESC);

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c6001'::uuid, 'INTEGRATION_HUB_VIEW', 'Integration Hub View', 'INTEGRATION_HUB',
     'View internal Integration Hub monitoring, run history, and error queues.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c6002'::uuid, 'INTEGRATION_HUB_REPLAY', 'Integration Hub Replay', 'INTEGRATION_HUB',
     'Create replay and reprocessing requests from the Integration Hub.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('INTEGRATION_HUB_VIEW', 'INTEGRATION_HUB_REPLAY')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
