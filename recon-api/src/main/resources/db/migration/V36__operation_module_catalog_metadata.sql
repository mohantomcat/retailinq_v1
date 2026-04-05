alter table recon.recon_module_catalog
    add column if not exists operations_module_catalog_json text;

update recon.recon_module_catalog
set module_label = case recon_view
        when 'XSTORE_SIM' then 'Xstore vs SIM'
        when 'XSTORE_SIOCS' then 'Xstore vs SIOCS'
        when 'XSTORE_XOCS' then 'Xstore vs XOCS'
        when 'SIOCS_MFCS' then 'SIOCS vs MFCS'
        when 'SIM_RMS' then 'SIM vs RMS'
        else module_label
    end,
    operations_module_catalog_json = case recon_view
        when 'XSTORE_SIM' then
            '[
              {
                "moduleId": "xstore-publisher",
                "moduleLabel": "Xstore Publisher",
                "category": "source",
                "baseUrlKey": "XSTORE",
                "statusPath": "/api/xstore-publisher/status",
                "actionPathPrefix": "/api/xstore-publisher/actions",
                "safeActions": ["publish", "release-stale-claims"],
                "advancedActions": [],
                "basicAuth": true,
                "resetPayloadMode": "NONE",
                "freshnessThresholdMinutes": 30
              },
              {
                "moduleId": "sim-poller",
                "moduleLabel": "SIM Poller",
                "category": "target",
                "baseUrlKey": "SIM",
                "statusPath": "/api/siocs-poller/status",
                "actionPathPrefix": "/api/siocs-poller/actions",
                "safeActions": ["poll", "release-lease"],
                "advancedActions": ["reset-checkpoint"],
                "basicAuth": true,
                "resetPayloadMode": "DB_POLLING",
                "freshnessThresholdMinutes": 45
              }
            ]'
        when 'XSTORE_SIOCS' then
            '[
              {
                "moduleId": "xstore-publisher",
                "moduleLabel": "Xstore Publisher",
                "category": "source",
                "baseUrlKey": "XSTORE",
                "statusPath": "/api/xstore-publisher/status",
                "actionPathPrefix": "/api/xstore-publisher/actions",
                "safeActions": ["publish", "release-stale-claims"],
                "advancedActions": [],
                "basicAuth": true,
                "resetPayloadMode": "NONE",
                "freshnessThresholdMinutes": 30
              },
              {
                "moduleId": "siocs-cloud-connector",
                "moduleLabel": "SIOCS Cloud Connector",
                "category": "target",
                "baseUrlKey": "SIOCS_CLOUD",
                "statusPath": "/api/cloud-connector/status",
                "actionPathPrefix": "/api/cloud-connector/actions",
                "safeActions": ["download", "publish", "release-stale-claims", "requeue-failed", "requeue-dlq"],
                "advancedActions": ["reset-checkpoint", "replay-window"],
                "basicAuth": false,
                "resetPayloadMode": "CLOUD_CURSOR",
                "freshnessThresholdMinutes": 60
              }
            ]'
        when 'XSTORE_XOCS' then
            '[
              {
                "moduleId": "xstore-publisher",
                "moduleLabel": "Xstore Publisher",
                "category": "source",
                "baseUrlKey": "XSTORE",
                "statusPath": "/api/xstore-publisher/status",
                "actionPathPrefix": "/api/xstore-publisher/actions",
                "safeActions": ["publish", "release-stale-claims"],
                "advancedActions": [],
                "basicAuth": true,
                "resetPayloadMode": "NONE",
                "freshnessThresholdMinutes": 30
              },
              {
                "moduleId": "xocs-cloud-connector",
                "moduleLabel": "XOCS Cloud Connector",
                "category": "target",
                "baseUrlKey": "XOCS_CLOUD",
                "statusPath": "/api/xocs-connector/status",
                "actionPathPrefix": "/api/xocs-connector/actions",
                "safeActions": ["download", "publish", "release-stale-claims", "requeue-failed"],
                "advancedActions": ["reset-checkpoint", "replay-window"],
                "basicAuth": false,
                "resetPayloadMode": "CLOUD_CURSOR",
                "freshnessThresholdMinutes": 60,
                "supportsRegisterFilter": true
              }
            ]'
        when 'SIOCS_MFCS' then
            '[
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
            ]'
        when 'SIM_RMS' then
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
                "moduleId": "rms-db-connector",
                "moduleLabel": "RMS DB Connector",
                "category": "target",
                "baseUrlKey": "RMS",
                "statusPath": "/api/rms-poller/status",
                "actionPathPrefix": "/api/rms-poller/actions",
                "safeActions": ["poll", "release-lease"],
                "advancedActions": ["reset-checkpoint"],
                "basicAuth": true,
                "resetPayloadMode": "DB_POLLING",
                "freshnessThresholdMinutes": 45
              }
            ]'
        else operations_module_catalog_json
    end,
    updated_at = now()
where recon_view in ('XSTORE_SIM', 'XSTORE_SIOCS', 'XSTORE_XOCS', 'SIOCS_MFCS', 'SIM_RMS');
