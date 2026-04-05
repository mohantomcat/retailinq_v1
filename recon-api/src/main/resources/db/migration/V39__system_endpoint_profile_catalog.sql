create table if not exists recon.system_endpoint_runtime_catalog (
    id uuid primary key,
    system_name varchar(64) not null,
    system_label varchar(128) not null,
    endpoint_mode varchar(32) not null,
    connector_module_id varchar(64),
    connector_label varchar(128),
    integration_connector_key varchar(64),
    base_url_key varchar(64),
    display_order integer not null,
    implemented boolean not null default true,
    is_default boolean not null default false,
    is_active boolean not null default true,
    notes varchar(512),
    created_at timestamp without time zone not null default now(),
    updated_at timestamp without time zone not null default now(),
    constraint uk_system_endpoint_runtime_catalog unique (system_name, endpoint_mode)
);

insert into recon.system_endpoint_runtime_catalog (
    id,
    system_name,
    system_label,
    endpoint_mode,
    connector_module_id,
    connector_label,
    integration_connector_key,
    base_url_key,
    display_order,
    implemented,
    is_default,
    is_active,
    notes,
    created_at,
    updated_at
)
values
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12001'::uuid,
        'XSTORE',
        'Xstore',
        'DB',
        'xstore-publisher',
        'Xstore Publisher',
        'xstore-publisher',
        'XSTORE',
        10,
        true,
        true,
        true,
        'On-prem POS database publishing runtime.',
        now(),
        now()
    ),
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12002'::uuid,
        'SIM',
        'SIM',
        'DB',
        'sim-db-connector',
        'SIM DB Connector',
        'sim-poller',
        'SIM',
        20,
        true,
        true,
        true,
        'On-prem SIM polling runtime.',
        now(),
        now()
    ),
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12003'::uuid,
        'RMS',
        'RMS',
        'DB',
        'rms-db-connector',
        'RMS DB Connector',
        'rms-db-main',
        'RMS',
        30,
        true,
        true,
        true,
        'On-prem RMS polling runtime.',
        now(),
        now()
    ),
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12004'::uuid,
        'SIOCS',
        'SIOCS',
        'REST',
        'siocs-cloud-connector',
        'SIOCS Cloud Connector',
        'siocs-cloud-main',
        'SIOCS_CLOUD',
        40,
        true,
        true,
        true,
        'Current cloud API ingestion runtime for SIOCS.',
        now(),
        now()
    ),
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12005'::uuid,
        'SIOCS',
        'SIOCS',
        'DAS',
        null,
        'SIOCS DAS Connector',
        null,
        null,
        41,
        false,
        false,
        true,
        'Future PaaS/DAS ingestion profile for SIOCS via replicated Oracle data.',
        now(),
        now()
    ),
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12006'::uuid,
        'MFCS',
        'MFCS',
        'RDS',
        'mfcs-rds-connector',
        'MFCS RDS Connector',
        'mfcs-rds-main',
        'MFCS_RDS',
        50,
        true,
        true,
        true,
        'Current ORDS/RDS ingestion runtime for MFCS.',
        now(),
        now()
    ),
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12007'::uuid,
        'MFCS',
        'MFCS',
        'DAS',
        null,
        'MFCS DAS Connector',
        null,
        null,
        51,
        false,
        false,
        true,
        'Future PaaS/DAS ingestion profile for MFCS via replicated Oracle data.',
        now(),
        now()
    ),
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12008'::uuid,
        'XOCS',
        'XOCS',
        'REST',
        'xocs-cloud-connector',
        'XOCS Cloud Connector',
        'xocs-cloud-main',
        'XOCS_CLOUD',
        60,
        true,
        true,
        true,
        'Current cloud API ingestion runtime for XOCS.',
        now(),
        now()
    ),
    (
        '75dc9a21-1f65-4afc-bd90-6cad6fe12009'::uuid,
        'XOCS',
        'XOCS',
        'DAS',
        null,
        'XOCS DAS Connector',
        null,
        null,
        61,
        false,
        false,
        true,
        'Future PaaS/DAS ingestion profile for XOCS via replicated Oracle data.',
        now(),
        now()
    )
on conflict (system_name, endpoint_mode) do update set
    system_label = excluded.system_label,
    connector_module_id = excluded.connector_module_id,
    connector_label = excluded.connector_label,
    integration_connector_key = excluded.integration_connector_key,
    base_url_key = excluded.base_url_key,
    display_order = excluded.display_order,
    implemented = excluded.implemented,
    is_default = excluded.is_default,
    is_active = excluded.is_active,
    notes = excluded.notes,
    updated_at = now();

create index if not exists idx_system_endpoint_runtime_catalog_system_name
    on recon.system_endpoint_runtime_catalog (system_name);

create table if not exists recon.tenant_system_endpoint_profile (
    id uuid primary key,
    tenant_id varchar(64) not null,
    system_name varchar(64) not null,
    endpoint_mode varchar(32) not null,
    connector_module_id varchar(64),
    updated_by varchar(128),
    created_at timestamp without time zone not null default now(),
    updated_at timestamp without time zone not null default now(),
    constraint uk_tenant_system_endpoint_profile unique (tenant_id, system_name)
);

create index if not exists idx_tenant_system_endpoint_profile_tenant
    on recon.tenant_system_endpoint_profile (tenant_id);

insert into recon.tenant_system_endpoint_profile (
    id,
    tenant_id,
    system_name,
    endpoint_mode,
    connector_module_id,
    updated_by,
    created_at,
    updated_at
)
select
    (
        substr(md5(tenant.tenant_id || ':system-endpoint:' || catalog.system_name), 1, 8) || '-' ||
        substr(md5(tenant.tenant_id || ':system-endpoint:' || catalog.system_name), 9, 4) || '-' ||
        substr(md5(tenant.tenant_id || ':system-endpoint:' || catalog.system_name), 13, 4) || '-' ||
        substr(md5(tenant.tenant_id || ':system-endpoint:' || catalog.system_name), 17, 4) || '-' ||
        substr(md5(tenant.tenant_id || ':system-endpoint:' || catalog.system_name), 21, 12)
    )::uuid,
    tenant.tenant_id,
    catalog.system_name,
    catalog.endpoint_mode,
    catalog.connector_module_id,
    'system',
    now(),
    now()
from recon.tenant_config tenant
join recon.system_endpoint_runtime_catalog catalog
  on catalog.is_default = true
 and catalog.implemented = true
 and catalog.is_active = true
where tenant.active = true
  and not exists (
      select 1
      from recon.tenant_system_endpoint_profile profile
      where profile.tenant_id = tenant.tenant_id
        and upper(profile.system_name) = upper(catalog.system_name)
  );
