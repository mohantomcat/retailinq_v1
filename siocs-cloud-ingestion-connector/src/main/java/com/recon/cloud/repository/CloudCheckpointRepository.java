package com.recon.cloud.repository;

import com.recon.cloud.domain.CloudIngestionCheckpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class CloudCheckpointRepository {

    private final JdbcTemplate jdbcTemplate;

    public CloudIngestionCheckpoint findOrCreate(String connectorName,
                                                 String sourceName,
                                                 String tenantId) {
        String insertSql = """
                INSERT INTO recon.siocs_ingestion_checkpoint (
                    connector_name, source_name, tenant_id,
                    last_cursor_id, last_success_timestamp,
                    last_polled_timestamp, last_status,
                    last_error_message, created_at, updated_at
                ) VALUES (?, ?, ?, 0, ?, NULL, 'READY', NULL, NOW(), NOW())
                ON CONFLICT (connector_name)
                DO NOTHING
                """;
        jdbcTemplate.update(insertSql, connectorName, sourceName, tenantId,
                Timestamp.from(Instant.EPOCH));

        String selectSql = """
                SELECT connector_name, source_name, tenant_id,
                       last_cursor_id, last_success_timestamp,
                       last_polled_timestamp, last_status,
                       last_error_message
                FROM recon.siocs_ingestion_checkpoint
                WHERE connector_name = ?
                """;
        return jdbcTemplate.queryForObject(selectSql, (rs, rn) ->
                        CloudIngestionCheckpoint.builder()
                                .connectorName(rs.getString("connector_name"))
                                .sourceName(rs.getString("source_name"))
                                .tenantId(rs.getString("tenant_id"))
                                .lastCursorId(rs.getObject("last_cursor_id", Long.class))
                                .lastSuccessTimestamp(
                                        rs.getTimestamp("last_success_timestamp"))
                                .lastPolledTimestamp(
                                        rs.getTimestamp("last_polled_timestamp"))
                                .lastStatus(rs.getString("last_status"))
                                .lastErrorMessage(
                                        rs.getString("last_error_message"))
                                .build(),
                connectorName);
    }

    public void markStarted(String connectorName) {
        String sql = """
                UPDATE recon.siocs_ingestion_checkpoint
                SET last_polled_timestamp = NOW(),
                    last_status = 'RUNNING',
                    updated_at = NOW()
                WHERE connector_name = ?
                """;
        jdbcTemplate.update(sql, connectorName);
    }

    public void advance(String connectorName,
                        Long lastCursorId,
                        Timestamp lastSuccessTimestamp) {
        String sql = """
                UPDATE recon.siocs_ingestion_checkpoint
                SET last_cursor_id = ?,
                    last_success_timestamp = ?,
                    last_status = 'SUCCESS',
                    last_error_message = NULL,
                    updated_at = NOW()
                WHERE connector_name = ?
                """;
        jdbcTemplate.update(sql,
                lastCursorId == null ? 0L : lastCursorId,
                lastSuccessTimestamp,
                connectorName);
    }

    public void markFailed(String connectorName, String errorMessage) {
        String sql = """
                UPDATE recon.siocs_ingestion_checkpoint
                SET last_status = 'FAILED',
                    last_error_message = ?,
                    updated_at = NOW()
                WHERE connector_name = ?
                """;
        jdbcTemplate.update(sql, errorMessage, connectorName);
    }

    public void reset(String connectorName,
                      Long lastCursorId,
                      Timestamp lastSuccessTimestamp) {
        String sql = """
                UPDATE recon.siocs_ingestion_checkpoint
                SET last_cursor_id = ?,
                    last_success_timestamp = ?,
                    last_polled_timestamp = NULL,
                    last_status = 'READY',
                    last_error_message = NULL,
                    updated_at = NOW()
                WHERE connector_name = ?
                """;
        jdbcTemplate.update(sql,
                lastCursorId == null ? 0L : lastCursorId,
                lastSuccessTimestamp,
                connectorName);
    }
}
