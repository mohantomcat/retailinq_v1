CREATE TABLE IF NOT EXISTS recon.alert_email_subscriptions (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    subscription_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    metric_key varchar(50),
    severity_threshold varchar(20),
    recipient_type varchar(20) NOT NULL,
    recipient_key varchar(200) NOT NULL,
    store_id varchar(50),
    wkstn_id varchar(50),
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_email_subscriptions_tenant_recon
    ON recon.alert_email_subscriptions (tenant_id, recon_view, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_email_deliveries (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES recon.alert_events(id) ON DELETE CASCADE,
    subscription_id uuid REFERENCES recon.alert_email_subscriptions(id) ON DELETE SET NULL,
    tenant_id varchar(100) NOT NULL,
    recon_view varchar(50) NOT NULL,
    recipient_email varchar(200) NOT NULL,
    delivery_status varchar(20) NOT NULL,
    email_subject varchar(255) NOT NULL,
    error_message text,
    created_at timestamp NOT NULL DEFAULT now(),
    last_attempt_at timestamp NOT NULL DEFAULT now(),
    delivered_at timestamp
);

CREATE INDEX IF NOT EXISTS idx_alert_email_deliveries_tenant_status
    ON recon.alert_email_deliveries (tenant_id, delivery_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_email_deliveries_event
    ON recon.alert_email_deliveries (event_id, created_at DESC);
