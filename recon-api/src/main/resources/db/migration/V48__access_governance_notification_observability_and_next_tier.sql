ALTER TABLE IF EXISTS recon.tenant_auth_config
    ADD COLUMN IF NOT EXISTS manager_access_review_next_tier_escalation_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS manager_access_review_next_tier_escalation_after_days integer NOT NULL DEFAULT 3;

ALTER TABLE IF EXISTS recon.users
    ADD COLUMN IF NOT EXISTS access_review_last_next_tier_escalated_at timestamp;

CREATE INDEX IF NOT EXISTS idx_access_governance_notification_jobs_tenant_created
    ON recon.access_governance_notification_jobs (tenant_id, created_at DESC);
