package com.recon.cloud.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CloudErrorRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String tenantId,
                     String sourceName,
                     String sourceRecordKey,
                     String payloadJson,
                     String errorType,
                     String errorMessage) {
        String sql = """
                INSERT INTO recon.cloud_ingestion_error (
                    tenant_id, source_name, source_record_key,
                    payload_json, error_type, error_message,
                    retry_count, created_at, updated_at
                ) VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, 0, NOW(), NOW())
                """;
        jdbcTemplate.update(sql, tenantId, sourceName, sourceRecordKey,
                payloadJson, errorType, errorMessage);
    }

    public long countAll() {
        String sql = "SELECT COUNT(*) FROM recon.cloud_ingestion_error";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
