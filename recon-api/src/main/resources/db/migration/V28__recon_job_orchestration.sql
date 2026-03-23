CREATE TABLE IF NOT EXISTS recon.recon_job_definition (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    job_name varchar(160) NOT NULL,
    recon_view varchar(40) NOT NULL,
    cron_expression varchar(120) NOT NULL,
    job_timezone varchar(80) NOT NULL DEFAULT 'UTC',
    window_type varchar(30) NOT NULL DEFAULT 'CONTINUOUS',
    end_of_day_local_time varchar(8),
    business_date_offset_days integer NOT NULL DEFAULT 0,
    max_retry_attempts integer NOT NULL DEFAULT 0,
    retry_delay_minutes integer NOT NULL DEFAULT 15,
    allow_concurrent_runs boolean NOT NULL DEFAULT false,
    enabled boolean NOT NULL DEFAULT true,
    scope_store_ids jsonb,
    notification_channel_type varchar(40),
    notification_endpoint text,
    notification_email varchar(255),
    notify_on_success boolean NOT NULL DEFAULT false,
    notify_on_failure boolean NOT NULL DEFAULT true,
    last_scheduled_at timestamp,
    next_scheduled_at timestamp,
    last_run_started_at timestamp,
    last_run_completed_at timestamp,
    last_run_status varchar(30),
    last_run_message text,
    created_by varchar(100),
    updated_by varchar(100),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_recon_job_definition_tenant_name
    ON recon.recon_job_definition (tenant_id, lower(job_name));

CREATE INDEX IF NOT EXISTS idx_recon_job_definition_tenant_enabled
    ON recon.recon_job_definition (tenant_id, enabled, next_scheduled_at);

CREATE TABLE IF NOT EXISTS recon.recon_job_step_definition (
    id uuid PRIMARY KEY,
    job_definition_id uuid NOT NULL REFERENCES recon.recon_job_definition(id) ON DELETE CASCADE,
    step_order integer NOT NULL,
    step_label varchar(160) NOT NULL,
    step_type varchar(40) NOT NULL,
    module_id varchar(80),
    action_key varchar(80),
    depends_on_step_id uuid REFERENCES recon.recon_job_step_definition(id) ON DELETE SET NULL,
    settle_delay_seconds integer,
    step_config jsonb,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_recon_job_step_definition_order
    ON recon.recon_job_step_definition (job_definition_id, step_order);

CREATE INDEX IF NOT EXISTS idx_recon_job_step_definition_job
    ON recon.recon_job_step_definition (job_definition_id, step_order);

CREATE TABLE IF NOT EXISTS recon.recon_job_run (
    id uuid PRIMARY KEY,
    job_definition_id uuid NOT NULL REFERENCES recon.recon_job_definition(id) ON DELETE CASCADE,
    tenant_id varchar(100) NOT NULL,
    job_name varchar(160) NOT NULL,
    recon_view varchar(40) NOT NULL,
    trigger_type varchar(30) NOT NULL,
    run_status varchar(30) NOT NULL,
    initiated_by varchar(100),
    parent_run_id uuid REFERENCES recon.recon_job_run(id) ON DELETE SET NULL,
    root_run_id uuid REFERENCES recon.recon_job_run(id) ON DELETE SET NULL,
    attempt_number integer NOT NULL DEFAULT 1,
    max_retry_attempts integer NOT NULL DEFAULT 0,
    retry_delay_minutes integer NOT NULL DEFAULT 15,
    retry_pending boolean NOT NULL DEFAULT false,
    scheduled_for timestamp,
    started_at timestamp,
    completed_at timestamp,
    business_date varchar(20),
    window_from_business_date varchar(20),
    window_to_business_date varchar(20),
    summary text,
    result_payload jsonb,
    next_retry_at timestamp,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recon_job_run_tenant_created
    ON recon.recon_job_run (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recon_job_run_definition_status
    ON recon.recon_job_run (job_definition_id, run_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recon_job_run_retry_pending
    ON recon.recon_job_run (retry_pending, next_retry_at);

CREATE TABLE IF NOT EXISTS recon.recon_job_step_run (
    id uuid PRIMARY KEY,
    job_run_id uuid NOT NULL REFERENCES recon.recon_job_run(id) ON DELETE CASCADE,
    step_definition_id uuid REFERENCES recon.recon_job_step_definition(id) ON DELETE SET NULL,
    tenant_id varchar(100) NOT NULL,
    step_order integer NOT NULL,
    step_label varchar(160) NOT NULL,
    step_type varchar(40) NOT NULL,
    module_id varchar(80),
    action_key varchar(80),
    run_status varchar(30) NOT NULL,
    started_at timestamp,
    completed_at timestamp,
    duration_ms bigint,
    message text,
    request_payload jsonb,
    response_payload jsonb,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recon_job_step_run_job
    ON recon.recon_job_step_run (job_run_id, step_order);

CREATE TABLE IF NOT EXISTS recon.recon_job_retry_event (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    job_definition_id uuid NOT NULL REFERENCES recon.recon_job_definition(id) ON DELETE CASCADE,
    failed_run_id uuid NOT NULL REFERENCES recon.recon_job_run(id) ON DELETE CASCADE,
    retry_run_id uuid REFERENCES recon.recon_job_run(id) ON DELETE SET NULL,
    attempt_number integer NOT NULL,
    scheduled_for timestamp NOT NULL,
    started_at timestamp,
    completed_at timestamp,
    retry_status varchar(30) NOT NULL DEFAULT 'PENDING',
    error_message text,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recon_job_retry_event_due
    ON recon.recon_job_retry_event (retry_status, scheduled_for);

CREATE TABLE IF NOT EXISTS recon.recon_job_notification_delivery (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    job_run_id uuid NOT NULL REFERENCES recon.recon_job_run(id) ON DELETE CASCADE,
    channel_type varchar(40) NOT NULL,
    destination text,
    delivery_status varchar(30) NOT NULL,
    response_code integer,
    error_message text,
    payload_json jsonb,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recon_job_notification_delivery_run
    ON recon.recon_job_notification_delivery (job_run_id, created_at DESC);
