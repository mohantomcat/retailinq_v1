# RetailINQ Platform

RetailINQ is a reconciliation platform for comparing Xstore transaction data with downstream retail transaction feeds and surfacing KPIs and transaction-level discrepancies in a React UI.

## Modules

- `recon-ui`: React/Vite frontend
- `recon-api`: Spring Boot API for dashboard, KPI, and transaction queries
- `recon-flink-engine`: Flink reconciliation engine
- `xstore-kafka-publisher`: Xstore database connector and Kafka publisher
- `siocs-kafka-poller`: SIOCS/SIM database connector and Kafka publisher
- `cloud-rest-ingestion-connector`: SIOCS cloud API connector with local staging and Kafka publishing

## Main Reconciliation Views

- `Xstore vs SIM`: Xstore database connector vs SIM database connector
- `Xstore vs SIOCS`: Xstore database connector vs SIOCS cloud API connector

## Architecture

```text
Xstore DB --------------------> xstore-kafka-publisher -----------+
                                                                  |
SIM/SIOCS DB ----------------> siocs-kafka-poller ---------------+|
                                                                 ||
SIOCS Cloud REST API --------> cloud-rest-ingestion-connector ---+|
                                                                  v
                                                        Kafka topics
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
mvn -pl cloud-rest-ingestion-connector spring-boot:run
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

## Notes

- The cloud connector publishes SIOCS cloud transactions into a separate reconciliation lane.
- KPI separation between `Xstore vs SIM` and `Xstore vs SIOCS` depends on the updated Flink engine indexing documents with `reconView`.
- Admin and actuator endpoints on the connector services are protected with HTTP Basic auth.
