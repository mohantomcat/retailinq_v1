CREATE TABLE IF NOT EXISTS recon.exception_sla_rules (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    recon_view varchar(50) NOT NULL,
    severity varchar(20) NOT NULL,
    target_minutes integer NOT NULL,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uk_exception_sla_rule UNIQUE (tenant_id, recon_view, severity)
);

CREATE INDEX IF NOT EXISTS idx_exception_sla_rules_tenant_recon
    ON recon.exception_sla_rules (tenant_id, recon_view, severity);

ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS severity varchar(20) NOT NULL DEFAULT 'MEDIUM';

ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS store_id varchar(30);

ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS wkstn_id varchar(30);

ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS business_date date;

ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS sla_target_minutes integer NOT NULL DEFAULT 480;

ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS due_at timestamp;

ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS breached_at timestamp;

CREATE INDEX IF NOT EXISTS idx_exception_cases_sla_due
    ON recon.exception_cases (tenant_id, recon_view, case_status, due_at);

CREATE INDEX IF NOT EXISTS idx_exception_cases_sla_scope
    ON recon.exception_cases (tenant_id, recon_view, store_id, wkstn_id, assignee_username);

INSERT INTO recon.exception_sla_rules (
    id, tenant_id, recon_view, severity, target_minutes, description, created_by, updated_by, created_at, updated_at
)
VALUES
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3001'::uuid, 'tenant-india', 'XSTORE_SIM',   'LOW',      1440, 'Default SLA for low-severity Xstore vs SIM exceptions.',   'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3002'::uuid, 'tenant-india', 'XSTORE_SIM',   'MEDIUM',    480, 'Default SLA for medium-severity Xstore vs SIM exceptions.', 'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3003'::uuid, 'tenant-india', 'XSTORE_SIM',   'HIGH',      240, 'Default SLA for high-severity Xstore vs SIM exceptions.',   'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3004'::uuid, 'tenant-india', 'XSTORE_SIM',   'CRITICAL',   60, 'Default SLA for critical Xstore vs SIM exceptions.',        'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3005'::uuid, 'tenant-india', 'XSTORE_SIOCS', 'LOW',       720, 'Default SLA for low-severity Xstore vs SIOCS exceptions.', 'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3006'::uuid, 'tenant-india', 'XSTORE_SIOCS', 'MEDIUM',    240, 'Default SLA for medium-severity Xstore vs SIOCS exceptions.', 'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3007'::uuid, 'tenant-india', 'XSTORE_SIOCS', 'HIGH',      120, 'Default SLA for high-severity Xstore vs SIOCS exceptions.', 'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3008'::uuid, 'tenant-india', 'XSTORE_SIOCS', 'CRITICAL',   60, 'Default SLA for critical Xstore vs SIOCS exceptions.',    'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3009'::uuid, 'tenant-india', 'XSTORE_XOCS',  'LOW',       720, 'Default SLA for low-severity Xstore vs XOCS exceptions.',  'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3010'::uuid, 'tenant-india', 'XSTORE_XOCS',  'MEDIUM',    240, 'Default SLA for medium-severity Xstore vs XOCS exceptions.', 'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3011'::uuid, 'tenant-india', 'XSTORE_XOCS',  'HIGH',      120, 'Default SLA for high-severity Xstore vs XOCS exceptions.', 'system', 'system', now(), now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3012'::uuid, 'tenant-india', 'XSTORE_XOCS',  'CRITICAL',   60, 'Default SLA for critical Xstore vs XOCS exceptions.',     'system', 'system', now(), now())
ON CONFLICT (tenant_id, recon_view, severity) DO NOTHING;

UPDATE recon.exception_cases
SET due_at = COALESCE(
        due_at,
        created_at + make_interval(mins => COALESCE(sla_target_minutes, 480))
    )
WHERE due_at IS NULL;

UPDATE recon.exception_cases
SET breached_at = COALESCE(
        breached_at,
        CASE
            WHEN case_status NOT IN ('RESOLVED', 'IGNORED')
                 AND due_at IS NOT NULL
                 AND due_at < now()
            THEN now()
            ELSE NULL
        END
    )
WHERE breached_at IS NULL;

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3f01'::uuid, 'SLA_VIEW', 'SLA Management View', 'SLA',
     'View SLA rules, breach indicators, and aging analytics.', now()),
    ('57cb1041-b460-4b6e-8463-cf5b8f5d3f02'::uuid, 'SLA_EDIT', 'SLA Management Edit', 'SLA',
     'Create and update SLA targets by module and severity.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('SLA_VIEW', 'SLA_EDIT')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
