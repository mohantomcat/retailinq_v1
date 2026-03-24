# RetailINQ Baseline Backup Pack

This backup pack captures the application baseline as of March 25, 2026.

Code baseline:
- Branch: `main`
- Commit: `e295dc5320e8b747b6a8537ac984f040fe8375ba`
- Snapshot branch: `snapshot-2026-03-25`
- Tag: `baseline-2026-03-25-e295dc5`

Contents:
- `code-snapshot.txt`: code reference metadata
- `recondb.dump`: PostgreSQL custom-format dump with schema and data
- `recondb-schema.sql`: PostgreSQL schema-only export
- `postgres-globals.sql`: PostgreSQL roles/global objects export
- `elasticsearch-indices.json`: Elasticsearch index inventory
- `elasticsearch-cluster-health.json`: cluster health at export time
- `elasticsearch-recon-transactions-mapping.json`: index mappings
- `elasticsearch-recon-transactions-settings.json`: index settings
- `elasticsearch-recon-transactions-docs.json`: raw search export of all indexed documents
- `elasticsearch-recon-transactions-bulk.ndjson`: bulk-importable document export
- `elasticsearch-recon-transactions-create.json`: ready-to-use create-index payload
- `checksums.json`: SHA-256 hashes for the backup artifacts

Not included:
- `.env` files or secrets
- Docker named volumes themselves
- Kafka topic payload backup
- runtime logs

## Restore Code

Use either the snapshot branch or the tag:

```powershell
git checkout snapshot-2026-03-25
```

or

```powershell
git checkout baseline-2026-03-25-e295dc5
```

## Restore PostgreSQL

Start PostgreSQL:

```powershell
docker compose up -d postgres
```

Restore globals:

```powershell
Get-Content .\backups\baseline-2026-03-25-e295dc5\postgres-globals.sql -Raw | docker exec -i recon-postgres psql -U recon -d postgres
```

Recreate the database if needed:

```powershell
docker exec recon-postgres psql -U recon -d postgres -c "DROP DATABASE IF EXISTS recondb_restore;"
docker exec recon-postgres psql -U recon -d postgres -c "CREATE DATABASE recondb_restore;"
```

Restore full dump:

```powershell
Get-Content .\backups\baseline-2026-03-25-e295dc5\recondb.dump -Encoding Byte | docker exec -i recon-postgres pg_restore -U recon -d recondb_restore --clean --if-exists --no-owner
```

If you want schema only:

```powershell
Get-Content .\backups\baseline-2026-03-25-e295dc5\recondb-schema.sql -Raw | docker exec -i recon-postgres psql -U recon -d recondb_restore
```

## Restore Elasticsearch

Start Elasticsearch:

```powershell
docker compose up -d elasticsearch
```

Delete and recreate the index:

```powershell
Invoke-WebRequest -UseBasicParsing -Method Delete -Uri "http://localhost:9200/recon-transactions"
Invoke-WebRequest -UseBasicParsing -Method Put -Uri "http://localhost:9200/recon-transactions" -ContentType "application/json" -InFile .\backups\baseline-2026-03-25-e295dc5\elasticsearch-recon-transactions-create.json
```

Then bulk load:

```powershell
Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:9200/recon-transactions/_bulk" -ContentType "application/x-ndjson" -InFile .\backups\baseline-2026-03-25-e295dc5\elasticsearch-recon-transactions-bulk.ndjson
Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:9200/recon-transactions/_refresh"
```

## Validation

Compare file hashes with:

```powershell
Get-FileHash .\backups\baseline-2026-03-25-e295dc5\*
```

Then compare against `checksums.json`.
