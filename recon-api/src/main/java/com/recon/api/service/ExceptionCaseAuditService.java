package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionCaseAuditEvent;
import com.recon.api.domain.ExceptionCaseAuditSnapshotDto;
import com.recon.api.domain.ExceptionCaseTimelineEventDto;
import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.ExceptionCaseAuditEventRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExceptionCaseAuditService {

    private static final List<String> SENSITIVE_FIELDS = List.of(
            "caseStatus",
            "severity",
            "reasonCode",
            "rootCauseCategory",
            "assigneeUsername",
            "assignedRoleName",
            "nextAction",
            "nextActionDueAt",
            "handoffNote",
            "notes",
            "escalationState"
    );

    private final ExceptionCaseAuditEventRepository auditEventRepository;
    private final AuditLedgerService auditLedgerService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordCaseChange(ExceptionCase exceptionCase,
                                 String actor,
                                 String eventType,
                                 String title,
                                 String summary,
                                 SensitiveFieldSnapshot before,
                                 SensitiveFieldSnapshot after) {
        if (exceptionCase != null) {
            auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                    .tenantId(exceptionCase.getTenantId())
                    .sourceType("EXCEPTION")
                    .moduleKey(exceptionCase.getReconView())
                    .entityType("EXCEPTION_CASE")
                    .entityKey(exceptionCase.getTransactionKey())
                    .actionType(defaultIfBlank(eventType, "CASE_UPDATED"))
                    .title(defaultIfBlank(title, "Case updated"))
                    .summary(summary)
                    .actor(actor)
                    .status(exceptionCase.getCaseStatus())
                    .referenceKey(exceptionCase.getTransactionKey())
                    .controlFamily("EXCEPTION_CONTROL")
                    .evidenceTags(List.of("EXCEPTION", "CASE"))
                    .beforeState(before != null ? before.values() : null)
                    .afterState(after != null ? after.values() : null)
                    .build());
        }
    }

    @Transactional
    public void recordComment(ExceptionCase exceptionCase,
                              String actor,
                              String commentText) {
        String trimmedComment = trimToNull(commentText);
        if (exceptionCase == null || trimmedComment == null) {
            return;
        }
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(exceptionCase.getTenantId())
                .sourceType("EXCEPTION")
                .moduleKey(exceptionCase.getReconView())
                .entityType("EXCEPTION_COMMENT")
                .entityKey(exceptionCase.getTransactionKey())
                .actionType("COMMENT_ADDED")
                .title("Exception comment added")
                .summary(trimmedComment)
                .actor(actor)
                .status("COMMENTED")
                .referenceKey(exceptionCase.getTransactionKey())
                .controlFamily("EXCEPTION_CONTROL")
                .evidenceTags(List.of("EXCEPTION", "COMMENT"))
                .afterState(trimmedComment)
                .build());
    }

    @Transactional
    public void recordReopen(ExceptionCase exceptionCase,
                             String actor,
                             String previousStatus,
                             String currentStatus,
                             String reopenReason) {
        saveEvent(exceptionCase,
                "CASE",
                "CASE_REOPENED",
                "Case reopened",
                defaultIfBlank(previousStatus, "RESOLVED") + " -> "
                        + defaultIfBlank(currentStatus, "OPEN")
                        + " / "
                        + defaultIfBlank(trimToNull(reopenReason), "Reopened for additional investigation"),
                actor,
                currentStatus,
                null,
                null,
                null);
        if (exceptionCase != null) {
            auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                    .tenantId(exceptionCase.getTenantId())
                    .sourceType("EXCEPTION")
                    .moduleKey(exceptionCase.getReconView())
                    .entityType("EXCEPTION_CASE")
                    .entityKey(exceptionCase.getTransactionKey())
                    .actionType("CASE_REOPENED")
                    .title("Case reopened")
                    .summary(defaultIfBlank(previousStatus, "RESOLVED") + " -> " + defaultIfBlank(currentStatus, "OPEN"))
                    .actor(actor)
                    .reason(reopenReason)
                    .status(currentStatus)
                    .referenceKey(exceptionCase.getTransactionKey())
                    .controlFamily("EXCEPTION_CONTROL")
                    .evidenceTags(List.of("EXCEPTION", "REOPEN"))
                    .build());
        }
    }

    @Transactional
    public void recordEscalation(ExceptionCase exceptionCase,
                                 String actor,
                                 String summary) {
        saveEvent(exceptionCase,
                "ESCALATION",
                "CASE_ESCALATED",
                "Case escalated",
                summary,
                actor,
                defaultIfBlank(exceptionCase.getEscalationState(), "ESCALATED"),
                null,
                null,
                null);
        if (exceptionCase != null) {
            auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                    .tenantId(exceptionCase.getTenantId())
                    .sourceType("EXCEPTION")
                    .moduleKey(exceptionCase.getReconView())
                    .entityType("EXCEPTION_CASE")
                    .entityKey(exceptionCase.getTransactionKey())
                    .actionType("CASE_ESCALATED")
                    .title("Case escalated")
                    .summary(summary)
                    .actor(actor)
                    .status(defaultIfBlank(exceptionCase.getEscalationState(), "ESCALATED"))
                    .referenceKey(exceptionCase.getTransactionKey())
                    .controlFamily("EXCEPTION_CONTROL")
                    .evidenceTags(List.of("EXCEPTION", "ESCALATION"))
                    .build());
        }
    }

    @Transactional
    public void recordSensitiveFieldSnapshot(ExceptionCase exceptionCase,
                                             String actor,
                                             SensitiveFieldSnapshot before,
                                             SensitiveFieldSnapshot after) {
        if (exceptionCase == null || before == null || after == null) {
            return;
        }
        List<String> changedFields = SENSITIVE_FIELDS.stream()
                .filter(field -> !Objects.equals(before.values().get(field), after.values().get(field)))
                .toList();
        if (changedFields.isEmpty()) {
            return;
        }
        saveEvent(exceptionCase,
                "AUDIT",
                "CASE_AUDIT_SNAPSHOT",
                "Sensitive field snapshot",
                "Captured before/after for " + String.join(", ", changedFields),
                actor,
                exceptionCase.getCaseStatus(),
                changedFields,
                serializeSnapshot(before.values()),
                serializeSnapshot(after.values()));
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(exceptionCase.getTenantId())
                .sourceType("EXCEPTION")
                .moduleKey(exceptionCase.getReconView())
                .entityType("EXCEPTION_CASE")
                .entityKey(exceptionCase.getTransactionKey())
                .actionType("CASE_AUDIT_SNAPSHOT")
                .title("Sensitive field snapshot")
                .summary("Captured before/after for " + String.join(", ", changedFields))
                .actor(actor)
                .status(exceptionCase.getCaseStatus())
                .referenceKey(exceptionCase.getTransactionKey())
                .controlFamily("EXCEPTION_CONTROL")
                .evidenceTags(List.of("EXCEPTION", "AUDIT"))
                .beforeState(before.values())
                .afterState(after.values())
                .metadata(changedFields)
                .build());
    }

    @Transactional(readOnly = true)
    public List<ExceptionCaseTimelineEventDto> getTimelineEvents(ExceptionCase exceptionCase, TenantConfig tenant) {
        return auditEventRepository.findByExceptionCaseOrderByCreatedAtAsc(exceptionCase).stream()
                .map(event -> ExceptionCaseTimelineEventDto.builder()
                        .sourceType(event.getSourceType())
                        .eventType(event.getEventType())
                        .title(event.getTitle())
                        .summary(event.getSummary())
                        .actor(event.getActor())
                        .status(event.getStatus())
                        .eventAt(TimezoneConverter.toDisplay(valueOrNull(event.getCreatedAt()), tenant))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimelineItem> getTimelineItems(ExceptionCase exceptionCase, TenantConfig tenant) {
        return auditEventRepository.findByExceptionCaseOrderByCreatedAtAsc(exceptionCase).stream()
                .map(event -> new TimelineItem(
                        event.getCreatedAt(),
                        ExceptionCaseTimelineEventDto.builder()
                                .sourceType(event.getSourceType())
                                .eventType(event.getEventType())
                                .title(event.getTitle())
                                .summary(event.getSummary())
                                .actor(event.getActor())
                                .status(event.getStatus())
                                .eventAt(TimezoneConverter.toDisplay(valueOrNull(event.getCreatedAt()), tenant))
                                .build()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExceptionCaseAuditSnapshotDto> getAuditSnapshots(ExceptionCase exceptionCase, TenantConfig tenant) {
        return auditEventRepository.findByExceptionCaseOrderByCreatedAtAsc(exceptionCase).stream()
                .filter(event -> "CASE_AUDIT_SNAPSHOT".equalsIgnoreCase(event.getEventType()))
                .map(event -> ExceptionCaseAuditSnapshotDto.builder()
                        .id(event.getId())
                        .title(event.getTitle())
                        .summary(event.getSummary())
                        .actor(event.getActor())
                        .createdAt(TimezoneConverter.toDisplay(valueOrNull(event.getCreatedAt()), tenant))
                        .changedFields(splitChangedFields(event.getChangedFields()))
                        .beforeSnapshot(event.getBeforeSnapshot())
                        .afterSnapshot(event.getAfterSnapshot())
                        .build())
                .toList();
    }

    public SensitiveFieldSnapshot captureSensitiveSnapshot(ExceptionCase exceptionCase) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("caseStatus", defaultIfBlank(exceptionCase != null ? exceptionCase.getCaseStatus() : null, "OPEN"));
        values.put("severity", defaultIfBlank(exceptionCase != null ? exceptionCase.getSeverity() : null, "MEDIUM"));
        values.put("reasonCode", trimToNull(exceptionCase != null ? exceptionCase.getReasonCode() : null));
        values.put("rootCauseCategory", trimToNull(exceptionCase != null ? exceptionCase.getRootCauseCategory() : null));
        values.put("assigneeUsername", trimToNull(exceptionCase != null ? exceptionCase.getAssigneeUsername() : null));
        values.put("assignedRoleName", trimToNull(exceptionCase != null ? exceptionCase.getAssignedRoleName() : null));
        values.put("nextAction", trimToNull(exceptionCase != null ? exceptionCase.getNextAction() : null));
        values.put("nextActionDueAt", valueOrNull(exceptionCase != null ? exceptionCase.getNextActionDueAt() : null));
        values.put("handoffNote", trimToNull(exceptionCase != null ? exceptionCase.getHandoffNote() : null));
        values.put("notes", trimToNull(exceptionCase != null ? exceptionCase.getNotes() : null));
        values.put("escalationState", defaultIfBlank(exceptionCase != null ? exceptionCase.getEscalationState() : null, "NONE"));
        return new SensitiveFieldSnapshot(values);
    }

    private void saveEvent(ExceptionCase exceptionCase,
                           String sourceType,
                           String eventType,
                           String title,
                           String summary,
                           String actor,
                           String status,
                           List<String> changedFields,
                           String beforeSnapshot,
                           String afterSnapshot) {
        if (exceptionCase == null) {
            return;
        }
        auditEventRepository.save(ExceptionCaseAuditEvent.builder()
                .id(UUID.randomUUID())
                .exceptionCase(exceptionCase)
                .tenantId(exceptionCase.getTenantId())
                .transactionKey(exceptionCase.getTransactionKey())
                .reconView(exceptionCase.getReconView())
                .sourceType(sourceType)
                .eventType(eventType)
                .title(title)
                .summary(trimToNull(summary))
                .actor(trimToNull(actor))
                .status(trimToNull(status))
                .changedFields(changedFields == null || changedFields.isEmpty() ? null : String.join(",", changedFields))
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(afterSnapshot)
                .build());
    }

    private List<String> splitChangedFields(String changedFields) {
        String trimmed = trimToNull(changedFields);
        if (trimmed == null) {
            return List.of();
        }
        return java.util.Arrays.stream(trimmed.split(","))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String serializeSnapshot(Map<String, String> snapshot) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return snapshot.toString();
        }
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String valueOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    public record SensitiveFieldSnapshot(Map<String, String> values) {
    }

    public record TimelineItem(java.time.LocalDateTime timestamp, ExceptionCaseTimelineEventDto event) {
    }
}
