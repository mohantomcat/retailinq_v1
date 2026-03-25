package com.recon.integration.jdbc;

import com.recon.integration.connector.IntegrationConnectorContract;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

public class IntegrationRunJournalSupport {

    private final JdbcTemplate jdbcTemplate;

    public IntegrationRunJournalSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID startRun(String tenantId,
                         IntegrationConnectorContract contract,
                         String triggerType) {
        UUID connectorId = ensureConnectorDefinition(tenantId, contract);
        ensureFlowDefinition(tenantId, contract, connectorId);
        ensureMappingDefinition(tenantId, contract);

        UUID runId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into recon.integration_run (
                    id, tenant_id, connector_key, flow_key, source_system, target_system,
                    trigger_type, run_status, started_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?)
                """,
                runId,
                tenantId,
                contract.connectorKey(),
                contract.flowKey(),
                contract.sourceSystem(),
                contract.targetSystem(),
                triggerType,
                now,
                now,
                now
        );
        return runId;
    }

    public UUID startStep(UUID runId,
                          String stepKey,
                          String stepLabel,
                          int stepOrder) {
        UUID stepId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into recon.integration_run_step (
                    id, run_id, step_key, step_label, step_order, step_status,
                    started_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?)
                """,
                stepId,
                runId,
                stepKey,
                stepLabel,
                stepOrder,
                now,
                now,
                now
        );
        return stepId;
    }

    public UUID recordPublishedMessage(String tenantId,
                                       UUID runId,
                                       IntegrationConnectorContract contract,
                                       CanonicalIntegrationEnvelope envelope,
                                       String payloadSnapshotJson) {
        UUID messageRowId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.queryForObject("""
                insert into recon.integration_message (
                    id, tenant_id, run_id, connector_key, flow_key, message_id, trace_id,
                    business_key, document_id, message_type, source_system, target_system,
                    message_status, retry_count, payload_ref, payload_snapshot, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?::jsonb, ?, ?)
                on conflict (tenant_id, message_id) do update set
                    run_id = excluded.run_id,
                    connector_key = excluded.connector_key,
                    flow_key = excluded.flow_key,
                    business_key = excluded.business_key,
                    document_id = excluded.document_id,
                    message_status = excluded.message_status,
                    retry_count = excluded.retry_count,
                    payload_ref = excluded.payload_ref,
                    payload_snapshot = excluded.payload_snapshot,
                    updated_at = excluded.updated_at
                returning id
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                messageRowId,
                tenantId,
                runId,
                contract.connectorKey(),
                contract.flowKey(),
                envelope.getMessageId(),
                envelope.getTraceId(),
                envelope.getBusinessKey(),
                envelope.getDocumentId(),
                envelope.getMessageType(),
                envelope.getSourceSystem(),
                envelope.getTargetSystem(),
                envelope.getRetryCount() == null ? 0 : envelope.getRetryCount(),
                envelope.getPayloadRef(),
                payloadSnapshotJson,
                now,
                now
        );
    }

    public void recordFailedMessage(String tenantId,
                                    UUID runId,
                                    IntegrationConnectorContract contract,
                                    String messageId,
                                    String traceId,
                                    String businessKey,
                                    String documentId,
                                    String payloadSnapshotJson,
                                    String errorType,
                                    String errorCode,
                                    String errorMessage,
                                    boolean retryable) {
        UUID messageRowId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        UUID integrationMessageId = jdbcTemplate.queryForObject("""
                insert into recon.integration_message (
                    id, tenant_id, run_id, connector_key, flow_key, message_id, trace_id,
                    business_key, document_id, message_type, source_system, target_system,
                    message_status, retry_count, payload_snapshot, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'FAILED', 0, ?::jsonb, ?, ?)
                on conflict (tenant_id, message_id) do update set
                    run_id = excluded.run_id,
                    connector_key = excluded.connector_key,
                    flow_key = excluded.flow_key,
                    business_key = excluded.business_key,
                    document_id = excluded.document_id,
                    message_status = excluded.message_status,
                    payload_snapshot = excluded.payload_snapshot,
                    updated_at = excluded.updated_at
                returning id
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                messageRowId,
                tenantId,
                runId,
                contract.connectorKey(),
                contract.flowKey(),
                messageId,
                traceId,
                businessKey,
                documentId,
                contract.messageType(),
                contract.sourceSystem(),
                contract.targetSystem(),
                payloadSnapshotJson,
                now,
                now
        );

        jdbcTemplate.update("""
                insert into recon.integration_error_queue (
                    id, tenant_id, run_id, integration_message_id, connector_key, flow_key,
                    business_key, document_id, error_type, error_code, error_message, retryable,
                    error_status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
                """,
                UUID.randomUUID(),
                tenantId,
                runId,
                integrationMessageId,
                contract.connectorKey(),
                contract.flowKey(),
                businessKey,
                documentId,
                errorType,
                errorCode,
                truncate(errorMessage, 4000),
                retryable,
                now
        );
    }

    public void completeStep(UUID stepId,
                             int processedCount,
                             int errorCount,
                             String detailMessage,
                             String stepStatus) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                update recon.integration_run_step
                set step_status = ?,
                    completed_at = ?,
                    processed_count = ?,
                    error_count = ?,
                    detail_message = ?,
                    updated_at = ?
                where id = ?
                """,
                stepStatus,
                now,
                processedCount,
                errorCount,
                truncate(detailMessage, 300),
                now,
                stepId
        );
    }

    public void completeRun(UUID runId,
                            int sourceRecordCount,
                            int publishedRecordCount,
                            int errorCount,
                            String summary,
                            String runStatus) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                update recon.integration_run
                set run_status = ?,
                    completed_at = ?,
                    source_record_count = ?,
                    published_record_count = ?,
                    error_count = ?,
                    run_summary = ?,
                    updated_at = ?
                where id = ?
                """,
                runStatus,
                now,
                sourceRecordCount,
                publishedRecordCount,
                errorCount,
                truncate(summary, 300),
                now,
                runId
        );
    }

    private UUID ensureConnectorDefinition(String tenantId,
                                           IntegrationConnectorContract contract) {
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.queryForObject("""
                insert into recon.integration_connector_definition (
                    id, tenant_id, connector_key, connector_label, connector_type,
                    source_system, target_system, module_key, enabled, runtime_mode,
                    created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?)
                on conflict (tenant_id, connector_key) do update set
                    connector_label = excluded.connector_label,
                    connector_type = excluded.connector_type,
                    source_system = excluded.source_system,
                    target_system = excluded.target_system,
                    module_key = excluded.module_key,
                    runtime_mode = excluded.runtime_mode,
                    updated_at = excluded.updated_at
                returning id
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                UUID.randomUUID(),
                tenantId,
                contract.connectorKey(),
                contract.connectorLabel(),
                contract.connectorType(),
                contract.sourceSystem(),
                contract.targetSystem(),
                contract.moduleKey(),
                contract.runtimeMode(),
                now,
                now
        );
    }

    private void ensureFlowDefinition(String tenantId,
                                      IntegrationConnectorContract contract,
                                      UUID connectorDefinitionId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into recon.integration_flow_definition (
                    id, tenant_id, connector_definition_id, flow_key, flow_label,
                    message_type, source_system, target_system, business_object,
                    enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)
                on conflict (tenant_id, flow_key) do update set
                    connector_definition_id = excluded.connector_definition_id,
                    flow_label = excluded.flow_label,
                    message_type = excluded.message_type,
                    source_system = excluded.source_system,
                    target_system = excluded.target_system,
                    business_object = excluded.business_object,
                    updated_at = excluded.updated_at
                """,
                UUID.randomUUID(),
                tenantId,
                connectorDefinitionId,
                contract.flowKey(),
                contract.flowLabel(),
                contract.messageType(),
                contract.sourceSystem(),
                contract.targetSystem(),
                contract.businessObject(),
                now,
                now
        );
    }

    private void ensureMappingDefinition(String tenantId,
                                         IntegrationConnectorContract contract) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into recon.integration_mapping_definition (
                    id, tenant_id, flow_key, mapping_name, schema_version,
                    mapping_status, source_schema_key, target_schema_key,
                    mapping_rules, created_at, updated_at
                ) values (?, ?, ?, ?, 1, 'ACTIVE', ?, ?, ?::jsonb, ?, ?)
                on conflict (tenant_id, flow_key, mapping_name) do update set
                    source_schema_key = excluded.source_schema_key,
                    target_schema_key = excluded.target_schema_key,
                    mapping_rules = excluded.mapping_rules,
                    updated_at = excluded.updated_at
                """,
                UUID.randomUUID(),
                tenantId,
                contract.flowKey(),
                contract.mappingName(),
                contract.sourceSchemaKey(),
                contract.targetSchemaKey(),
                contract.mappingRulesJson(),
                now,
                now
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
