create table if not exists recon.recon_module_catalog (
    id uuid primary key,
    recon_view varchar(64) not null unique,
    tab_id varchar(64) not null unique,
    module_label varchar(128) not null,
    target_system varchar(64),
    permission_code varchar(128) not null unique,
    display_order integer not null,
    configuration_module_id varchar(64),
    operations_module_ids text,
    integration_connector_keys text,
    is_active boolean not null default true,
    created_at timestamp without time zone not null default now(),
    updated_at timestamp without time zone not null default now()
);

insert into recon.recon_module_catalog (
    id,
    recon_view,
    tab_id,
    module_label,
    target_system,
    permission_code,
    display_order,
    configuration_module_id,
    operations_module_ids,
    integration_connector_keys,
    is_active,
    created_at,
    updated_at
)
values
    (
        '6e40c0a1-0ac8-477d-86df-1e29a2e4f201'::uuid,
        'XSTORE_SIM',
        'xstore-sim',
        'Xstore vs SIM',
        'SIM',
        'RECON_XSTORE_SIM',
        10,
        'xstore-sim',
        'xstore-publisher,sim-poller',
        'xstore-publisher,sim-poller',
        true,
        now(),
        now()
    ),
    (
        '6e40c0a1-0ac8-477d-86df-1e29a2e4f202'::uuid,
        'XSTORE_SIOCS',
        'xstore-siocs',
        'Xstore vs SIOCS',
        'SIOCS',
        'RECON_XSTORE_SIOCS',
        20,
        'xstore-siocs',
        'xstore-publisher,siocs-cloud-connector',
        'xstore-publisher,siocs-cloud-main',
        true,
        now(),
        now()
    ),
    (
        '6e40c0a1-0ac8-477d-86df-1e29a2e4f203'::uuid,
        'XSTORE_XOCS',
        'xstore-xocs',
        'Xstore vs XOCS',
        'XOCS',
        'RECON_XSTORE_XOCS',
        30,
        'xstore-xocs',
        'xstore-publisher,xocs-cloud-connector',
        'xstore-publisher,xocs-cloud-main',
        true,
        now(),
        now()
    ),
    (
        '6e40c0a1-0ac8-477d-86df-1e29a2e4f204'::uuid,
        'SIOCS_MFCS',
        'siocs-mfcs',
        'SIOCS ↔ MFCS',
        'MFCS',
        'RECON_SIOCS_MFCS',
        40,
        'siocs-mfcs',
        'mfcs-rds-connector',
        'mfcs-rds-main',
        true,
        now(),
        now()
    ),
    (
        '6e40c0a1-0ac8-477d-86df-1e29a2e4f205'::uuid,
        'SIM_RMS',
        'sim-rms',
        'SIM ↔ RMS',
        'RMS',
        'RECON_SIM_RMS',
        50,
        'sim-rms',
        'sim-db-connector,rms-db-connector',
        'sim-poller,rms-db-main',
        true,
        now(),
        now()
    )
on conflict (recon_view) do update set
    tab_id = excluded.tab_id,
    module_label = excluded.module_label,
    target_system = excluded.target_system,
    permission_code = excluded.permission_code,
    display_order = excluded.display_order,
    configuration_module_id = excluded.configuration_module_id,
    operations_module_ids = excluded.operations_module_ids,
    integration_connector_keys = excluded.integration_connector_keys,
    is_active = excluded.is_active,
    updated_at = now();

insert into recon.permissions (id, code, name, module, description, created_at)
values
    ('6e40c0a1-0ac8-477d-86df-1e29a2e4f211'::uuid, 'RECON_XSTORE_SIM', 'Xstore vs SIM Reconciliation', 'RECONCILIATION', 'View and work the Xstore vs SIM reconciliation lane.', now()),
    ('6e40c0a1-0ac8-477d-86df-1e29a2e4f212'::uuid, 'RECON_XSTORE_SIOCS', 'Xstore vs SIOCS Reconciliation', 'RECONCILIATION', 'View and work the Xstore vs SIOCS reconciliation lane.', now()),
    ('6e40c0a1-0ac8-477d-86df-1e29a2e4f213'::uuid, 'RECON_XSTORE_XOCS', 'Xstore vs XOCS Reconciliation', 'RECONCILIATION', 'View and work the Xstore vs XOCS reconciliation lane.', now()),
    ('0fbc8f31-2dd8-4aa3-9442-bd18b2b93001'::uuid, 'RECON_SIOCS_MFCS', 'SIOCS vs MFCS Reconciliation', 'RECONCILIATION', 'View and work the SIOCS vs MFCS reconciliation lane.', now()),
    ('3a05d367-d4d7-4d61-8c14-18a9167208fa'::uuid, 'RECON_SIM_RMS', 'SIM vs RMS Reconciliation', 'RECONCILIATION', 'View and work the SIM vs RMS reconciliation lane.', now())
on conflict (code) do update set
    name = excluded.name,
    module = excluded.module,
    description = excluded.description;

insert into recon.role_permissions (role_id, permission_id)
select r.id, p.id
from recon.roles r
join recon.permissions p
  on p.code in (
      'RECON_XSTORE_SIM',
      'RECON_XSTORE_SIOCS',
      'RECON_XSTORE_XOCS',
      'RECON_SIOCS_MFCS',
      'RECON_SIM_RMS'
  )
where lower(r.name) = 'admin'
  and not exists (
      select 1
      from recon.role_permissions rp
      where rp.role_id = r.id
        and rp.permission_id = p.id
  );
