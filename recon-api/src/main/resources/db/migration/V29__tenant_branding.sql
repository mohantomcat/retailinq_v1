CREATE TABLE IF NOT EXISTS recon.tenant_branding (
    tenant_id varchar(100) PRIMARY KEY REFERENCES recon.tenant_config(tenant_id) ON DELETE CASCADE,
    app_name varchar(160),
    light_logo_data text,
    dark_logo_data text,
    primary_color varchar(7) NOT NULL DEFAULT '#3F6FD8',
    secondary_color varchar(7) NOT NULL DEFAULT '#5F7CE2',
    updated_by varchar(100),
    updated_at timestamp NOT NULL DEFAULT now()
);

INSERT INTO recon.tenant_branding (tenant_id)
SELECT tenant_id
FROM recon.tenant_config
ON CONFLICT (tenant_id) DO NOTHING;
