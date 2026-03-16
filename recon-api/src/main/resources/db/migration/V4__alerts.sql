CREATE TABLE IF NOT EXISTS recon.alert_rules (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    rule_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    metric_key varchar(50) NOT NULL,
    operator varchar(10) NOT NULL,
    threshold_value numeric(18,2) NOT NULL,
    severity varchar(20) NOT NULL DEFAULT 'MEDIUM',
    store_id varchar(50),
    wkstn_id varchar(50),
    lookback_days integer NOT NULL DEFAULT 1,
    cooldown_minutes integer NOT NULL DEFAULT 60,
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_tenant_recon
    ON recon.alert_rules (tenant_id, recon_view, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.alert_events (
    id uuid PRIMARY KEY,
    rule_id uuid NOT NULL REFERENCES recon.alert_rules(id) ON DELETE CASCADE,
    tenant_id varchar(100) NOT NULL,
    rule_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    metric_key varchar(50) NOT NULL,
    severity varchar(20) NOT NULL,
    scope_key varchar(180) NOT NULL,
    store_id varchar(50),
    wkstn_id varchar(50),
    alert_status varchar(20) NOT NULL DEFAULT 'OPEN',
    metric_value numeric(18,2) NOT NULL,
    threshold_value numeric(18,2) NOT NULL,
    event_message text NOT NULL,
    trigger_count integer NOT NULL DEFAULT 1,
    first_triggered_at timestamp NOT NULL DEFAULT now(),
    last_triggered_at timestamp NOT NULL DEFAULT now(),
    acknowledged_by varchar(100),
    acknowledged_at timestamp,
    resolved_by varchar(100),
    resolved_at timestamp,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_events_tenant_status
    ON recon.alert_events (tenant_id, recon_view, alert_status, last_triggered_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_events_rule_scope
    ON recon.alert_events (rule_id, scope_key, alert_status, last_triggered_at DESC);

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c3001'::uuid, 'ALERT_VIEW', 'Alerts View', 'ALERTS',
     'View alert rules and in-app alert events.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c3002'::uuid, 'ALERT_EDIT', 'Alerts Edit', 'ALERTS',
     'Create, update, acknowledge, resolve, and delete alert rules and events.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('ALERT_VIEW', 'ALERT_EDIT')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
