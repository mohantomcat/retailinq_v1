ALTER TABLE recon.alert_events
    ALTER COLUMN rule_id DROP NOT NULL;

ALTER TABLE recon.alert_events
    ADD COLUMN IF NOT EXISTS anomaly_rule_id uuid,
    ADD COLUMN IF NOT EXISTS detection_type varchar(20) NOT NULL DEFAULT 'THRESHOLD',
    ADD COLUMN IF NOT EXISTS anomaly_direction varchar(20),
    ADD COLUMN IF NOT EXISTS baseline_value numeric(18,2),
    ADD COLUMN IF NOT EXISTS delta_percentage numeric(9,2);

CREATE TABLE IF NOT EXISTS recon.alert_anomaly_rules (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    rule_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    metric_key varchar(50) NOT NULL,
    anomaly_type varchar(20) NOT NULL,
    percent_change_threshold numeric(9,2) NOT NULL DEFAULT 30,
    min_baseline_value numeric(18,2) NOT NULL DEFAULT 1,
    lookback_days integer NOT NULL DEFAULT 7,
    cooldown_minutes integer NOT NULL DEFAULT 180,
    severity varchar(20) NOT NULL DEFAULT 'HIGH',
    store_id varchar(50),
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_anomaly_rules_tenant_recon
    ON recon.alert_anomaly_rules (tenant_id, recon_view, active, updated_at DESC);

ALTER TABLE recon.alert_events
    ADD CONSTRAINT fk_alert_events_anomaly_rule
    FOREIGN KEY (anomaly_rule_id) REFERENCES recon.alert_anomaly_rules(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_alert_events_anomaly_scope
    ON recon.alert_events (anomaly_rule_id, scope_key, alert_status, last_triggered_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_digest_subscriptions (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    digest_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    scope_type varchar(20) NOT NULL,
    scope_key varchar(100),
    severity_threshold varchar(20),
    recipient_type varchar(20) NOT NULL,
    recipient_key varchar(200) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_digest_subscriptions_tenant_recon
    ON recon.alert_digest_subscriptions (tenant_id, recon_view, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_digest_runs (
    id uuid PRIMARY KEY,
    subscription_id uuid NOT NULL REFERENCES recon.alert_digest_subscriptions(id) ON DELETE CASCADE,
    tenant_id varchar(100) NOT NULL,
    recon_view varchar(50) NOT NULL,
    scope_type varchar(20) NOT NULL,
    scope_key varchar(100),
    recipient_summary text,
    run_status varchar(20) NOT NULL,
    item_count integer NOT NULL DEFAULT 0,
    digest_subject varchar(200) NOT NULL,
    error_message text,
    created_at timestamp NOT NULL DEFAULT now(),
    delivered_at timestamp
);

CREATE INDEX IF NOT EXISTS idx_alert_digest_runs_tenant_recon
    ON recon.alert_digest_runs (tenant_id, recon_view, created_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_sms_subscriptions (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    subscription_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    metric_key varchar(50),
    severity_threshold varchar(20),
    phone_number varchar(40) NOT NULL,
    store_id varchar(50),
    wkstn_id varchar(50),
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_sms_subscriptions_tenant_recon
    ON recon.alert_sms_subscriptions (tenant_id, recon_view, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_sms_deliveries (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES recon.alert_events(id) ON DELETE CASCADE,
    subscription_id uuid REFERENCES recon.alert_sms_subscriptions(id) ON DELETE SET NULL,
    tenant_id varchar(100) NOT NULL,
    recon_view varchar(50) NOT NULL,
    phone_number varchar(40) NOT NULL,
    provider_name varchar(60) NOT NULL,
    delivery_status varchar(20) NOT NULL,
    response_status_code integer,
    error_message text,
    created_at timestamp NOT NULL DEFAULT now(),
    last_attempt_at timestamp NOT NULL DEFAULT now(),
    delivered_at timestamp
);

CREATE INDEX IF NOT EXISTS idx_alert_sms_deliveries_tenant_recon
    ON recon.alert_sms_deliveries (tenant_id, recon_view, created_at DESC);
