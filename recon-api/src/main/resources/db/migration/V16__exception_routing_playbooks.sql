ALTER TABLE recon.exception_cases
    ADD COLUMN IF NOT EXISTS recon_status varchar(50),
    ADD COLUMN IF NOT EXISTS auto_assigned boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS routing_rule_id uuid,
    ADD COLUMN IF NOT EXISTS routing_rule_name varchar(150),
    ADD COLUMN IF NOT EXISTS playbook_id uuid,
    ADD COLUMN IF NOT EXISTS playbook_name varchar(150);

CREATE TABLE IF NOT EXISTS recon.exception_routing_rules (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    rule_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    recon_status varchar(50),
    min_severity varchar(20) NOT NULL DEFAULT 'MEDIUM',
    root_cause_category varchar(80),
    reason_code varchar(80),
    store_id varchar(50),
    target_assignee_username varchar(100),
    target_role_name varchar(100),
    priority integer NOT NULL DEFAULT 100,
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_exception_routing_rules_tenant_recon
    ON recon.exception_routing_rules (tenant_id, recon_view, active, priority, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.exception_playbooks (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    playbook_name varchar(150) NOT NULL,
    recon_view varchar(50) NOT NULL,
    recon_status varchar(50),
    min_severity varchar(20) NOT NULL DEFAULT 'MEDIUM',
    root_cause_category varchar(80),
    reason_code varchar(80),
    active boolean NOT NULL DEFAULT true,
    description text,
    created_by varchar(100) NOT NULL,
    updated_by varchar(100) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_exception_playbooks_tenant_recon
    ON recon.exception_playbooks (tenant_id, recon_view, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.exception_playbook_steps (
    id uuid PRIMARY KEY,
    playbook_id uuid NOT NULL REFERENCES recon.exception_playbooks(id) ON DELETE CASCADE,
    step_order integer NOT NULL,
    step_title varchar(200) NOT NULL,
    step_detail text,
    operation_module_id varchar(80),
    operation_action_key varchar(80),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_exception_playbook_steps_playbook
    ON recon.exception_playbook_steps (playbook_id, step_order);

INSERT INTO recon.permissions (id, code, name, module, description, created_at)
VALUES
    ('1d51ad3a-3b84-4d5c-9db0-11b37f0d1601'::uuid, 'EXCEPTION_AUTOMATION_VIEW', 'Exception Automation View', 'EXCEPTIONS',
     'View exception routing rules, playbooks, and automation guidance.', now()),
    ('1d51ad3a-3b84-4d5c-9db0-11b37f0d1602'::uuid, 'EXCEPTION_AUTOMATION_EDIT', 'Exception Automation Edit', 'EXCEPTIONS',
     'Create and update exception routing rules and resolution playbooks.', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO recon.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM recon.roles r
JOIN recon.permissions p
  ON p.code IN ('EXCEPTION_AUTOMATION_VIEW', 'EXCEPTION_AUTOMATION_EDIT')
WHERE lower(r.name) = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM recon.role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
