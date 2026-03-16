package com.recon.xocs.repository;

import com.recon.xocs.domain.XocsIngestionCheckpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class XocsCheckpointRepository {

    private final JdbcTemplate jdbcTemplate;

    public XocsIngestionCheckpoint findOrCreate(String connectorName,
                                                String sourceName,
                                                String tenantId) {
        List<XocsIngestionCheckpoint> rows = jdbcTemplate.query("""
                SELECT connector_name, source_name, tenant_id,
                       last_cursor_id, last_success_timestamp,
                       last_poll_status, last_error_message
                FROM recon.xocs_ingestion_checkpoint
                WHERE connector_name = ?
                """, (rs, rn) -> XocsIngestionCheckpoint.builder()
                .connectorName(rs.getString("connector_name"))
                .sourceName(rs.getString("source_name"))
                .tenantId(rs.getString("tenant_id"))
                .lastCursorId(rs.getObject("last_cursor_id", Long.class))
                .lastSuccessTimestamp(rs.getTimestamp("last_success_timestamp"))
                .lastPollStatus(rs.getString("last_poll_status"))
                .lastErrorMessage(rs.getString("last_error_message"))
                .build(), connectorName);

        if (!rows.isEmpty()) {
            return rows.get(0);
        }

        jdbcTemplate.update("""
                INSERT INTO recon.xocs_ingestion_checkpoint (
                    connector_name, source_name, tenant_id,
                    last_cursor_id, last_success_timestamp,
                    last_poll_status, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, ?, 'READY', NOW(), NOW())
                """,
                connectorName, sourceName, tenantId, Timestamp.from(Instant.EPOCH));
        return XocsIngestionCheckpoint.builder()
                .connectorName(connectorName)
                .sourceName(sourceName)
                .tenantId(tenantId)
                .lastSuccessTimestamp(Timestamp.from(Instant.EPOCH))
                .lastPollStatus("READY")
                .build();
    }

    public void markStarted(String connectorName) {
        jdbcTemplate.update("""
                UPDATE recon.xocs_ingestion_checkpoint
                SET last_poll_started_at = NOW(),
                    last_run_started_at = NOW(),
                    last_poll_status = 'RUNNING',
                    last_run_status = 'RUNNING',
                    last_error_message = NULL,
                    updated_at = NOW()
                WHERE connector_name = ?
                """, connectorName);
    }

    public void advance(String connectorName, Long lastCursorId, Timestamp lastSuccessTimestamp) {
        jdbcTemplate.update("""
                UPDATE recon.xocs_ingestion_checkpoint
                SET last_cursor_id = ?,
                    last_success_timestamp = ?,
                    last_poll_completed_at = NOW(),
                    last_run_completed_at = NOW(),
                    last_poll_status = 'SUCCESS',
                    last_run_status = 'SUCCESS',
                    last_error_message = NULL,
                    updated_at = NOW()
                WHERE connector_name = ?
                """, lastCursorId, lastSuccessTimestamp, connectorName);
    }

    public void markFailed(String connectorName, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE recon.xocs_ingestion_checkpoint
                SET last_poll_completed_at = NOW(),
                    last_run_completed_at = NOW(),
                    last_poll_status = 'FAILED',
                    last_run_status = 'FAILED',
                    last_error_message = ?,
                    updated_at = NOW()
                WHERE connector_name = ?
                """, errorMessage, connectorName);
    }

    public void reset(String connectorName, Long lastCursorId, Timestamp lastSuccessTimestamp) {
        jdbcTemplate.update("""
                UPDATE recon.xocs_ingestion_checkpoint
                SET last_cursor_id = ?,
                    last_success_timestamp = ?,
                    last_poll_started_at = NULL,
                    last_poll_completed_at = NULL,
                    last_poll_status = 'READY',
                    last_run_started_at = NULL,
                    last_run_completed_at = NULL,
                    last_run_status = 'READY',
                    last_error_message = NULL,
                    updated_at = NOW()
                WHERE connector_name = ?
                """, lastCursorId, lastSuccessTimestamp, connectorName);
    }
}
