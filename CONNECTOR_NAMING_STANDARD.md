# Connector Naming Standard

Use system-specific names for every connector-facing artifact.

Module and artifact naming:
- `<system>-cloud-ingestion-connector` for cloud pull connectors
- `<system>-kafka-publisher` for DB-to-Kafka publisher connectors
- `<system>-kafka-poller` for DB poller connectors

Database object naming:
- `recon.<system>_ingestion_checkpoint`
- `recon.<system>_ingestion_raw`
- `recon.<system>_ingestion_transaction`
- `recon.<system>_ingestion_line`
- `recon.<system>_ingestion_error`

Runtime identity naming:
- `connector-name`: `<system>-rest-main` or `<system>-db-main`
- `source-name`: uppercase system name such as `SIOCS`, `XOCS`, `XSTORE`
- Kafka event type: `<SYSTEM>_TRANSACTION`

Future connectors:
- do not use generic names such as `cloud`, `rest`, or `sim` when the connector is system-specific
- keep table names, module names, README references, and application names aligned from the first commit

Current intentional exception:
- the SIOCS Flyway history table remains `cloud_flyway_schema_history` to preserve existing migration history safely
