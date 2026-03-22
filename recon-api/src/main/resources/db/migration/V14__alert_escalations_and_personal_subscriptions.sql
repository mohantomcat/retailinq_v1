CREATE TABLE IF NOT EXISTS recon.alert_escalation_policies (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    policy_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    metric_key varchar(50),
    severity_threshold varchar(20),
    store_id varchar(50),
    wkstn_id varchar(50),
    escalation_after_minutes integer NOT NULL DEFAULT 60,
    destination_type varchar(30) NOT NULL,
    destination_key text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_escalation_policies_tenant_recon
    ON recon.alert_escalation_policies (tenant_id, recon_view, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_escalation_history (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES recon.alert_events(id) ON DELETE CASCADE,
    policy_id uuid NOT NULL REFERENCES recon.alert_escalation_policies(id) ON DELETE CASCADE,
    tenant_id varchar(100) NOT NULL,
    recon_view varchar(50) NOT NULL,
    rule_name varchar(150) NOT NULL,
    severity varchar(20) NOT NULL,
    destination_type varchar(30) NOT NULL,
    destination_key text NOT NULL,
    escalation_status varchar(20) NOT NULL,
    error_message text,
    escalated_at timestamp NOT NULL DEFAULT now(),
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_escalation_history_policy_event
    ON recon.alert_escalation_history (policy_id, event_id);

CREATE INDEX IF NOT EXISTS idx_alert_escalation_history_tenant_recon
    ON recon.alert_escalation_history (tenant_id, recon_view, escalated_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_user_subscriptions (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    user_id uuid NOT NULL REFERENCES recon.users(id) ON DELETE CASCADE,
    username varchar(100) NOT NULL,
    recon_view varchar(50) NOT NULL,
    metric_key varchar(50),
    severity_threshold varchar(20),
    channel_type varchar(30) NOT NULL,
    endpoint_url text,
    store_id varchar(50),
    wkstn_id varchar(50),
    active boolean NOT NULL DEFAULT true,
    description text,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_user_subscriptions_user_recon
    ON recon.alert_user_subscriptions (tenant_id, user_id, recon_view, active, updated_at DESC);
