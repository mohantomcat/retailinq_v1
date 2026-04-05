package com.recon.api.repository;

import com.recon.api.domain.IntegrationConnectorDto;
import com.recon.api.domain.IntegrationErrorQueueItemDto;
import com.recon.api.domain.IntegrationFlowDto;
import com.recon.api.domain.IntegrationHubSummaryDto;
import com.recon.api.domain.IntegrationMessageTraceDetailDto;
import com.recon.api.domain.IntegrationMessageTraceItemDto;
import com.recon.api.domain.IntegrationReplayRequestDto;
import com.recon.api.domain.IntegrationRunDto;
import com.recon.api.domain.SaveIntegrationReplayRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IntegrationHubQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public IntegrationHubSummaryDto getSummary(String tenantId, Collection<String> connectorKeys) {
        if (connectorKeys == null || connectorKeys.isEmpty()) {
            return IntegrationHubSummaryDto.builder().build();
        }
        return IntegrationHubSummaryDto.builder()
                .activeConnectors(count(withConnectorKeyFilter(
                        "select count(*) from recon.integration_connector_definition where tenant_id = ? and enabled = true",
                        connectorKeys,
                        "connector_key"), argsWithTenantAndConnectorKeys(tenantId, connectorKeys)))
                .activeFlows(count(withFlowConnectorFilter(
                        """
                                select count(*)
                                from recon.integration_flow_definition fd
                                join recon.integration_connector_definition cd
                                  on cd.id = fd.connector_definition_id
                                where fd.tenant_id = ?
                                  and fd.enabled = true
                                """,
                        connectorKeys), argsWithTenantAndConnectorKeys(tenantId, connectorKeys)))
                .runningRuns(count(withConnectorKeyFilter(
                        "select count(*) from recon.integration_run where tenant_id = ? and run_status in ('RUNNING', 'QUEUED')",
                        connectorKeys,
                        "connector_key"), argsWithTenantAndConnectorKeys(tenantId, connectorKeys)))
                .failedRunsLast24Hours(count(withConnectorKeyFilter("""
                        select count(*) from recon.integration_run
                        where tenant_id = ?
                          and run_status = 'FAILED'
                          and started_at >= now() - interval '24 hour'
                        """, connectorKeys, "connector_key"), argsWithTenantAndConnectorKeys(tenantId, connectorKeys)))
                .openErrors(count(withConnectorKeyFilter(
                        "select count(*) from recon.integration_error_queue where tenant_id = ? and error_status = 'OPEN'",
                        connectorKeys,
                        "connector_key"), argsWithTenantAndConnectorKeys(tenantId, connectorKeys)))
                .pendingReplayRequests(count(withConnectorKeyFilter(
                        "select count(*) from recon.integration_replay_request where tenant_id = ? and replay_status in ('REQUESTED', 'ACCEPTED', 'RUNNING')",
                        connectorKeys,
                        "connector_key"), argsWithTenantAndConnectorKeys(tenantId, connectorKeys)))
                .publishedMessagesLast24Hours(count(withConnectorKeyFilter("""
                        select count(*) from recon.integration_message
                        where tenant_id = ?
                          and message_status = 'PUBLISHED'
                          and created_at >= now() - interval '24 hour'
                        """, connectorKeys, "connector_key"), argsWithTenantAndConnectorKeys(tenantId, connectorKeys)))
                .build();
    }

    public List<IntegrationConnectorDto> getConnectors(String tenantId) {
        return jdbcTemplate.query("""
                select cd.connector_key,
                       cd.connector_label,
                       cd.connector_type,
                       cd.source_system,
                       cd.target_system,
                       cd.module_key,
                       cd.enabled,
                       lr.run_status as latest_run_status,
                       lr.started_at as latest_run_started_at,
                       lr.completed_at as latest_run_completed_at,
                       lp.last_published_at,
                       coalesce(err.open_error_count, 0) as open_error_count,
                       coalesce(pub.published_count, 0) as published_count,
                       coalesce(fail.failed_count, 0) as failed_count
                from recon.integration_connector_definition cd
                left join lateral (
                    select r.run_status, r.started_at, r.completed_at
                    from recon.integration_run r
                    where r.tenant_id = cd.tenant_id
                      and r.connector_key = cd.connector_key
                    order by r.started_at desc nulls last, r.created_at desc
                    limit 1
                ) lr on true
                left join lateral (
                    select max(m.created_at) as last_published_at
                    from recon.integration_message m
                    where m.tenant_id = cd.tenant_id
                      and m.connector_key = cd.connector_key
                      and m.message_status = 'PUBLISHED'
                ) lp on true
                left join (
                    select connector_key, count(*) as open_error_count
                    from recon.integration_error_queue
                    where tenant_id = ?
                      and error_status = 'OPEN'
                    group by connector_key
                ) err on err.connector_key = cd.connector_key
                left join (
                    select connector_key, count(*) as published_count
                    from recon.integration_message
                    where tenant_id = ?
                      and message_status = 'PUBLISHED'
                      and created_at >= now() - interval '24 hour'
                    group by connector_key
                ) pub on pub.connector_key = cd.connector_key
                left join (
                    select connector_key, count(*) as failed_count
                    from recon.integration_message
                    where tenant_id = ?
                      and message_status = 'FAILED'
                      and created_at >= now() - interval '24 hour'
                    group by connector_key
                ) fail on fail.connector_key = cd.connector_key
                where cd.tenant_id = ?
                order by cd.connector_label
                """, (rs, rowNum) -> IntegrationConnectorDto.builder()
                .connectorKey(rs.getString("connector_key"))
                .connectorLabel(rs.getString("connector_label"))
                .connectorType(rs.getString("connector_type"))
                .sourceSystem(rs.getString("source_system"))
                .targetSystem(rs.getString("target_system"))
                .moduleKey(rs.getString("module_key"))
                .enabled(rs.getBoolean("enabled"))
                .latestRunStatus(rs.getString("latest_run_status"))
                .latestRunStartedAt(toIso(rs.getTimestamp("latest_run_started_at")))
                .latestRunCompletedAt(toIso(rs.getTimestamp("latest_run_completed_at")))
                .lastPublishedAt(toIso(rs.getTimestamp("last_published_at")))
                .openErrorCount(rs.getLong("open_error_count"))
                .publishedMessagesLast24Hours(rs.getLong("published_count"))
                .failedMessagesLast24Hours(rs.getLong("failed_count"))
                .build(), tenantId, tenantId, tenantId, tenantId);
    }

    public List<IntegrationFlowDto> getFlows(String tenantId) {
        return jdbcTemplate.query("""
                select cd.connector_key,
                       fd.flow_key,
                       fd.flow_label,
                       fd.message_type,
                       fd.source_system,
                       fd.target_system,
                       fd.business_object,
                       fd.enabled,
                       coalesce(msg.message_count, 0) as message_count,
                       coalesce(err.error_count, 0) as error_count,
                       lr.run_status as latest_run_status,
                       lr.started_at as latest_run_started_at,
                       lr.completed_at as latest_run_completed_at,
                       lp.last_published_at
                from recon.integration_flow_definition fd
                join recon.integration_connector_definition cd
                  on cd.id = fd.connector_definition_id
                left join (
                    select flow_key, count(*) as message_count
                    from recon.integration_message
                    where tenant_id = ?
                      and created_at >= now() - interval '24 hour'
                    group by flow_key
                ) msg on msg.flow_key = fd.flow_key
                left join (
                    select flow_key, count(*) as error_count
                    from recon.integration_error_queue
                    where tenant_id = ?
                      and created_at >= now() - interval '24 hour'
                    group by flow_key
                ) err on err.flow_key = fd.flow_key
                left join lateral (
                    select r.run_status, r.started_at, r.completed_at
                    from recon.integration_run r
                    where r.tenant_id = fd.tenant_id
                      and r.flow_key = fd.flow_key
                    order by r.started_at desc nulls last, r.created_at desc
                    limit 1
                ) lr on true
                left join lateral (
                    select max(m.created_at) as last_published_at
                    from recon.integration_message m
                    where m.tenant_id = fd.tenant_id
                      and m.flow_key = fd.flow_key
                      and m.message_status = 'PUBLISHED'
                ) lp on true
                where fd.tenant_id = ?
                order by fd.flow_label
                """, (rs, rowNum) -> IntegrationFlowDto.builder()
                .connectorKey(rs.getString("connector_key"))
                .flowKey(rs.getString("flow_key"))
                .flowLabel(rs.getString("flow_label"))
                .messageType(rs.getString("message_type"))
                .sourceSystem(rs.getString("source_system"))
                .targetSystem(rs.getString("target_system"))
                .businessObject(rs.getString("business_object"))
                .enabled(rs.getBoolean("enabled"))
                .messagesLast24Hours(rs.getLong("message_count"))
                .errorsLast24Hours(rs.getLong("error_count"))
                .latestRunStatus(rs.getString("latest_run_status"))
                .latestRunStartedAt(toIso(rs.getTimestamp("latest_run_started_at")))
                .latestRunCompletedAt(toIso(rs.getTimestamp("latest_run_completed_at")))
                .lastPublishedAt(toIso(rs.getTimestamp("last_published_at")))
                .build(), tenantId, tenantId, tenantId);
    }

    public List<IntegrationRunDto> getRecentRuns(String tenantId, int limit) {
        return jdbcTemplate.query("""
                select id,
                       connector_key,
                       flow_key,
                       source_system,
                       target_system,
                       trigger_type,
                       run_status,
                       started_at,
                       completed_at,
                       source_record_count,
                       published_record_count,
                       error_count,
                       run_summary
                from recon.integration_run
                where tenant_id = ?
                order by started_at desc nulls last, created_at desc
                limit ?
                """, (rs, rowNum) -> IntegrationRunDto.builder()
                .id(rs.getObject("id", UUID.class))
                .connectorKey(rs.getString("connector_key"))
                .flowKey(rs.getString("flow_key"))
                .sourceSystem(rs.getString("source_system"))
                .targetSystem(rs.getString("target_system"))
                .triggerType(rs.getString("trigger_type"))
                .runStatus(rs.getString("run_status"))
                .startedAt(toIso(rs.getTimestamp("started_at")))
                .completedAt(toIso(rs.getTimestamp("completed_at")))
                .sourceRecordCount(rs.getInt("source_record_count"))
                .publishedRecordCount(rs.getInt("published_record_count"))
                .errorCount(rs.getInt("error_count"))
                .runSummary(rs.getString("run_summary"))
                .build(), tenantId, limit);
    }

    public List<IntegrationErrorQueueItemDto> getOpenErrors(String tenantId, int limit) {
        return jdbcTemplate.query("""
                select id,
                       integration_message_id,
                       connector_key,
                       flow_key,
                       business_key,
                       document_id,
                       error_type,
                       error_code,
                       error_message,
                       retryable,
                       error_status,
                       created_at,
                       resolved_at,
                       resolution_notes
                from recon.integration_error_queue
                where tenant_id = ?
                  and error_status = 'OPEN'
                order by created_at desc
                limit ?
                """, (rs, rowNum) -> IntegrationErrorQueueItemDto.builder()
                .id(rs.getObject("id", UUID.class))
                .integrationMessageId(rs.getObject("integration_message_id", UUID.class))
                .connectorKey(rs.getString("connector_key"))
                .flowKey(rs.getString("flow_key"))
                .businessKey(rs.getString("business_key"))
                .documentId(rs.getString("document_id"))
                .errorType(rs.getString("error_type"))
                .errorCode(rs.getString("error_code"))
                .errorMessage(rs.getString("error_message"))
                .retryable(rs.getBoolean("retryable"))
                .errorStatus(rs.getString("error_status"))
                .createdAt(toIso(rs.getTimestamp("created_at")))
                .resolvedAt(toIso(rs.getTimestamp("resolved_at")))
                .resolutionNotes(rs.getString("resolution_notes"))
                .build(), tenantId, limit);
    }

    public List<IntegrationReplayRequestDto> getReplayRequests(String tenantId, int limit) {
        return jdbcTemplate.query("""
                select id,
                       connector_key,
                       flow_key,
                       business_key,
                       document_id,
                       requested_by,
                       requested_at,
                       requested_from,
                       requested_to,
                       replay_status,
                       reason,
                       processed_at,
                       processed_by,
                       resolution_message
                from recon.integration_replay_request
                where tenant_id = ?
                order by requested_at desc
                limit ?
                """, (rs, rowNum) -> mapReplayRequest(rs), tenantId, limit);
    }

    public List<IntegrationMessageTraceItemDto> getMessages(String tenantId,
                                                            String connectorKey,
                                                            String flowKey,
                                                            String businessKey,
                                                            String documentId,
                                                            String messageStatus,
                                                            int limit) {
        StringBuilder sql = new StringBuilder("""
                select id,
                       run_id,
                       connector_key,
                       flow_key,
                       message_id,
                       trace_id,
                       business_key,
                       document_id,
                       message_type,
                       source_system,
                       target_system,
                       message_status,
                       retry_count,
                       payload_ref,
                       created_at,
                       updated_at
                from recon.integration_message
                where tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        appendFilter(sql, args, "connector_key", connectorKey);
        appendFilter(sql, args, "flow_key", flowKey);
        appendFilter(sql, args, "business_key", businessKey);
        appendFilter(sql, args, "document_id", documentId);
        appendFilter(sql, args, "message_status", messageStatus == null ? null : messageStatus.toUpperCase());
        sql.append(" order by created_at desc limit ?");
        args.add(Math.max(1, limit));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> IntegrationMessageTraceItemDto.builder()
                .id(rs.getObject("id", UUID.class))
                .runId(rs.getObject("run_id", UUID.class))
                .connectorKey(rs.getString("connector_key"))
                .flowKey(rs.getString("flow_key"))
                .messageId(rs.getString("message_id"))
                .traceId(rs.getString("trace_id"))
                .businessKey(rs.getString("business_key"))
                .documentId(rs.getString("document_id"))
                .messageType(rs.getString("message_type"))
                .sourceSystem(rs.getString("source_system"))
                .targetSystem(rs.getString("target_system"))
                .messageStatus(rs.getString("message_status"))
                .retryCount(rs.getObject("retry_count", Integer.class))
                .payloadRef(rs.getString("payload_ref"))
                .createdAt(toIso(rs.getTimestamp("created_at")))
                .updatedAt(toIso(rs.getTimestamp("updated_at")))
                .build(), args.toArray());
    }

    public IntegrationMessageTraceDetailDto findMessageDetail(String tenantId, UUID id) {
        List<IntegrationMessageTraceDetailDto> rows = jdbcTemplate.query("""
                select m.id,
                       m.run_id,
                       m.connector_key,
                       m.flow_key,
                       m.message_id,
                       m.trace_id,
                       m.business_key,
                       m.document_id,
                       m.message_type,
                       m.source_system,
                       m.target_system,
                       m.message_status,
                       m.retry_count,
                       m.payload_ref,
                       cast(m.payload_snapshot as text) as payload_snapshot_json,
                       m.created_at,
                       m.updated_at,
                       r.run_status,
                       r.trigger_type,
                       r.started_at as run_started_at,
                       r.completed_at as run_completed_at,
                       r.run_summary
                from recon.integration_message m
                left join recon.integration_run r
                  on r.id = m.run_id
                where m.tenant_id = ?
                  and m.id = ?
                """, (rs, rowNum) -> IntegrationMessageTraceDetailDto.builder()
                .id(rs.getObject("id", UUID.class))
                .runId(rs.getObject("run_id", UUID.class))
                .connectorKey(rs.getString("connector_key"))
                .flowKey(rs.getString("flow_key"))
                .messageId(rs.getString("message_id"))
                .traceId(rs.getString("trace_id"))
                .businessKey(rs.getString("business_key"))
                .documentId(rs.getString("document_id"))
                .messageType(rs.getString("message_type"))
                .sourceSystem(rs.getString("source_system"))
                .targetSystem(rs.getString("target_system"))
                .messageStatus(rs.getString("message_status"))
                .retryCount(rs.getObject("retry_count", Integer.class))
                .payloadRef(rs.getString("payload_ref"))
                .payloadSnapshotJson(rs.getString("payload_snapshot_json"))
                .createdAt(toIso(rs.getTimestamp("created_at")))
                .updatedAt(toIso(rs.getTimestamp("updated_at")))
                .runStatus(rs.getString("run_status"))
                .triggerType(rs.getString("trigger_type"))
                .runStartedAt(toIso(rs.getTimestamp("run_started_at")))
                .runCompletedAt(toIso(rs.getTimestamp("run_completed_at")))
                .runSummary(rs.getString("run_summary"))
                .build(), tenantId, id);
        if (rows.isEmpty()) {
            return null;
        }
        IntegrationMessageTraceDetailDto detail = rows.get(0);
        detail.setRelatedErrors(getRelatedErrors(tenantId, id));
        detail.setRelatedReplayRequests(getRelatedReplayRequests(tenantId, detail));
        return detail;
    }

    public IntegrationReplayRequestDto createReplayRequest(String tenantId,
                                                           String requestedBy,
                                                           SaveIntegrationReplayRequest request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into recon.integration_replay_request (
                    id, tenant_id, connector_key, flow_key, requested_by, requested_at,
                    replay_status, business_key, document_id, requested_from, requested_to,
                    reason, request_metadata
                ) values (?, ?, ?, ?, ?, now(), 'REQUESTED', ?, ?, ?, ?, ?, '{}'::jsonb)
                """,
                id,
                tenantId,
                request.getConnectorKey(),
                request.getFlowKey(),
                requestedBy,
                request.getBusinessKey(),
                request.getDocumentId(),
                parseTimestamp(request.getRequestedFrom()),
                parseTimestamp(request.getRequestedTo()),
                request.getReason()
        );
        return findReplayRequestById(tenantId, id);
    }

    public IntegrationErrorQueueItemDto findErrorById(String tenantId, UUID id) {
        List<IntegrationErrorQueueItemDto> rows = jdbcTemplate.query("""
                select id,
                       integration_message_id,
                       connector_key,
                       flow_key,
                       business_key,
                       document_id,
                       error_type,
                       error_code,
                       error_message,
                       retryable,
                       error_status,
                       created_at,
                       resolved_at,
                       resolution_notes
                from recon.integration_error_queue
                where tenant_id = ?
                  and id = ?
                """, (rs, rowNum) -> IntegrationErrorQueueItemDto.builder()
                .id(rs.getObject("id", UUID.class))
                .integrationMessageId(rs.getObject("integration_message_id", UUID.class))
                .connectorKey(rs.getString("connector_key"))
                .flowKey(rs.getString("flow_key"))
                .businessKey(rs.getString("business_key"))
                .documentId(rs.getString("document_id"))
                .errorType(rs.getString("error_type"))
                .errorCode(rs.getString("error_code"))
                .errorMessage(rs.getString("error_message"))
                .retryable(rs.getBoolean("retryable"))
                .errorStatus(rs.getString("error_status"))
                .createdAt(toIso(rs.getTimestamp("created_at")))
                .resolvedAt(toIso(rs.getTimestamp("resolved_at")))
                .resolutionNotes(rs.getString("resolution_notes"))
                .build(), tenantId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public IntegrationErrorQueueItemDto resolveError(String tenantId,
                                                     UUID id,
                                                     String resolutionNotes) {
        jdbcTemplate.update("""
                update recon.integration_error_queue
                set error_status = 'RESOLVED',
                    resolved_at = now(),
                    resolution_notes = ?
                where tenant_id = ?
                  and id = ?
                """, truncate(resolutionNotes, 300), tenantId, id);
        return findErrorById(tenantId, id);
    }

    public IntegrationReplayRequestRecord findReplayRequestForExecution(String tenantId, UUID id) {
        List<IntegrationReplayRequestRecord> rows = jdbcTemplate.query("""
                select id,
                       connector_key,
                       flow_key,
                       business_key,
                       document_id,
                       requested_by,
                       requested_at,
                       requested_from,
                       requested_to,
                       replay_status,
                       reason,
                       processed_at,
                       processed_by,
                       resolution_message
                from recon.integration_replay_request
                where tenant_id = ?
                  and id = ?
                """, (rs, rowNum) -> new IntegrationReplayRequestRecord(
                rs.getObject("id", UUID.class),
                rs.getString("connector_key"),
                rs.getString("flow_key"),
                rs.getString("business_key"),
                rs.getString("document_id"),
                rs.getString("requested_by"),
                rs.getTimestamp("requested_at"),
                rs.getTimestamp("requested_from"),
                rs.getTimestamp("requested_to"),
                rs.getString("replay_status"),
                rs.getString("reason"),
                rs.getTimestamp("processed_at"),
                rs.getString("processed_by"),
                rs.getString("resolution_message")
        ), tenantId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void markReplayRunning(String tenantId, UUID id, String processedBy) {
        jdbcTemplate.update("""
                update recon.integration_replay_request
                set replay_status = 'RUNNING',
                    processed_by = ?,
                    processed_at = now()
                where tenant_id = ?
                  and id = ?
                """, processedBy, tenantId, id);
    }

    public IntegrationReplayRequestDto completeReplayRequest(String tenantId,
                                                             UUID id,
                                                             String processedBy,
                                                             String replayStatus,
                                                             String resolutionMessage) {
        jdbcTemplate.update("""
                update recon.integration_replay_request
                set replay_status = ?,
                    processed_by = ?,
                    processed_at = now(),
                    resolution_message = ?
                where tenant_id = ?
                  and id = ?
                """, replayStatus, processedBy, truncate(resolutionMessage, 300), tenantId, id);
        return findReplayRequestById(tenantId, id);
    }

    public IntegrationReplayRequestDto findReplayRequestById(String tenantId, UUID id) {
        List<IntegrationReplayRequestDto> rows = jdbcTemplate.query("""
                select id,
                       connector_key,
                       flow_key,
                       business_key,
                       document_id,
                       requested_by,
                       requested_at,
                       requested_from,
                       requested_to,
                       replay_status,
                       reason,
                       processed_at,
                       processed_by,
                       resolution_message
                from recon.integration_replay_request
                where tenant_id = ?
                  and id = ?
                """, (rs, rowNum) -> mapReplayRequest(rs), tenantId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private IntegrationReplayRequestDto mapReplayRequest(java.sql.ResultSet rs) throws java.sql.SQLException {
        return IntegrationReplayRequestDto.builder()
                .id(rs.getObject("id", UUID.class))
                .connectorKey(rs.getString("connector_key"))
                .flowKey(rs.getString("flow_key"))
                .businessKey(rs.getString("business_key"))
                .documentId(rs.getString("document_id"))
                .requestedBy(rs.getString("requested_by"))
                .requestedAt(toIso(rs.getTimestamp("requested_at")))
                .requestedFrom(toIso(rs.getTimestamp("requested_from")))
                .requestedTo(toIso(rs.getTimestamp("requested_to")))
                .replayStatus(rs.getString("replay_status"))
                .reason(rs.getString("reason"))
                .processedAt(toIso(rs.getTimestamp("processed_at")))
                .processedBy(rs.getString("processed_by"))
                .resolutionMessage(rs.getString("resolution_message"))
                .build();
    }

    private List<IntegrationErrorQueueItemDto> getRelatedErrors(String tenantId, UUID integrationMessageId) {
        return jdbcTemplate.query("""
                select id,
                       integration_message_id,
                       connector_key,
                       flow_key,
                       business_key,
                       document_id,
                       error_type,
                       error_code,
                       error_message,
                       retryable,
                       error_status,
                       created_at,
                       resolved_at,
                       resolution_notes
                from recon.integration_error_queue
                where tenant_id = ?
                  and integration_message_id = ?
                order by created_at desc
                limit 10
                """, (rs, rowNum) -> IntegrationErrorQueueItemDto.builder()
                .id(rs.getObject("id", UUID.class))
                .integrationMessageId(rs.getObject("integration_message_id", UUID.class))
                .connectorKey(rs.getString("connector_key"))
                .flowKey(rs.getString("flow_key"))
                .businessKey(rs.getString("business_key"))
                .documentId(rs.getString("document_id"))
                .errorType(rs.getString("error_type"))
                .errorCode(rs.getString("error_code"))
                .errorMessage(rs.getString("error_message"))
                .retryable(rs.getBoolean("retryable"))
                .errorStatus(rs.getString("error_status"))
                .createdAt(toIso(rs.getTimestamp("created_at")))
                .resolvedAt(toIso(rs.getTimestamp("resolved_at")))
                .resolutionNotes(rs.getString("resolution_notes"))
                .build(), tenantId, integrationMessageId);
    }

    private List<IntegrationReplayRequestDto> getRelatedReplayRequests(String tenantId,
                                                                       IntegrationMessageTraceDetailDto detail) {
        StringBuilder sql = new StringBuilder("""
                select id,
                       connector_key,
                       flow_key,
                       business_key,
                       document_id,
                       requested_by,
                       requested_at,
                       requested_from,
                       requested_to,
                       replay_status,
                       reason,
                       processed_at,
                       processed_by,
                       resolution_message
                from recon.integration_replay_request
                where tenant_id = ?
                  and connector_key = ?
                  and flow_key = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(detail.getConnectorKey());
        args.add(detail.getFlowKey());
        if (detail.getBusinessKey() != null && !detail.getBusinessKey().isBlank()) {
            sql.append(" and business_key = ?");
            args.add(detail.getBusinessKey());
        } else if (detail.getDocumentId() != null && !detail.getDocumentId().isBlank()) {
            sql.append(" and document_id = ?");
            args.add(detail.getDocumentId());
        } else {
            return List.of();
        }
        sql.append(" order by requested_at desc limit 10");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapReplayRequest(rs), args.toArray());
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" and ").append(column).append(" = ?");
        args.add(value);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private String withConnectorKeyFilter(String baseSql,
                                          Collection<String> connectorKeys,
                                          String connectorColumn) {
        return baseSql + " and " + connectorColumn + " in (" + connectorKeys.stream()
                .map(key -> "?")
                .collect(java.util.stream.Collectors.joining(", ")) + ")";
    }

    private String withFlowConnectorFilter(String baseSql, Collection<String> connectorKeys) {
        return baseSql + " and cd.connector_key in (" + connectorKeys.stream()
                .map(key -> "?")
                .collect(java.util.stream.Collectors.joining(", ")) + ")";
    }

    private Object[] argsWithTenantAndConnectorKeys(String tenantId, Collection<String> connectorKeys) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(connectorKeys);
        return args.toArray();
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Timestamp.valueOf(LocalDateTime.parse(value));
    }

    private String toIso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record IntegrationReplayRequestRecord(UUID id,
                                                 String connectorKey,
                                                 String flowKey,
                                                 String businessKey,
                                                 String documentId,
                                                 String requestedBy,
                                                 Timestamp requestedAt,
                                                 Timestamp requestedFrom,
                                                 Timestamp requestedTo,
                                                 String replayStatus,
                                                 String reason,
                                                 Timestamp processedAt,
                                                 String processedBy,
                                                 String resolutionMessage) {
    }
}
