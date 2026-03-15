# RetailINQ Developer Checklist

## IDE Split

- IntelliJ: Java backend modules
- VS Code: `recon-ui`
- DBeaver: PostgreSQL and Oracle
- PowerShell: service startup and operations

## Before Starting

- Confirm required environment variables are loaded in the current shell.
- Confirm Kafka, PostgreSQL, Elasticsearch, and Oracle connectivity are available.
- Confirm Docker Desktop is running if local infra depends on it.

## Backend Startup

Run each service from `D:\Projects\recon-platform`:

```powershell
mvn -pl recon-api spring-boot:run
```

```powershell
mvn -pl xstore-kafka-publisher spring-boot:run
```

```powershell
mvn -pl siocs-kafka-poller spring-boot:run
```

```powershell
mvn -pl siocs-cloud-ingestion-connector spring-boot:run
```

Run `recon-flink-engine` in the intended runtime environment when split-view reconciliation indexing is needed.

## Frontend Startup

From `D:\Projects\recon-platform\recon-ui`:

```powershell
npm run dev
```

Open:

- `http://localhost:5173`

## Common URLs

- UI: `http://localhost:5173`
- API: `http://localhost:8090`
- Xstore publisher: `http://localhost:8181`
- SIOCS poller: `http://localhost:8182`

## Common Checks

- API health and auth behavior
- connector status endpoints
- Kafka publish activity in logs
- PostgreSQL staging/checkpoint tables in DBeaver
- Elasticsearch reconciliation documents when KPI values look wrong

## When KPI Tabs Show Zero

- Check whether `recon-api` is filtering by `reconView`.
- Check whether `recon-flink-engine` has produced new Elasticsearch documents with `reconView`.
- Replay the relevant connector data if old unsplit Elasticsearch documents are still the only records.
