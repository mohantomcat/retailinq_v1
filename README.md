# RetailINQ Platform

RetailINQ is a reconciliation platform for comparing Xstore transaction data with downstream retail transaction feeds and surfacing KPIs and transaction-level discrepancies in a React UI.

## Modules

- `recon-ui`: React/Vite frontend
- `recon-api`: Spring Boot API for dashboard, KPI, and transaction queries
- `recon-flink-engine`: Flink reconciliation engine
- `xstore-kafka-publisher`: Xstore database connector and Kafka publisher
- `siocs-kafka-poller`: SIM database connector and Kafka publisher for POS and inventory domains
- `rms-db-connector`: RMS database connector and Kafka publisher for inventory-domain transactions
- `siocs-cloud-ingestion-connector`: SIOCS cloud connector with local staging and domain-scoped Kafka publishing

## Main Reconciliation Views

- `Xstore vs SIM`: Xstore database connector vs SIM database connector
- `Xstore vs SIOCS`: Xstore database connector vs SIOCS cloud API connector

## Architecture

```text
Xstore DB --------------------> xstore-kafka-publisher -----------> xstore.pos.transactions.raw
SIM DB -----------------------> siocs-kafka-poller ---------------> sim.pos.transactions.raw
                                                                 -> sim.inventory.transactions.raw
SIOCS Cloud REST API --------> siocs-cloud-ingestion-connector ---> siocs.pos.transactions.raw
                                                                 -> siocs.inventory.transactions.raw
MFCS Cloud / RDS -----------> mfcs-rds-ingestion-connector ------> mfcs.inventory.transactions.raw
XOCS Cloud -----------------> xocs-cloud-ingestion-connector ----> xocs.pos.transactions.raw
RMS DB -----------------------------------------------------------> rms.inventory.transactions.raw
                                                                  |
                                                                  v
                                                     recon-flink-engine
                                                                  v
                                                         Elasticsearch
                                                                  v
                                                            recon-api
                                                                  v
                                                             recon-ui
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 20+
- Kafka
- PostgreSQL
- Oracle access for Xstore and SIM/SIOCS database connectors
- Elasticsearch

## Environment Variables

Sensitive configuration is expected through environment variables, not hardcoded config.

Common variables used across the services:

- `PG_HOST`
- `PG_DB`
- `PG_USER`
- `PG_PASSWORD`
- `KAFKA_BROKERS`
- `KAFKA_XSTORE_POS_TOPIC`
- `KAFKA_SIM_POS_TOPIC`
- `KAFKA_SIM_INVENTORY_TOPIC`
- `KAFKA_SIM_UNKNOWN_TOPIC`
- `KAFKA_SIOCS_POS_TOPIC`
- `KAFKA_SIOCS_INVENTORY_TOPIC`
- `KAFKA_SIOCS_UNKNOWN_TOPIC`
- `KAFKA_MFCS_INVENTORY_TOPIC`
- `KAFKA_XOCS_POS_TOPIC`
- `KAFKA_RMS_INVENTORY_TOPIC`
- `CONNECTOR_ADMIN_USER`
- `CONNECTOR_ADMIN_PASSWORD`
- `DB_PASSWORD`
- `SIOCS_DB_PASSWORD`
- `CLOUD_AUTH_TYPE`
- `CLOUD_IDCS_TOKEN_URL`
- `CLOUD_IDCS_CLIENT_ID`
- `CLOUD_IDCS_CLIENT_SECRET`
- `CLOUD_IDCS_SCOPE`
- `CLOUD_API_BASE_URL`
- `CLOUD_API_TRANSACTIONS_PATH`

## Local Startup Order

Start the backend services first:

```powershell
cd D:\Projects\recon-platform
mvn -pl recon-api spring-boot:run
```

```powershell
cd D:\Projects\recon-platform
mvn -pl xstore-kafka-publisher spring-boot:run
```

```powershell
cd D:\Projects\recon-platform
mvn -pl siocs-kafka-poller spring-boot:run
```

```powershell
cd D:\Projects\recon-platform
mvn -pl rms-db-connector spring-boot:run
```

```powershell
cd D:\Projects\recon-platform
mvn -pl siocs-cloud-ingestion-connector spring-boot:run
```

Run the UI separately:

```powershell
cd D:\Projects\recon-platform\recon-ui
npm install
npm run dev
```

Default local URLs:

- UI: `http://localhost:5173`
- API: `http://localhost:8090`
- Xstore publisher: `http://localhost:8181`
- SIOCS poller: `http://localhost:8182`
- RMS connector: `http://localhost:8186`

## Notes

- The cloud connector publishes SIOCS cloud transactions into a separate reconciliation lane.
- KPI separation between `Xstore vs SIM` and `Xstore vs SIOCS` depends on the updated Flink engine indexing documents with `reconView`.
- Admin and actuator endpoints on the connector services are protected with HTTP Basic auth.

## Known Runtime Notes

- The UI split between `Xstore vs SIM` and `Xstore vs SIOCS` only works when `recon-flink-engine` is running the updated split-view build.
- Old Elasticsearch documents created before the `reconView` split do not automatically appear under either tab.
- If both tabs show `0`, the API is usually filtering by `reconView` correctly but Elasticsearch has not yet been repopulated with new split-view documents.
- After changing reconciliation view logic, replay the relevant connector data so Flink can rebuild Elasticsearch documents for the correct lane.
