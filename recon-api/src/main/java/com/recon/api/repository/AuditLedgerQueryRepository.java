package com.recon.api.repository;

import com.recon.api.domain.AuditLedgerRecordDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AuditLedgerQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<AuditLedgerRecordDto> findEntries(String tenantId,
                                                  String moduleKey,
                                                  String sourceType,
                                                  String actor,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  boolean includeArchived,
                                                  int limit) {
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();

        sql.append(buildSelect("recon.audit_ledger_entries", false, tenantId, moduleKey, sourceType, actor, fromDate, toDate, args));
        if (includeArchived) {
            sql.append(" union all ");
            sql.append(buildSelect("recon.audit_ledger_archive_entries", true, tenantId, moduleKey, sourceType, actor, fromDate, toDate, args));
        }
        sql.append(" order by event_timestamp desc limit ?");
        args.add(Math.max(limit, 1));

        return jdbcTemplate.query(sql.toString(), this::mapRecord, args.toArray());
    }

    public long countEligibleForArchive(String tenantId, LocalDateTime archiveBefore) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from recon.audit_ledger_entries e
                left join recon.audit_ledger_archive_entries a
                  on a.original_entry_id = e.id
                where e.tenant_id = ?
                  and e.event_at < ?
                  and a.original_entry_id is null
                """, Long.class, tenantId, archiveBefore);
    }

    private String buildSelect(String table,
                               boolean archived,
                               String tenantId,
                               String moduleKey,
                               String sourceType,
                               String actor,
                               LocalDate fromDate,
                               LocalDate toDate,
                               List<Object> args) {
        StringBuilder sql = new StringBuilder("""
                select source_type,
                       module_key,
                       entity_type,
                       entity_key,
                       action_type,
                       actor,
                       title,
                       summary,
                       reason,
                       status,
                       reference_key,
                       control_family,
                       evidence_tags,
                       before_state,
                       after_state,
                       metadata_json,
                       event_hash,
                       event_at as event_timestamp,
                """);
        sql.append(archived ? "true" : "false");
        sql.append("""
                 as archived
                from
                """).append(table).append("""
                 where tenant_id = ?
                """);
        args.add(tenantId);

        appendEqualsIgnoreCase(sql, args, "module_key", moduleKey);
        appendEqualsIgnoreCase(sql, args, "source_type", sourceType);
        appendEqualsIgnoreCase(sql, args, "actor", actor);
        appendDateFilters(sql, args, fromDate, toDate);

        return sql.toString();
    }

    private AuditLedgerRecordDto mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return AuditLedgerRecordDto.builder()
                .sourceType(rs.getString("source_type"))
                .moduleKey(rs.getString("module_key"))
                .entityType(rs.getString("entity_type"))
                .entityKey(rs.getString("entity_key"))
                .actionType(rs.getString("action_type"))
                .actor(rs.getString("actor"))
                .title(rs.getString("title"))
                .summary(rs.getString("summary"))
                .reason(rs.getString("reason"))
                .status(rs.getString("status"))
                .referenceKey(rs.getString("reference_key"))
                .controlFamily(rs.getString("control_family"))
                .evidenceTags(rs.getString("evidence_tags"))
                .beforeState(rs.getString("before_state"))
                .afterState(rs.getString("after_state"))
                .metadataJson(rs.getString("metadata_json"))
                .eventHash(rs.getString("event_hash"))
                .archived(rs.getBoolean("archived"))
                .eventTimestamp(toLocalDateTime(rs, "event_timestamp"))
                .build();
    }

    private void appendEqualsIgnoreCase(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" and upper(").append(column).append(") = ?");
        args.add(value.trim().toUpperCase());
    }

    private void appendDateFilters(StringBuilder sql,
                                   List<Object> args,
                                   LocalDate fromDate,
                                   LocalDate toDate) {
        if (fromDate != null) {
            sql.append(" and event_at >= ?");
            args.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            sql.append(" and event_at < ?");
            args.add(toDate.plusDays(1).atStartOfDay());
        }
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }
}
