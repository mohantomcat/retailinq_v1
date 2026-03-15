package com.recon.xocs.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class XocsErrorRepository {

    private final JdbcTemplate jdbcTemplate;

    public void save(String connectorName,
                     String tenantId,
                     String sourceName,
                     String sourceRecordKey,
                     String payloadJson,
                     String stage,
                     String errorCode,
                     String errorMessage,
                     boolean retryable) {
        jdbcTemplate.update("""
                INSERT INTO recon.xocs_ingestion_error (
                    connector_name, tenant_id, source_name, source_record_key,
                    payload_json, error_type, error_message, stage, error_code, retryable
                ) VALUES (?, ?, ?, ?, CAST(? AS TEXT), ?, ?, ?, ?, ?)
                """,
                connectorName,
                tenantId,
                sourceName,
                sourceRecordKey,
                payloadJson,
                errorCode,
                errorMessage,
                stage,
                errorCode,
                retryable);
    }

    public int purgeOlderThanDays(int retentionDays) {
        return jdbcTemplate.update("""
                DELETE FROM recon.xocs_ingestion_error
                WHERE created_at < NOW() - (? * INTERVAL '1 day')
                """, retentionDays);
    }
}
