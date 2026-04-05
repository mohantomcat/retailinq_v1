update recon.recon_module_catalog
set operations_module_ids = 'siocs-cloud-connector,mfcs-rds-connector',
    operations_module_catalog_json = '[
      {
        "moduleId": "siocs-cloud-connector",
        "moduleLabel": "SIOCS Cloud Connector",
        "category": "source",
        "baseUrlKey": "SIOCS_CLOUD",
        "statusPath": "/api/cloud-connector/status",
        "actionPathPrefix": "/api/cloud-connector/actions",
        "safeActions": ["download", "publish", "release-stale-claims", "requeue-failed", "requeue-dlq"],
        "advancedActions": ["reset-checkpoint", "replay-window"],
        "basicAuth": false,
        "resetPayloadMode": "CLOUD_CURSOR",
        "freshnessThresholdMinutes": 60
      },
      {
        "moduleId": "mfcs-rds-connector",
        "moduleLabel": "MFCS RDS Connector",
        "category": "counterparty",
        "baseUrlKey": "MFCS_RDS",
        "statusPath": "/api/mfcs-connector/status",
        "actionPathPrefix": "/api/mfcs-connector/actions",
        "safeActions": ["download", "publish", "release-stale-claims", "requeue-failed", "requeue-dlq"],
        "advancedActions": ["reset-checkpoint", "replay-window"],
        "basicAuth": false,
        "resetPayloadMode": "CLOUD_CURSOR",
        "freshnessThresholdMinutes": 60
      }
    ]',
    integration_connector_keys = 'siocs-cloud-main,mfcs-rds-main',
    updated_at = now()
where recon_view = 'SIOCS_MFCS';

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
    operations_module_catalog_json,
    integration_connector_keys,
    is_active,
    created_at,
    updated_at
)
values
    (
        '6e40c0a1-0ac8-477d-86df-1e29a2e4f206'::uuid,
        'SIM_MFCS',
        'sim-mfcs',
        'SIM vs MFCS',
        'MFCS',
        'RECON_SIM_MFCS',
        60,
        'sim-mfcs',
        'sim-db-connector,mfcs-rds-connector',
        '[
          {
            "moduleId": "sim-db-connector",
            "moduleLabel": "SIM DB Connector",
            "category": "source",
            "baseUrlKey": "SIM",
            "statusPath": "/api/siocs-poller/status",
            "actionPathPrefix": "/api/siocs-poller/actions",
            "safeActions": ["poll", "release-lease"],
            "advancedActions": ["reset-checkpoint"],
            "basicAuth": true,
            "resetPayloadMode": "DB_POLLING",
            "freshnessThresholdMinutes": 45
          },
          {
            "moduleId": "mfcs-rds-connector",
            "moduleLabel": "MFCS RDS Connector",
            "category": "counterparty",
            "baseUrlKey": "MFCS_RDS",
            "statusPath": "/api/mfcs-connector/status",
            "actionPathPrefix": "/api/mfcs-connector/actions",
            "safeActions": ["download", "publish", "release-stale-claims", "requeue-failed", "requeue-dlq"],
            "advancedActions": ["reset-checkpoint", "replay-window"],
            "basicAuth": false,
            "resetPayloadMode": "CLOUD_CURSOR",
            "freshnessThresholdMinutes": 60
          }
        ]',
        'sim-poller,mfcs-rds-main',
        true,
        now(),
        now()
    ),
    (
        '6e40c0a1-0ac8-477d-86df-1e29a2e4f207'::uuid,
        'SIOCS_RMS',
        'siocs-rms',
        'SIOCS vs RMS',
        'RMS',
        'RECON_SIOCS_RMS',
        70,
        'siocs-rms',
        'siocs-cloud-connector,rms-db-connector',
        '[
          {
            "moduleId": "siocs-cloud-connector",
            "moduleLabel": "SIOCS Cloud Connector",
            "category": "source",
            "baseUrlKey": "SIOCS_CLOUD",
            "statusPath": "/api/cloud-connector/status",
            "actionPathPrefix": "/api/cloud-connector/actions",
            "safeActions": ["download", "publish", "release-stale-claims", "requeue-failed", "requeue-dlq"],
            "advancedActions": ["reset-checkpoint", "replay-window"],
            "basicAuth": false,
            "resetPayloadMode": "CLOUD_CURSOR",
            "freshnessThresholdMinutes": 60
          },
          {
            "moduleId": "rms-db-connector",
            "moduleLabel": "RMS DB Connector",
            "category": "counterparty",
            "baseUrlKey": "RMS",
            "statusPath": "/api/rms-poller/status",
            "actionPathPrefix": "/api/rms-poller/actions",
            "safeActions": ["poll", "release-lease"],
            "advancedActions": ["reset-checkpoint"],
            "basicAuth": true,
            "resetPayloadMode": "DB_POLLING",
            "freshnessThresholdMinutes": 45
          }
        ]',
        'siocs-cloud-main,rms-db-main',
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
    operations_module_catalog_json = excluded.operations_module_catalog_json,
    integration_connector_keys = excluded.integration_connector_keys,
    is_active = excluded.is_active,
    updated_at = now();

insert into recon.permissions (id, code, name, module, description, created_at)
values
    ('6e40c0a1-0ac8-477d-86df-1e29a2e4f216'::uuid, 'RECON_SIM_MFCS', 'SIM vs MFCS Reconciliation', 'RECONCILIATION', 'View and work the SIM vs MFCS reconciliation lane.', now()),
    ('6e40c0a1-0ac8-477d-86df-1e29a2e4f217'::uuid, 'RECON_SIOCS_RMS', 'SIOCS vs RMS Reconciliation', 'RECONCILIATION', 'View and work the SIOCS vs RMS reconciliation lane.', now())
on conflict (code) do update set
    name = excluded.name,
    module = excluded.module,
    description = excluded.description;

insert into recon.role_permissions (role_id, permission_id)
select r.id, p.id
from recon.roles r
join recon.permissions p
  on p.code in ('RECON_SIM_MFCS', 'RECON_SIOCS_RMS')
where lower(r.name) = 'admin'
  and not exists (
      select 1
      from recon.role_permissions rp
      where rp.role_id = r.id
        and rp.permission_id = p.id
  );
