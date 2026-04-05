package com.recon.rms.repository;

import com.recon.rms.domain.RmsPollCheckpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@Slf4j
public class CheckpointRepository {

    private final JdbcTemplate jdbcTemplate;

    public CheckpointRepository(
            @Qualifier("checkpointJdbcTemplate")
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RmsPollCheckpoint findOrCreate(String pollerId, String tenantId) {
        try {
            return findById(pollerId);
        } catch (EmptyResultDataAccessException e) {
            Timestamp epoch = Timestamp.from(Instant.EPOCH);
            String insertSql = """
                    INSERT INTO recon.rms_poll_checkpoint (
                        poller_id, tenant_id, last_processed_timestamp,
                        last_processed_external_id, last_processed_id,
                        last_poll_status, total_records_polled, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'READY', 0, ?)
                    """;
            jdbcTemplate.update(insertSql,
                    pollerId, tenantId, epoch, "", 0L, Timestamp.from(Instant.now()));
            return findById(pollerId);
        }
    }

    public RmsPollCheckpoint findById(String pollerId) {
        String sql = """
                SELECT poller_id, tenant_id,
                       last_processed_timestamp,
                       last_processed_external_id,
                       last_processed_id,
                       last_poll_started_at,
                       last_poll_completed_at,
                       last_poll_status,
                       last_error_message,
                       total_records_polled,
                       lock_owner,
                       lock_expires_at
                FROM recon.rms_poll_checkpoint
                WHERE poller_id = ?
                """;
        return jdbcTemplate.queryForObject(sql,
                (rs, rn) -> RmsPollCheckpoint.builder()
                        .pollerId(rs.getString("poller_id"))
                        .tenantId(rs.getString("tenant_id"))
                        .lastProcessedTimestamp(rs.getTimestamp("last_processed_timestamp"))
                        .lastProcessedExternalId(rs.getString("last_processed_external_id"))
                        .lastProcessedId(rs.getObject("last_processed_id", Long.class))
                        .lastPollStartedAt(rs.getTimestamp("last_poll_started_at"))
                        .lastPollCompletedAt(rs.getTimestamp("last_poll_completed_at"))
                        .lastPollStatus(rs.getString("last_poll_status"))
                        .lastErrorMessage(rs.getString("last_error_message"))
                        .totalRecordsPolled(rs.getLong("total_records_polled"))
                        .lockOwner(rs.getString("lock_owner"))
                        .lockExpiresAt(rs.getTimestamp("lock_expires_at"))
                        .build(),
                pollerId);
    }

    public boolean tryAcquireLease(String pollerId,
                                   String owner,
                                   int leaseTimeoutSeconds) {
        String sql = """
                UPDATE recon.rms_poll_checkpoint
                SET lock_owner = ?,
                    lock_expires_at = NOW() + (? * INTERVAL '1 second'),
                    updated_at = ?
                WHERE poller_id = ?
                  AND (
                      lock_expires_at IS NULL
                      OR lock_expires_at < NOW()
                      OR lock_owner = ?
                  )
                """;
        int updated = jdbcTemplate.update(sql,
                owner, leaseTimeoutSeconds, Timestamp.from(Instant.now()), pollerId, owner);
        return updated == 1;
    }

    public void releaseLease(String pollerId, String owner) {
        String sql = """
                UPDATE recon.rms_poll_checkpoint
                SET lock_owner = NULL,
                    lock_expires_at = NULL,
                    updated_at = ?
                WHERE poller_id = ?
                  AND lock_owner = ?
                """;
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), pollerId, owner);
    }

    public void forceReleaseLease(String pollerId) {
        String sql = """
                UPDATE recon.rms_poll_checkpoint
                SET lock_owner = NULL,
                    lock_expires_at = NULL,
                    updated_at = ?
                WHERE poller_id = ?
                """;
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), pollerId);
    }

    public void markStarted(String pollerId) {
        String sql = """
                UPDATE recon.rms_poll_checkpoint
                SET last_poll_started_at = ?,
                    last_poll_status = 'RUNNING',
                    updated_at = ?
                WHERE poller_id = ?
                """;
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(sql, now, now, pollerId);
    }

    public void markCompleted(String pollerId,
                              int recordsPolled) {
        String sql = """
                UPDATE recon.rms_poll_checkpoint
                SET last_poll_completed_at = ?,
                    last_poll_status = 'SUCCESS',
                    last_error_message = NULL,
                    total_records_polled = total_records_polled + ?,
                    updated_at = ?
                WHERE poller_id = ?
                """;
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(sql, now, recordsPolled, now, pollerId);
    }

    public void markFailed(String pollerId,
                           String errorMessage) {
        String sql = """
                UPDATE recon.rms_poll_checkpoint
                SET last_poll_completed_at = ?,
                    last_poll_status = 'FAILED',
                    last_error_message = ?,
                    updated_at = ?
                WHERE poller_id = ?
                """;
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(sql, now, errorMessage, now, pollerId);
    }

    public void updateComposite(String pollerId,
                                Timestamp lastTimestamp,
                                String lastExternalId,
                                long lastProcessedId) {
        String sql = """
                UPDATE recon.rms_poll_checkpoint
                SET last_processed_timestamp = ?,
                    last_processed_external_id = ?,
                    last_processed_id = ?,
                    updated_at = ?
                WHERE poller_id = ?
                """;
        jdbcTemplate.update(sql,
                lastTimestamp, lastExternalId, lastProcessedId,
                Timestamp.from(Instant.now()),
                pollerId);
    }

    public void resetCheckpoint(String pollerId,
                                Timestamp lastTimestamp,
                                String lastExternalId,
                                long lastProcessedId) {
        String sql = """
                UPDATE recon.rms_poll_checkpoint
                SET last_processed_timestamp = ?,
                    last_processed_external_id = ?,
                    last_processed_id = ?,
                    last_poll_status = 'READY',
                    last_error_message = NULL,
                    lock_owner = NULL,
                    lock_expires_at = NULL,
                    updated_at = ?
                WHERE poller_id = ?
                """;
        jdbcTemplate.update(sql,
                lastTimestamp,
                lastExternalId,
                lastProcessedId,
                Timestamp.from(Instant.now()),
                pollerId);
    }
}
