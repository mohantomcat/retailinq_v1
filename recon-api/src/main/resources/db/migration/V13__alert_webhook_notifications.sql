CREATE TABLE IF NOT EXISTS recon.alert_webhook_subscriptions (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    subscription_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    metric_key varchar(50),
    severity_threshold varchar(20),
    channel_type varchar(30) NOT NULL,
    endpoint_url text NOT NULL,
    store_id varchar(50),
    wkstn_id varchar(50),
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_webhook_subscriptions_tenant_recon
    ON recon.alert_webhook_subscriptions (tenant_id, recon_view, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_webhook_deliveries (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES recon.alert_events(id) ON DELETE CASCADE,
    subscription_id uuid REFERENCES recon.alert_webhook_subscriptions(id) ON DELETE SET NULL,
    tenant_id varchar(100) NOT NULL,
    recon_view varchar(50) NOT NULL,
    channel_type varchar(30) NOT NULL,
    endpoint_url text NOT NULL,
    delivery_status varchar(20) NOT NULL,
    response_status_code integer,
    request_payload text,
    response_body text,
    error_message text,
    created_at timestamp NOT NULL DEFAULT now(),
    last_attempt_at timestamp NOT NULL DEFAULT now(),
    delivered_at timestamp
);

CREATE INDEX IF NOT EXISTS idx_alert_webhook_deliveries_tenant_status
    ON recon.alert_webhook_deliveries (tenant_id, delivery_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_webhook_deliveries_event
    ON recon.alert_webhook_deliveries (event_id, created_at DESC);
