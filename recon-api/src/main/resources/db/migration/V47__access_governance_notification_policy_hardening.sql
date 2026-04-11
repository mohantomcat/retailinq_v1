ALTER TABLE IF EXISTS recon.tenant_auth_config
    ADD COLUMN IF NOT EXISTS governance_notification_max_attempts integer NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS governance_notification_backoff_minutes integer NOT NULL DEFAULT 15,
    ADD COLUMN IF NOT EXISTS manager_access_review_escalation_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS manager_access_review_escalation_after_days integer NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS manager_access_review_escalation_email_recipients text,
    ADD COLUMN IF NOT EXISTS manager_access_review_escalation_teams_webhook_url text,
    ADD COLUMN IF NOT EXISTS manager_access_review_escalation_slack_webhook_url text,
    ADD COLUMN IF NOT EXISTS manager_access_review_reminder_subject_template text,
    ADD COLUMN IF NOT EXISTS manager_access_review_reminder_body_template text,
    ADD COLUMN IF NOT EXISTS manager_access_review_escalation_subject_template text,
    ADD COLUMN IF NOT EXISTS manager_access_review_escalation_body_template text,
    ADD COLUMN IF NOT EXISTS privileged_action_alert_subject_template text,
    ADD COLUMN IF NOT EXISTS privileged_action_alert_body_template text;

ALTER TABLE IF EXISTS recon.users
    ADD COLUMN IF NOT EXISTS access_review_reminder_acknowledged_at timestamp,
    ADD COLUMN IF NOT EXISTS access_review_reminder_acknowledged_by varchar(255),
    ADD COLUMN IF NOT EXISTS access_review_reminder_ack_note text,
    ADD COLUMN IF NOT EXISTS access_review_last_escalated_at timestamp;

CREATE TABLE IF NOT EXISTS recon.access_governance_notification_jobs (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    notification_type varchar(100) NOT NULL,
    channel_type varchar(50) NOT NULL,
    target_key text NOT NULL,
    notification_context_key varchar(255),
    reference_user_ids text,
    payload_data text,
    notification_status varchar(50) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 3,
    backoff_minutes integer NOT NULL DEFAULT 15,
    next_attempt_at timestamp NOT NULL,
    last_attempt_at timestamp,
    delivered_at timestamp,
    last_error text,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_access_governance_notification_jobs_due
    ON recon.access_governance_notification_jobs (notification_status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_access_governance_notification_jobs_tenant_due
    ON recon.access_governance_notification_jobs (tenant_id, notification_status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_access_governance_notification_jobs_context
    ON recon.access_governance_notification_jobs (notification_context_key, notification_status);
