package com.recon.cloud.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Repository
@RequiredArgsConstructor
public class CloudRawRepository {

    private final JdbcTemplate jdbcTemplate;

    public long insert(String tenantId,
                       String sourceName,
                       String sourceRecordKey,
                       String sourceCursor,
                       String payloadJson,
                       String requestId) {
        String sql = """
                INSERT INTO recon.siocs_ingestion_raw (
                    tenant_id, source_name, source_record_key,
                    source_cursor, payload_hash, payload_json,
                    fetched_at, request_id, created_at
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, NOW())
                RETURNING id
                """;

        String payloadHash = sha256(payloadJson);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, tenantId);
            ps.setString(2, sourceName);
            ps.setString(3, sourceRecordKey);
            ps.setString(4, sourceCursor);
            ps.setString(5, payloadHash);
            ps.setString(6, payloadJson);
            ps.setTimestamp(7, java.sql.Timestamp.from(Instant.now()));
            ps.setString(8, requestId);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to insert raw payload");
        }
        return key.longValue();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash payload", e);
        }
    }

    public int purgeOlderThanDays(int days) {
        String sql = """
                DELETE FROM recon.siocs_ingestion_raw
                WHERE fetched_at < NOW() - (? * INTERVAL '1 day')
                """;
        return jdbcTemplate.update(sql, days);
    }
}
