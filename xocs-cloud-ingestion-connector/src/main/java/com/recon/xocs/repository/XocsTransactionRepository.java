package com.recon.xocs.repository;

import com.recon.xocs.domain.XocsApiTransaction;
import com.recon.xocs.domain.XocsStagedTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class XocsTransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public long upsertTransaction(String tenantId,
                                  String sourceName,
                                  long rawPayloadId,
                                  XocsApiTransaction record) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO recon.xocs_ingestion_transaction (
                    dedup_key, tenant_id, source_name, organization_id, rtl_loc_id, business_date,
                    wkstn_id, trans_seq, external_id, transaction_key, begin_datetime,
                    end_datetime, session_id, operator_party_id, trans_typcode, trans_statcode,
                    trans_total, trans_subtotal, trans_taxtotal, trans_roundtotal,
                    line_count, distinct_item_count, total_item_qty, sum_extended_amt,
                    sum_net_amt, sum_gross_amt, source_update_date, raw_payload_id,
                    ingestion_status, retry_count, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY', 0, NOW(), NOW())
                ON CONFLICT (organization_id, rtl_loc_id, business_date, wkstn_id, trans_seq) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    source_name = EXCLUDED.source_name,
                    external_id = EXCLUDED.external_id,
                    transaction_key = EXCLUDED.transaction_key,
                    begin_datetime = EXCLUDED.begin_datetime,
                    end_datetime = EXCLUDED.end_datetime,
                    session_id = EXCLUDED.session_id,
                    operator_party_id = EXCLUDED.operator_party_id,
                    trans_typcode = EXCLUDED.trans_typcode,
                    trans_statcode = EXCLUDED.trans_statcode,
                    trans_total = EXCLUDED.trans_total,
                    trans_subtotal = EXCLUDED.trans_subtotal,
                    trans_taxtotal = EXCLUDED.trans_taxtotal,
                    trans_roundtotal = EXCLUDED.trans_roundtotal,
                    line_count = EXCLUDED.line_count,
                    distinct_item_count = EXCLUDED.distinct_item_count,
                    total_item_qty = EXCLUDED.total_item_qty,
                    sum_extended_amt = EXCLUDED.sum_extended_amt,
                    sum_net_amt = EXCLUDED.sum_net_amt,
                    sum_gross_amt = EXCLUDED.sum_gross_amt,
                    source_update_date = EXCLUDED.source_update_date,
                    raw_payload_id = EXCLUDED.raw_payload_id,
                    ingestion_status = CASE
                        WHEN recon.xocs_ingestion_transaction.ingestion_status = 'PUBLISHED'
                            THEN recon.xocs_ingestion_transaction.ingestion_status
                        ELSE 'READY'
                    END,
                    updated_at = NOW()
                RETURNING id
                """, Long.class,
                buildDedupKey(tenantId, sourceName, record),
                tenantId,
                sourceName,
                record.getOrganizationId(),
                record.getRtlLocId(),
                record.getBusinessDate(),
                record.getWkstnId(),
                record.getTransSeq(),
                record.getExternalId(),
                record.getTransactionKey(),
                toTimestamp(record.getBeginDatetime()),
                toTimestamp(record.getEndDatetime()),
                record.getSessionId(),
                record.getOperatorPartyId(),
                record.getTransTypcode(),
                record.getTransStatcode(),
                record.getTransTotal(),
                record.getTransSubtotal(),
                record.getTransTaxtotal(),
                record.getTransRoundtotal(),
                record.getLineCount(),
                record.getDistinctItemCount(),
                record.getTotalItemQty(),
                record.getSumExtendedAmt(),
                record.getSumNetAmt(),
                record.getSumGrossAmt(),
                toTimestamp(record.getUpdateDateTime()),
                rawPayloadId);
    }

    public List<XocsStagedTransaction> claimBatch(String workerId, int batchSize, int maxRetries) {
        return jdbcTemplate.query("""
                WITH claimed AS (
                    SELECT id
                    FROM recon.xocs_ingestion_transaction
                    WHERE ingestion_status IN ('READY', 'FAILED')
                      AND retry_count < ?
                    ORDER BY COALESCE(source_update_date, updated_at, created_at), trans_seq, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE recon.xocs_ingestion_transaction t
                SET ingestion_status = 'PROCESSING',
                    claimed_by = ?,
                    claimed_at = NOW(),
                    updated_at = NOW()
                FROM claimed
                WHERE t.id = claimed.id
                RETURNING t.id, t.tenant_id, t.source_name, t.organization_id, t.rtl_loc_id,
                          t.business_date, t.wkstn_id, t.trans_seq, t.external_id, t.transaction_key,
                          t.begin_datetime, t.end_datetime, t.session_id, t.operator_party_id,
                          t.trans_typcode, t.trans_statcode, t.trans_total, t.trans_subtotal,
                          t.trans_taxtotal, t.trans_roundtotal, t.line_count, t.distinct_item_count,
                          t.total_item_qty, t.sum_extended_amt, t.sum_net_amt, t.sum_gross_amt,
                          t.source_update_date, t.raw_payload_id, t.ingestion_status, t.retry_count,
                          t.last_error_message
                """, (rs, rn) -> XocsStagedTransaction.builder()
                .id(rs.getLong("id"))
                .tenantId(rs.getString("tenant_id"))
                .sourceName(rs.getString("source_name"))
                .organizationId(rs.getLong("organization_id"))
                .rtlLocId(rs.getLong("rtl_loc_id"))
                .businessDate(rs.getObject("business_date", java.time.LocalDate.class))
                .wkstnId(rs.getLong("wkstn_id"))
                .transSeq(rs.getLong("trans_seq"))
                .externalId(rs.getString("external_id"))
                .transactionKey(rs.getString("transaction_key"))
                .beginDatetime(rs.getTimestamp("begin_datetime"))
                .endDatetime(rs.getTimestamp("end_datetime"))
                .sessionId(rs.getObject("session_id", Long.class))
                .operatorPartyId(rs.getObject("operator_party_id", Long.class))
                .transTypcode(rs.getString("trans_typcode"))
                .transStatcode(rs.getString("trans_statcode"))
                .transTotal(rs.getBigDecimal("trans_total"))
                .transSubtotal(rs.getBigDecimal("trans_subtotal"))
                .transTaxtotal(rs.getBigDecimal("trans_taxtotal"))
                .transRoundtotal(rs.getBigDecimal("trans_roundtotal"))
                .lineCount(rs.getObject("line_count", Integer.class))
                .distinctItemCount(rs.getObject("distinct_item_count", Integer.class))
                .totalItemQty(rs.getBigDecimal("total_item_qty"))
                .sumExtendedAmt(rs.getBigDecimal("sum_extended_amt"))
                .sumNetAmt(rs.getBigDecimal("sum_net_amt"))
                .sumGrossAmt(rs.getBigDecimal("sum_gross_amt"))
                .sourceUpdateDate(rs.getTimestamp("source_update_date"))
                .rawPayloadId(rs.getLong("raw_payload_id"))
                .ingestionStatus(rs.getString("ingestion_status"))
                .retryCount(rs.getInt("retry_count"))
                .lastErrorMessage(rs.getString("last_error_message"))
                .build(), maxRetries, batchSize, workerId);
    }

    public int releaseStaleClaims(int timeoutMinutes) {
        return jdbcTemplate.update("""
                UPDATE recon.xocs_ingestion_transaction
                SET ingestion_status = 'READY',
                    claimed_by = NULL,
                    claimed_at = NULL,
                    updated_at = NOW()
                WHERE ingestion_status = 'PROCESSING'
                  AND claimed_at < NOW() - (? * INTERVAL '1 minute')
                """, timeoutMinutes);
    }

    public void markPublished(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    UPDATE recon.xocs_ingestion_transaction
                    SET ingestion_status = 'PUBLISHED',
                        published_at = NOW(),
                        claimed_by = NULL,
                        claimed_at = NULL,
                        last_error_message = NULL,
                        updated_at = NOW()
                    WHERE id = ANY (?)
                    """);
            ps.setArray(1, connection.createArrayOf("bigint", ids.toArray()));
            return ps;
        });
    }

    public void markFailed(List<Long> ids, String errorMessage, int maxRetries) {
        if (ids.isEmpty()) {
            return;
        }
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    UPDATE recon.xocs_ingestion_transaction
                    SET retry_count = retry_count + 1,
                        ingestion_status = CASE
                            WHEN retry_count + 1 >= ? THEN 'DLQ'
                            ELSE 'FAILED'
                        END,
                        claimed_by = NULL,
                        claimed_at = NULL,
                        last_error_message = ?,
                        updated_at = NOW()
                    WHERE id = ANY (?)
                    """);
            ps.setInt(1, maxRetries);
            ps.setString(2, errorMessage);
            ps.setArray(3, connection.createArrayOf("bigint", ids.toArray()));
            return ps;
        });
    }

    public int purgeOlderThanDays(int retentionDays) {
        return jdbcTemplate.update("""
                DELETE FROM recon.xocs_ingestion_transaction
                WHERE business_date < CURRENT_DATE - (? * INTERVAL '1 day')
                """, retentionDays);
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new HashMap<>();
        jdbcTemplate.query("""
                SELECT ingestion_status, COUNT(*) AS cnt
                FROM recon.xocs_ingestion_transaction
                GROUP BY ingestion_status
                """, (rs, rn) -> {
            counts.put(rs.getString("ingestion_status"), rs.getLong("cnt"));
            return null;
        });
        return counts;
    }

    public Timestamp oldestTimestampForStatus(String status) {
        List<Timestamp> rows = jdbcTemplate.query("""
                SELECT MIN(COALESCE(source_update_date, updated_at, created_at)) AS oldest
                FROM recon.xocs_ingestion_transaction
                WHERE ingestion_status = ?
                """, (rs, rn) -> rs.getTimestamp("oldest"), status);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int requeueStatus(String status) {
        return jdbcTemplate.update("""
                UPDATE recon.xocs_ingestion_transaction
                SET ingestion_status = 'READY',
                    claimed_by = NULL,
                    claimed_at = NULL,
                    updated_at = NOW()
                WHERE ingestion_status = ?
                """, status);
    }

    public int replayWindow(LocalDate fromBusinessDate,
                            LocalDate toBusinessDate,
                            Long rtlLocId,
                            Long wkstnId) {
        StringBuilder sql = new StringBuilder("""
                UPDATE recon.xocs_ingestion_transaction
                SET ingestion_status = 'READY',
                    claimed_by = NULL,
                    claimed_at = NULL,
                    last_error_message = NULL,
                    updated_at = NOW()
                WHERE ingestion_status IN ('PUBLISHED', 'FAILED', 'DLQ')
                  AND business_date >= ?
                  AND business_date <= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(fromBusinessDate);
        args.add(toBusinessDate);
        if (rtlLocId != null) {
            sql.append(" AND rtl_loc_id = ?");
            args.add(rtlLocId);
        }
        if (wkstnId != null) {
            sql.append(" AND wkstn_id = ?");
            args.add(wkstnId);
        }
        return jdbcTemplate.update(sql.toString(), args.toArray());
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String buildDedupKey(String tenantId, String sourceName, XocsApiTransaction record) {
        String updateTime = record.getUpdateDateTime() == null ? "" : record.getUpdateDateTime().toString();
        return String.join("|",
                tenantId == null ? "" : tenantId,
                sourceName == null ? "" : sourceName,
                record.getTransactionKey() == null ? "" : record.getTransactionKey(),
                updateTime);
    }
}
