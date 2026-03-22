CREATE TABLE IF NOT EXISTS recon.exception_suppression_rules (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    rule_name VARCHAR(200) NOT NULL,
    recon_view VARCHAR(64) NOT NULL,
    recon_status VARCHAR(128),
    max_severity VARCHAR(32) NOT NULL DEFAULT 'LOW',
    root_cause_category VARCHAR(128),
    reason_code VARCHAR(128),
    store_id VARCHAR(64),
    action_type VARCHAR(32) NOT NULL DEFAULT 'SUPPRESS_QUEUE',
    max_value_at_risk NUMERIC(18,2),
    min_repeat_count INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_by VARCHAR(128) NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exception_suppression_rules_tenant_recon
    ON recon.exception_suppression_rules (tenant_id, recon_view, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS recon.exception_suppression_audits (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    case_id UUID NOT NULL,
    transaction_key VARCHAR(128) NOT NULL,
    recon_view VARCHAR(64) NOT NULL,
    rule_id UUID,
    rule_name VARCHAR(200),
    action_type VARCHAR(32) NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    result_message TEXT,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exception_suppression_audits_tenant_created
    ON recon.exception_suppression_audits (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_exception_suppression_audits_case
    ON recon.exception_suppression_audits (case_id, created_at DESC);
