package com.recon.api.repository;

import com.recon.api.domain.ActivityRecordDto;
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
public class ActivityFeedRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<ActivityRecordDto> findActivity(String tenantId,
                                                String moduleKey,
                                                String sourceType,
                                                String actor,
                                                LocalDate fromDate,
                                                LocalDate toDate,
                                                int limit) {
        List<ActivityRecordDto> records = new ArrayList<>();
        records.addAll(fetchConfigurationActivity(actor, fromDate, toDate, limit));
        records.addAll(fetchOperationsActivity(tenantId, actor, fromDate, toDate, limit));
        records.addAll(fetchExceptionActivity(tenantId, actor, fromDate, toDate, limit));
        records.addAll(fetchSlaActivity(tenantId, actor, fromDate, toDate, limit));
        records.addAll(fetchAlertActivity(tenantId, actor, fromDate, toDate, limit));

        return records.stream()
                .filter(record -> sourceType == null || sourceType.isBlank()
                        || sourceType.equalsIgnoreCase(record.getSourceType()))
                .filter(record -> moduleKey == null || moduleKey.isBlank()
                        || moduleKey.equalsIgnoreCase(record.getModuleKey()))
                .sorted((left, right) -> right.getEventTimestamp().compareTo(left.getEventTimestamp()))
                .limit(Math.max(limit, 1))
                .toList();
    }

    private List<ActivityRecordDto> fetchConfigurationActivity(String actor,
                                                               LocalDate fromDate,
                                                               LocalDate toDate,
                                                               int limit) {
        StringBuilder sql = new StringBuilder("""
                select action,
                       config_key,
                       changed_by,
                       changed_at,
                       old_value,
                       new_value
                from recon.configuration_change_audit
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        appendActorFilter(sql, args, "changed_by", actor);
        appendDateFilters(sql, args, "changed_at", fromDate, toDate);
        sql.append(" order by changed_at desc limit ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), this::mapConfigurationRecord, args.toArray());
    }

    private ActivityRecordDto mapConfigurationRecord(ResultSet rs, int rowNum) throws SQLException {
        String action = rs.getString("action");
        String configKey = rs.getString("config_key");
        return ActivityRecordDto.builder()
                .sourceType("CONFIGURATION")
                .moduleKey("CONFIGURATIONS")
                .actionType(action)
                .actor(rs.getString("changed_by"))
                .title("Configuration " + action)
                .summary(buildConfigSummary(configKey, rs.getString("old_value"), rs.getString("new_value"), action))
                .referenceKey(configKey)
                .status(action)
                .eventTimestamp(toLocalDateTime(rs, "changed_at"))
                .build();
    }

    private List<ActivityRecordDto> fetchOperationsActivity(String tenantId,
                                                            String actor,
                                                            LocalDate fromDate,
                                                            LocalDate toDate,
                                                            int limit) {
        StringBuilder sql = new StringBuilder("""
                select module_id,
                       action_key,
                       requested_by,
                       transaction_key,
                       recon_view,
                       playbook_step_title,
                       result_status,
                       result_message,
                       created_at
                from recon.operations_action_audit
                where tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        appendActorFilter(sql, args, "requested_by", actor);
        appendDateFilters(sql, args, "created_at", fromDate, toDate);
        sql.append(" order by created_at desc limit ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), this::mapOperationsRecord, args.toArray());
    }

    private ActivityRecordDto mapOperationsRecord(ResultSet rs, int rowNum) throws SQLException {
        String moduleId = rs.getString("module_id");
        String actionKey = rs.getString("action_key");
        String transactionKey = rs.getString("transaction_key");
        String reconView = rs.getString("recon_view");
        String playbookStepTitle = rs.getString("playbook_step_title");
        String resultStatus = rs.getString("result_status");
        return ActivityRecordDto.builder()
                .sourceType("OPERATIONS")
                .moduleKey(reconView == null || reconView.isBlank() ? mapOperationsModule(moduleId) : reconView)
                .actionType(playbookStepTitle == null || playbookStepTitle.isBlank() ? actionKey : "PLAYBOOK_STEP_EXECUTED")
                .actor(rs.getString("requested_by"))
                .title(playbookStepTitle == null || playbookStepTitle.isBlank() ? "Operations action" : "Playbook action executed")
                .summary(buildOperationsSummary(
                        moduleId,
                        actionKey,
                        playbookStepTitle,
                        transactionKey,
                        rs.getString("result_message")))
                .referenceKey(transactionKey == null || transactionKey.isBlank() ? moduleId : transactionKey)
                .status(resultStatus)
                .eventTimestamp(toLocalDateTime(rs, "created_at"))
                .build();
    }

    private List<ActivityRecordDto> fetchExceptionActivity(String tenantId,
                                                           String actor,
                                                           LocalDate fromDate,
                                                           LocalDate toDate,
                                                           int limit) {
        List<ActivityRecordDto> records = new ArrayList<>();

        StringBuilder caseSql = new StringBuilder("""
                select transaction_key,
                       recon_view,
                       case_status,
                       reason_code,
                       assignee_username,
                       created_by,
                       updated_by,
                       created_at,
                       updated_at
                from recon.exception_cases
                where tenant_id = ?
                """);
        List<Object> caseArgs = new ArrayList<>();
        caseArgs.add(tenantId);
        appendActorFilter(caseSql, caseArgs, "coalesce(updated_by, created_by)", actor);
        appendDateFilters(caseSql, caseArgs, "greatest(created_at, updated_at)", fromDate, toDate);
        caseSql.append(" order by greatest(created_at, updated_at) desc limit ?");
        caseArgs.add(limit);
        records.addAll(jdbcTemplate.query(caseSql.toString(), this::mapExceptionCaseRecord, caseArgs.toArray()));

        StringBuilder commentSql = new StringBuilder("""
                select c.comment_text,
                       c.created_by,
                       c.created_at,
                       e.transaction_key,
                       e.recon_view
                from recon.exception_comments c
                join recon.exception_cases e
                  on e.id = c.case_id
                where e.tenant_id = ?
                """);
        List<Object> commentArgs = new ArrayList<>();
        commentArgs.add(tenantId);
        appendActorFilter(commentSql, commentArgs, "c.created_by", actor);
        appendDateFilters(commentSql, commentArgs, "c.created_at", fromDate, toDate);
        commentSql.append(" order by c.created_at desc limit ?");
        commentArgs.add(limit);
        records.addAll(jdbcTemplate.query(commentSql.toString(), this::mapExceptionCommentRecord, commentArgs.toArray()));

        return records;
    }

    private ActivityRecordDto mapExceptionCaseRecord(ResultSet rs, int rowNum) throws SQLException {
        LocalDateTime createdAt = toLocalDateTime(rs, "created_at");
        LocalDateTime updatedAt = toLocalDateTime(rs, "updated_at");
        boolean created = createdAt != null && updatedAt != null && createdAt.equals(updatedAt);
        String reconView = rs.getString("recon_view");
        String caseStatus = rs.getString("case_status");
        return ActivityRecordDto.builder()
                .sourceType("EXCEPTION")
                .moduleKey(reconView)
                .actionType(created ? "CREATED" : "UPDATED")
                .actor(created ? rs.getString("created_by") : rs.getString("updated_by"))
                .title(created ? "Exception case created" : "Exception case updated")
                .summary(buildExceptionSummary(rs.getString("transaction_key"),
                        caseStatus,
                        rs.getString("reason_code"),
                        rs.getString("assignee_username")))
                .referenceKey(rs.getString("transaction_key"))
                .status(caseStatus)
                .eventTimestamp(created ? createdAt : updatedAt)
                .build();
    }

    private ActivityRecordDto mapExceptionCommentRecord(ResultSet rs, int rowNum) throws SQLException {
        return ActivityRecordDto.builder()
                .sourceType("EXCEPTION")
                .moduleKey(rs.getString("recon_view"))
                .actionType("COMMENTED")
                .actor(rs.getString("created_by"))
                .title("Exception comment added")
                .summary(rs.getString("comment_text"))
                .referenceKey(rs.getString("transaction_key"))
                .status("COMMENTED")
                .eventTimestamp(toLocalDateTime(rs, "created_at"))
                .build();
    }

    private List<ActivityRecordDto> fetchAlertActivity(String tenantId,
                                                       String actor,
                                                       LocalDate fromDate,
                                                       LocalDate toDate,
                                                       int limit) {
        List<ActivityRecordDto> records = new ArrayList<>();

        StringBuilder ruleSql = new StringBuilder("""
                select rule_name,
                       recon_view,
                       metric_key,
                       severity,
                       active,
                       created_by,
                       updated_by,
                       created_at,
                       updated_at
                from recon.alert_rules
                where tenant_id = ?
                """);
        List<Object> ruleArgs = new ArrayList<>();
        ruleArgs.add(tenantId);
        appendActorFilter(ruleSql, ruleArgs, "coalesce(updated_by, created_by)", actor);
        appendDateFilters(ruleSql, ruleArgs, "greatest(created_at, updated_at)", fromDate, toDate);
        ruleSql.append(" order by greatest(created_at, updated_at) desc limit ?");
        ruleArgs.add(limit);
        records.addAll(jdbcTemplate.query(ruleSql.toString(), this::mapAlertRuleRecord, ruleArgs.toArray()));

        StringBuilder eventSql = new StringBuilder("""
                select rule_name,
                       recon_view,
                       event_message,
                       severity,
                       alert_status,
                       acknowledged_by,
                       acknowledged_at,
                       resolved_by,
                       resolved_at,
                       created_at,
                       updated_at
                from recon.alert_events
                where tenant_id = ?
                """);
        List<Object> eventArgs = new ArrayList<>();
        eventArgs.add(tenantId);
        if (actor != null && !actor.isBlank()) {
            eventSql.append(" and (coalesce(resolved_by, acknowledged_by, '') = ?)");
            eventArgs.add(actor);
        }
        appendDateFilters(eventSql, eventArgs, "greatest(created_at, updated_at)", fromDate, toDate);
        eventSql.append(" order by greatest(created_at, updated_at) desc limit ?");
        eventArgs.add(limit);
        records.addAll(jdbcTemplate.query(eventSql.toString(), this::mapAlertEventRecord, eventArgs.toArray()));

        return records;
    }

    private List<ActivityRecordDto> fetchSlaActivity(String tenantId,
                                                     String actor,
                                                     LocalDate fromDate,
                                                     LocalDate toDate,
                                                     int limit) {
        StringBuilder sql = new StringBuilder("""
                select recon_view,
                       severity,
                       target_minutes,
                       created_by,
                       updated_by,
                       created_at,
                       updated_at
                from recon.exception_sla_rules
                where tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        appendActorFilter(sql, args, "coalesce(updated_by, created_by)", actor);
        appendDateFilters(sql, args, "greatest(created_at, updated_at)", fromDate, toDate);
        sql.append(" order by greatest(created_at, updated_at) desc limit ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapSlaRuleRecord, args.toArray());
    }

    private ActivityRecordDto mapAlertRuleRecord(ResultSet rs, int rowNum) throws SQLException {
        LocalDateTime createdAt = toLocalDateTime(rs, "created_at");
        LocalDateTime updatedAt = toLocalDateTime(rs, "updated_at");
        boolean created = createdAt != null && updatedAt != null && createdAt.equals(updatedAt);
        String reconView = rs.getString("recon_view");
        return ActivityRecordDto.builder()
                .sourceType("ALERT")
                .moduleKey(reconView)
                .actionType(created ? "RULE_CREATED" : "RULE_UPDATED")
                .actor(created ? rs.getString("created_by") : rs.getString("updated_by"))
                .title(created ? "Alert rule created" : "Alert rule updated")
                .summary(rs.getString("rule_name") + " -> " + rs.getString("metric_key"))
                .referenceKey(rs.getString("rule_name"))
                .status(rs.getBoolean("active") ? "ACTIVE" : "INACTIVE")
                .eventTimestamp(created ? createdAt : updatedAt)
                .build();
    }

    private ActivityRecordDto mapAlertEventRecord(ResultSet rs, int rowNum) throws SQLException {
        String status = rs.getString("alert_status");
        String actionType = "EVENT_OPEN";
        String actor = null;
        LocalDateTime eventAt = toLocalDateTime(rs, "created_at");
        if ("ACKNOWLEDGED".equalsIgnoreCase(status) && rs.getTimestamp("acknowledged_at") != null) {
            actionType = "EVENT_ACKNOWLEDGED";
            actor = rs.getString("acknowledged_by");
            eventAt = toLocalDateTime(rs, "acknowledged_at");
        } else if ("RESOLVED".equalsIgnoreCase(status) && rs.getTimestamp("resolved_at") != null) {
            actionType = "EVENT_RESOLVED";
            actor = rs.getString("resolved_by");
            eventAt = toLocalDateTime(rs, "resolved_at");
        }
        return ActivityRecordDto.builder()
                .sourceType("ALERT")
                .moduleKey(rs.getString("recon_view"))
                .actionType(actionType)
                .actor(actor)
                .title("Alert event " + status.toLowerCase())
                .summary(rs.getString("rule_name") + " -> " + rs.getString("event_message"))
                .referenceKey(rs.getString("rule_name"))
                .status(status)
                .eventTimestamp(eventAt)
                .build();
    }

    private ActivityRecordDto mapSlaRuleRecord(ResultSet rs, int rowNum) throws SQLException {
        LocalDateTime createdAt = toLocalDateTime(rs, "created_at");
        LocalDateTime updatedAt = toLocalDateTime(rs, "updated_at");
        boolean created = createdAt != null && updatedAt != null && createdAt.equals(updatedAt);
        return ActivityRecordDto.builder()
                .sourceType("SLA")
                .moduleKey(rs.getString("recon_view"))
                .actionType(created ? "SLA_RULE_CREATED" : "SLA_RULE_UPDATED")
                .actor(created ? rs.getString("created_by") : rs.getString("updated_by"))
                .title(created ? "SLA rule created" : "SLA rule updated")
                .summary(rs.getString("severity") + " -> " + rs.getInt("target_minutes") + " minutes")
                .referenceKey(rs.getString("recon_view") + ":" + rs.getString("severity"))
                .status("ACTIVE")
                .eventTimestamp(created ? createdAt : updatedAt)
                .build();
    }

    private void appendActorFilter(StringBuilder sql, List<Object> args, String column, String actor) {
        if (actor != null && !actor.isBlank()) {
            sql.append(" and ").append(column).append(" = ?");
            args.add(actor);
        }
    }

    private void appendDateFilters(StringBuilder sql,
                                   List<Object> args,
                                   String column,
                                   LocalDate fromDate,
                                   LocalDate toDate) {
        if (fromDate != null) {
            sql.append(" and ").append(column).append(" >= ?");
            args.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            sql.append(" and ").append(column).append(" < ?");
            args.add(toDate.plusDays(1).atStartOfDay());
        }
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    private String buildConfigSummary(String configKey, String oldValue, String newValue, String action) {
        if ("DELETE".equalsIgnoreCase(action)) {
            return configKey + " cleared (previous value: " + safeValue(oldValue) + ")";
        }
        return configKey + " changed from " + safeValue(oldValue) + " to " + safeValue(newValue);
    }

    private String buildExceptionSummary(String transactionKey,
                                         String caseStatus,
                                         String reasonCode,
                                         String assigneeUsername) {
        StringBuilder summary = new StringBuilder(transactionKey)
                .append(" -> ")
                .append(caseStatus);
        if (reasonCode != null && !reasonCode.isBlank()) {
            summary.append(" / ").append(reasonCode);
        }
        if (assigneeUsername != null && !assigneeUsername.isBlank()) {
            summary.append(" / ").append(assigneeUsername);
        }
        return summary.toString();
    }

    private String buildOperationsSummary(String moduleId,
                                          String actionKey,
                                          String playbookStepTitle,
                                          String transactionKey,
                                          String resultMessage) {
        StringBuilder summary = new StringBuilder(moduleId)
                .append(" -> ")
                .append(actionKey);
        if (playbookStepTitle != null && !playbookStepTitle.isBlank()) {
            summary.append(" / ").append(playbookStepTitle);
        }
        if (transactionKey != null && !transactionKey.isBlank()) {
            summary.append(" / case ").append(transactionKey);
        }
        summary.append(" (").append(safeValue(resultMessage)).append(")");
        return summary.toString();
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String mapOperationsModule(String moduleId) {
        return switch (moduleId) {
            case "xstore-publisher", "sim-poller" -> "XSTORE_SIM";
            case "siocs-cloud-connector" -> "XSTORE_SIOCS";
            case "mfcs-rds-connector" -> "SIOCS_MFCS";
            case "xocs-cloud-connector" -> "XSTORE_XOCS";
            default -> "OPERATIONS";
        };
    }
}
