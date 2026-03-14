package com.recon.cloud.repository;

import com.recon.cloud.domain.CloudApiLineItem;
import com.recon.cloud.domain.CloudApiTransaction;
import com.recon.cloud.domain.CloudIngestionTransactionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CloudTransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public void upsertTransactionRows(String tenantId,
                                      String sourceName,
                                      String sourceCursor,
                                      long rawPayloadId,
                                      List<CloudApiTransaction> records) {
        List<CloudIngestionTransactionRow> rows = flatten(
                tenantId, sourceName, sourceCursor, rawPayloadId, records);
        if (rows.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO recon.cloud_ingestion_transaction (
                    dedup_key, tenant_id, source_name, source_record_key, source_cursor,
                    external_id, transaction_extended_id, request_id, store_id,
                    transaction_date_time, update_date_time, type, processing_status,
                    line_id, item_id, quantity, unit_of_measure, raw_payload_id,
                    ingestion_status, retry_count, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY', 0, NOW(), NOW())
                ON CONFLICT (dedup_key) DO UPDATE SET
                    source_record_key = EXCLUDED.source_record_key,
                    source_cursor = EXCLUDED.source_cursor,
                    request_id = EXCLUDED.request_id,
                    store_id = EXCLUDED.store_id,
                    transaction_date_time = EXCLUDED.transaction_date_time,
                    processing_status = EXCLUDED.processing_status,
                    line_id = EXCLUDED.line_id,
                    quantity = EXCLUDED.quantity,
                    unit_of_measure = EXCLUDED.unit_of_measure,
                    raw_payload_id = EXCLUDED.raw_payload_id,
                    ingestion_status = CASE
                        WHEN recon.cloud_ingestion_transaction.ingestion_status = 'PUBLISHED'
                            THEN recon.cloud_ingestion_transaction.ingestion_status
                        ELSE 'READY'
                    END,
                    updated_at = NOW()
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                CloudIngestionTransactionRow row = rows.get(i);
                ps.setString(1, row.getDedupKey());
                ps.setString(2, row.getTenantId());
                ps.setString(3, row.getSourceName());
                ps.setString(4, row.getSourceRecordKey());
                ps.setString(5, row.getSourceCursor());
                ps.setString(6, row.getExternalId());
                ps.setString(7, row.getTransactionExtendedId());
                ps.setObject(8, row.getRequestId());
                ps.setString(9, row.getStoreId());
                ps.setTimestamp(10, row.getTransactionDateTime());
                ps.setTimestamp(11, row.getUpdateDateTime());
                ps.setObject(12, row.getType());
                ps.setObject(13, row.getProcessingStatus());
                ps.setObject(14, row.getLineId());
                ps.setString(15, row.getItemId());
                ps.setBigDecimal(16, row.getQuantity());
                ps.setString(17, row.getUnitOfMeasure());
                ps.setLong(18, row.getRawPayloadId());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    public List<CloudIngestionTransactionRow> claimBatch(String workerId,
                                                         int batchSize,
                                                         int maxRetries) {
        String sql = """
                WITH claimed AS (
                    SELECT id
                    FROM recon.cloud_ingestion_transaction
                    WHERE ingestion_status IN ('READY', 'FAILED')
                      AND retry_count < ?
                    ORDER BY COALESCE(update_date_time, transaction_date_time), id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE recon.cloud_ingestion_transaction t
                SET ingestion_status = 'PROCESSING',
                    locked_by = ?,
                    locked_at = NOW(),
                    updated_at = NOW()
                FROM claimed
                WHERE t.id = claimed.id
                RETURNING t.id, t.tenant_id, t.source_name, t.source_record_key,
                          t.dedup_key, t.source_cursor, t.external_id, t.transaction_extended_id,
                          t.request_id, t.store_id, t.transaction_date_time,
                          t.update_date_time, t.type, t.processing_status,
                          t.line_id, t.item_id, t.quantity, t.unit_of_measure,
                          t.raw_payload_id, t.ingestion_status, t.retry_count,
                          t.last_error_message
                """;
        return jdbcTemplate.query(sql, (rs, rn) ->
                        CloudIngestionTransactionRow.builder()
                                .id(rs.getLong("id"))
                                .dedupKey(rs.getString("dedup_key"))
                                .tenantId(rs.getString("tenant_id"))
                                .sourceName(rs.getString("source_name"))
                                .sourceRecordKey(rs.getString("source_record_key"))
                                .sourceCursor(rs.getString("source_cursor"))
                                .externalId(rs.getString("external_id"))
                                .transactionExtendedId(
                                        rs.getString("transaction_extended_id"))
                                .requestId(rs.getObject("request_id", Long.class))
                                .storeId(rs.getString("store_id"))
                                .transactionDateTime(
                                        rs.getTimestamp("transaction_date_time"))
                                .updateDateTime(
                                        rs.getTimestamp("update_date_time"))
                                .type(rs.getObject("type", Integer.class))
                                .processingStatus(
                                        rs.getObject("processing_status", Integer.class))
                                .lineId(rs.getObject("line_id", Long.class))
                                .itemId(rs.getString("item_id"))
                                .quantity(rs.getBigDecimal("quantity"))
                                .unitOfMeasure(rs.getString("unit_of_measure"))
                                .rawPayloadId(rs.getLong("raw_payload_id"))
                                .ingestionStatus(rs.getString("ingestion_status"))
                                .retryCount(rs.getInt("retry_count"))
                                .lastErrorMessage(rs.getString("last_error_message"))
                                .build(),
                maxRetries, batchSize, workerId);
    }

    public int releaseStaleClaims(int lockTimeoutMinutes) {
        String sql = """
                UPDATE recon.cloud_ingestion_transaction
                SET ingestion_status = 'READY',
                    locked_by = NULL,
                    locked_at = NULL,
                    updated_at = NOW()
                WHERE ingestion_status = 'PROCESSING'
                  AND locked_at < NOW() - (? * INTERVAL '1 minute')
                """;
        return jdbcTemplate.update(sql, lockTimeoutMinutes);
    }

    public Map<String, Long> countByStatus() {
        String sql = """
                SELECT ingestion_status, COUNT(*)
                FROM recon.cloud_ingestion_transaction
                GROUP BY ingestion_status
                ORDER BY ingestion_status
                """;
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                counts.put(rs.getString(1), rs.getLong(2)));
        return counts;
    }

    public Timestamp oldestTimestampForStatus(String status) {
        String sql = """
                SELECT MIN(COALESCE(update_date_time, transaction_date_time, created_at))
                FROM recon.cloud_ingestion_transaction
                WHERE ingestion_status = ?
                """;
        return jdbcTemplate.queryForObject(sql, Timestamp.class, status);
    }

    public int requeueStatus(String status) {
        String sql = """
                UPDATE recon.cloud_ingestion_transaction
                SET ingestion_status = 'READY',
                    locked_by = NULL,
                    locked_at = NULL,
                    last_error_message = NULL,
                    updated_at = NOW()
                WHERE ingestion_status = ?
                """;
        return jdbcTemplate.update(sql, status);
    }

    public void markPublished(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String sql = """
                UPDATE recon.cloud_ingestion_transaction
                SET ingestion_status = 'PUBLISHED',
                    published_at = NOW(),
                    locked_by = NULL,
                    locked_at = NULL,
                    last_error_message = NULL,
                    updated_at = NOW()
                WHERE id = ANY (?)
                """;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setArray(1, connection.createArrayOf("bigint", ids.toArray()));
            return ps;
        });
    }

    public void markFailed(List<Long> ids, String errorMessage, int maxRetries) {
        if (ids.isEmpty()) {
            return;
        }
        String sql = """
                UPDATE recon.cloud_ingestion_transaction
                SET retry_count = retry_count + 1,
                    ingestion_status = CASE
                        WHEN retry_count + 1 >= ? THEN 'DLQ'
                        ELSE 'FAILED'
                    END,
                    locked_by = NULL,
                    locked_at = NULL,
                    last_error_message = ?,
                    updated_at = NOW()
                WHERE id = ANY (?)
                """;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, maxRetries);
            ps.setString(2, errorMessage);
            ps.setArray(3, connection.createArrayOf("bigint", ids.toArray()));
            return ps;
        });
    }

    private List<CloudIngestionTransactionRow> flatten(String tenantId,
                                                       String sourceName,
                                                       String sourceCursor,
                                                       long rawPayloadId,
                                                       List<CloudApiTransaction> records) {
        List<CloudIngestionTransactionRow> rows = new ArrayList<>();
        for (CloudApiTransaction record : records) {
            List<CloudApiLineItem> lineItems = record.getLineItems();
            if (lineItems == null || lineItems.isEmpty()) {
                lineItems = List.of(CloudApiLineItem.builder().build());
            }
            for (CloudApiLineItem lineItem : lineItems) {
                rows.add(CloudIngestionTransactionRow.builder()
                        .dedupKey(buildDedupKey(tenantId, sourceName, record, lineItem))
                        .tenantId(tenantId)
                        .sourceName(sourceName)
                        .sourceRecordKey(record.getSourceRecordKey())
                        .sourceCursor(sourceCursor)
                        .externalId(record.getExternalId())
                        .transactionExtendedId(lineItem.getTransactionExtendedId())
                        .requestId(record.getRequestId())
                        .storeId(record.getStoreId())
                        .transactionDateTime(toTimestamp(record.getTransactionDateTime()))
                        .updateDateTime(toTimestamp(record.getUpdateDateTime()))
                        .type(lineItem.getType() != null ? lineItem.getType() : record.getType())
                        .processingStatus(lineItem.getProcessingStatus() != null
                                ? lineItem.getProcessingStatus()
                                : record.getProcessingStatus())
                        .lineId(lineItem.getId())
                        .itemId(lineItem.getItemId())
                        .quantity(lineItem.getQuantity())
                        .unitOfMeasure(lineItem.getUnitOfMeasure())
                        .rawPayloadId(rawPayloadId)
                        .build());
            }
        }
        return rows;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String buildDedupKey(String tenantId,
                                 String sourceName,
                                 CloudApiTransaction record,
                                 CloudApiLineItem lineItem) {
        String updateTime = record.getUpdateDateTime() == null
                ? ""
                : record.getUpdateDateTime().toString();
        String lineIdentity = lineItem.getId() != null
                ? String.valueOf(lineItem.getId())
                : safe(lineItem.getItemId()) + "|" +
                safe(lineItem.getTransactionExtendedId()) + "|" +
                safe(lineItem.getType());
        return String.join("|",
                safe(tenantId),
                safe(sourceName),
                safe(record.getExternalId()),
                safe(updateTime),
                safe(lineIdentity));
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
