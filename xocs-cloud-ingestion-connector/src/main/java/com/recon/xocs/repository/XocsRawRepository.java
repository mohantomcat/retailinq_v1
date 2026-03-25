package com.recon.xocs.repository;

import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;

@Repository
@RequiredArgsConstructor
public class XocsRawRepository {

    private final JdbcTemplate jdbcTemplate;

    public long insert(String tenantId,
                       String connectorName,
                       String sourceName,
                       java.sql.Timestamp requestFromUpdateTs,
                       Long requestLastCursorId,
                       Integer requestLimit,
                       String payloadJson,
                       int responseRecordCount,
                       Integer httpStatus) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO recon.xocs_ingestion_raw (
                        tenant_id, connector_name, source_name, request_from_update_ts,
                        request_last_cursor_id, request_limit, payload_json,
                        response_record_count, http_status, content_hash
                    ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS TEXT), ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, tenantId);
            ps.setString(2, connectorName);
            ps.setString(3, sourceName);
            ps.setTimestamp(4, requestFromUpdateTs);
            ps.setObject(5, requestLastCursorId);
            ps.setObject(6, requestLimit);
            ps.setString(7, payloadJson);
            ps.setInt(8, responseRecordCount);
            ps.setObject(9, httpStatus);
            ps.setString(10, DigestUtils.sha256Hex(payloadJson));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int purgeOlderThanDays(int retentionDays) {
        return jdbcTemplate.update("""
                DELETE FROM recon.xocs_ingestion_raw
                WHERE created_at < NOW() - (? * INTERVAL '1 day')
                """, retentionDays);
    }
}
