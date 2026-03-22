package com.recon.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.ActivityRecordDto;
import com.recon.api.domain.AuditArchiveBatch;
import com.recon.api.domain.AuditArchiveBatchDto;
import com.recon.api.domain.AuditArchiveExecutionResponse;
import com.recon.api.domain.AuditLedgerArchiveEntry;
import com.recon.api.domain.AuditLedgerEntry;
import com.recon.api.domain.AuditLedgerRecordDto;
import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.AuditRetentionCenterResponse;
import com.recon.api.domain.AuditRetentionPolicy;
import com.recon.api.domain.AuditRetentionPolicyDto;
import com.recon.api.domain.RunAuditArchiveRequest;
import com.recon.api.domain.SaveAuditRetentionPolicyRequest;
import com.recon.api.domain.SoxAuditReportResponse;
import com.recon.api.domain.SoxAuditSummaryDto;
import com.recon.api.domain.SoxControlEvidenceDto;
import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.AuditArchiveBatchRepository;
import com.recon.api.repository.AuditLedgerArchiveEntryRepository;
import com.recon.api.repository.AuditLedgerEntryRepository;
import com.recon.api.repository.AuditLedgerQueryRepository;
import com.recon.api.repository.AuditRetentionPolicyRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditComplianceService {

    private static final int EXPORT_LIMIT = 10_000;

    private final AuditRetentionPolicyRepository retentionPolicyRepository;
    private final AuditArchiveBatchRepository archiveBatchRepository;
    private final AuditLedgerArchiveEntryRepository archiveEntryRepository;
    private final AuditLedgerEntryRepository auditLedgerEntryRepository;
    private final AuditLedgerQueryRepository auditLedgerQueryRepository;
    private final AuditLedgerService auditLedgerService;
    private final TenantService tenantService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AuditRetentionCenterResponse getRetentionCenter(String tenantId) {
        TenantConfig tenant = tenantService.getTenant(tenantId);
        AuditRetentionPolicy policy = getOrDefaultPolicy(tenantId, "system");
        LocalDateTime archiveBefore = LocalDateTime.now().minusDays(Math.max(1, policy.getArchiveAfterDays()));

        return AuditRetentionCenterResponse.builder()
                .policy(toDto(policy, tenant))
                .liveEntries(auditLedgerEntryRepository.countByTenantId(tenantId))
                .archivedEntries(archiveEntryRepository.countByTenantId(tenantId))
                .eligibleForArchive(auditLedgerQueryRepository.countEligibleForArchive(tenantId, archiveBefore))
                .lastArchiveAt(archiveBatchRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId)
                        .map(AuditArchiveBatch::getCreatedAt)
                        .map(value -> TimezoneConverter.toDisplay(value.toString(), tenant))
                        .orElse(null))
                .recentBatches(archiveBatchRepository.findTop20ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                        .map(batch -> toDto(batch, tenant))
                        .toList())
                .build();
    }

    @Transactional
    public AuditRetentionPolicyDto saveRetentionPolicy(String tenantId,
                                                       SaveAuditRetentionPolicyRequest request,
                                                       String actorUsername) {
        AuditRetentionPolicy policy = retentionPolicyRepository.findByTenantId(tenantId)
                .orElseGet(() -> AuditRetentionPolicy.builder()
                        .tenantId(tenantId)
                        .createdBy(actorUsername)
                        .build());
        policy.setPolicyName(defaultIfBlank(
                request != null ? request.getPolicyName() : null,
                defaultPolicyName(tenantId)));
        policy.setMinimumRetentionDays(maxPositive(
                request != null ? request.getMinimumRetentionDays() : null,
                policy.getMinimumRetentionDays(),
                2555));
        policy.setArchiveAfterDays(maxPositive(
                request != null ? request.getArchiveAfterDays() : null,
                policy.getArchiveAfterDays(),
                90));
        policy.setLegalHold(request != null && request.getLegalHold() != null
                ? request.getLegalHold()
                : policy.isLegalHold());
        policy.setDefaultExportFormat(defaultIfBlank(
                request != null ? request.getDefaultExportFormat() : null,
                defaultIfBlank(policy.getDefaultExportFormat(), "CSV")).toUpperCase(Locale.ROOT));
        policy.setNotes(trimToNull(request != null ? request.getNotes() : null));
        if (policy.getCreatedBy() == null) {
            policy.setCreatedBy(actorUsername);
        }
        policy.setUpdatedBy(actorUsername);
        AuditRetentionPolicy saved = retentionPolicyRepository.save(policy);

        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("COMPLIANCE")
                .moduleKey("AUDIT")
                .entityType("AUDIT_RETENTION_POLICY")
                .entityKey(tenantId)
                .actionType("RETENTION_POLICY_UPDATED")
                .title("Audit retention policy updated")
                .summary(saved.getPolicyName())
                .actor(actorUsername)
                .reason(trimToNull(saved.getNotes()))
                .status(saved.isLegalHold() ? "LEGAL_HOLD" : "ACTIVE")
                .controlFamily("SOX")
                .evidenceTags(List.of("AUDIT", "RETENTION", "POLICY"))
                .afterState(saved)
                .build());

        return toDto(saved, tenantService.getTenant(tenantId));
    }

    @Transactional
    public AuditArchiveExecutionResponse runArchive(String tenantId,
                                                    RunAuditArchiveRequest request,
                                                    String actorUsername) {
        AuditRetentionPolicy policy = getOrDefaultPolicy(tenantId, actorUsername);
        List<AuditLedgerEntry> candidates = auditLedgerEntryRepository
                .findTop500ByTenantIdAndEventAtBeforeOrderByEventAtAsc(
                        tenantId,
                        LocalDateTime.now().minusDays(Math.max(1, policy.getArchiveAfterDays())))
                .stream()
                .filter(entry -> !archiveEntryRepository.existsByOriginalEntryId(entry.getId()))
                .toList();
        if (candidates.isEmpty()) {
            return AuditArchiveExecutionResponse.builder()
                    .archivedCount(0)
                    .batch(null)
                    .build();
        }

        AuditArchiveBatch batch = archiveBatchRepository.save(AuditArchiveBatch.builder()
                .tenantId(tenantId)
                .policyName(policy.getPolicyName())
                .archiveReason(defaultIfBlank(request != null ? request.getArchiveReason() : null, "RETENTION_ARCHIVE"))
                .exportFormat(defaultIfBlank(
                        request != null ? request.getExportFormat() : null,
                        defaultIfBlank(policy.getDefaultExportFormat(), "JSON")).toUpperCase(Locale.ROOT))
                .fromEventAt(candidates.get(0).getEventAt())
                .toEventAt(candidates.get(candidates.size() - 1).getEventAt())
                .entryCount(candidates.size())
                .createdBy(actorUsername)
                .build());

        archiveEntryRepository.saveAll(candidates.stream()
                .map(entry -> AuditLedgerArchiveEntry.builder()
                        .archiveBatch(batch)
                        .originalEntryId(entry.getId())
                        .originalEntryNumber(entry.getEntryNumber())
                        .tenantId(entry.getTenantId())
                        .sourceType(entry.getSourceType())
                        .moduleKey(entry.getModuleKey())
                        .entityType(entry.getEntityType())
                        .entityKey(entry.getEntityKey())
                        .actionType(entry.getActionType())
                        .title(entry.getTitle())
                        .summary(entry.getSummary())
                        .actor(entry.getActor())
                        .reason(entry.getReason())
                        .status(entry.getStatus())
                        .referenceKey(entry.getReferenceKey())
                        .controlFamily(entry.getControlFamily())
                        .evidenceTags(entry.getEvidenceTags())
                        .beforeState(entry.getBeforeState())
                        .afterState(entry.getAfterState())
                        .metadataJson(entry.getMetadataJson())
                        .eventAt(entry.getEventAt())
                        .previousHash(entry.getPreviousHash())
                        .eventHash(entry.getEventHash())
                        .build())
                .toList());

        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("COMPLIANCE")
                .moduleKey("AUDIT")
                .entityType("AUDIT_ARCHIVE_BATCH")
                .entityKey(batch.getId().toString())
                .actionType("ARCHIVE_CREATED")
                .title("Audit archive package created")
                .summary(candidates.size() + " entries archived")
                .actor(actorUsername)
                .reason(batch.getArchiveReason())
                .status("ARCHIVED")
                .referenceKey(batch.getId().toString())
                .controlFamily("SOX")
                .evidenceTags(List.of("AUDIT", "ARCHIVE", "COMPLIANCE"))
                .metadata(batch)
                .build());

        return AuditArchiveExecutionResponse.builder()
                .batch(toDto(batch, tenantService.getTenant(tenantId)))
                .archivedCount(candidates.size())
                .build();
    }

    @Scheduled(fixedDelayString = "${app.audit.archive-interval-ms:86400000}")
    @Transactional
    public void archiveEligibleEntries() {
        for (TenantConfig tenant : tenantService.getAllTenants()) {
            runArchive(tenant.getTenantId(), RunAuditArchiveRequest.builder()
                    .archiveReason("SCHEDULED_RETENTION_ARCHIVE")
                    .exportFormat("JSON")
                    .build(), "system");
        }
    }

    @Transactional
    public ExportBundle exportEntries(String tenantId,
                                      String moduleKey,
                                      String sourceType,
                                      String actor,
                                      LocalDate fromDate,
                                      LocalDate toDate,
                                      String format,
                                      boolean includeArchived,
                                      String actorUsername) {
        String resolvedFormat = defaultIfBlank(format, "CSV").toUpperCase(Locale.ROOT);
        List<AuditLedgerRecordDto> entries = auditLedgerQueryRepository.findEntries(
                tenantId,
                moduleKey,
                sourceType,
                actor,
                fromDate,
                toDate,
                includeArchived,
                EXPORT_LIMIT
        );
        String filename = "audit-export-" + LocalDate.now() + "." + ("JSON".equals(resolvedFormat) ? "json" : "csv");
        byte[] payload = "JSON".equals(resolvedFormat)
                ? buildJson(entries)
                : buildCsv(entries);

        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("COMPLIANCE")
                .moduleKey("AUDIT")
                .entityType("AUDIT_EXPORT")
                .entityKey(filename)
                .actionType("AUDIT_EXPORT")
                .title("Audit evidence export generated")
                .summary(entries.size() + " records exported as " + resolvedFormat)
                .actor(actorUsername)
                .reason(buildExportReason(moduleKey, sourceType, fromDate, toDate))
                .status("EXPORTED")
                .referenceKey(filename)
                .controlFamily("SOX")
                .evidenceTags(List.of("AUDIT", "EXPORT", resolvedFormat))
                .metadata(buildExportMetadata(moduleKey, sourceType, includeArchived, entries.size()))
                .build());

        return new ExportBundle(
                filename,
                "JSON".equals(resolvedFormat) ? "application/json" : "text/csv;charset=UTF-8",
                payload
        );
    }

    @Transactional(readOnly = true)
    public SoxAuditReportResponse getSoxReport(String tenantId,
                                               String moduleKey,
                                               LocalDate fromDate,
                                               LocalDate toDate) {
        LocalDate resolvedFrom = fromDate != null ? fromDate : LocalDate.now().minusDays(30);
        LocalDate resolvedTo = toDate != null ? toDate : LocalDate.now();
        TenantConfig tenant = tenantService.getTenant(tenantId);
        AuditRetentionPolicy policy = getOrDefaultPolicy(tenantId, "system");

        List<AuditLedgerRecordDto> entries = auditLedgerQueryRepository.findEntries(
                tenantId,
                moduleKey,
                null,
                null,
                resolvedFrom,
                resolvedTo,
                true,
                5000
        );
        List<ActivityRecordDto> recentEvidence = entries.stream()
                .sorted(Comparator.comparing(AuditLedgerRecordDto::getEventTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(12)
                .map(this::toActivityDto)
                .toList();

        SoxAuditSummaryDto summary = SoxAuditSummaryDto.builder()
                .periodStart(resolvedFrom.toString())
                .periodEnd(resolvedTo.toString())
                .totalAuditableActions(entries.size())
                .configurationChanges(countBySource(entries, "CONFIGURATION"))
                .securityAdminChanges(countBySource(entries, "SECURITY"))
                .exceptionControlActions(countBySource(entries, "EXCEPTION"))
                .operationalInterventions(countBySource(entries, "OPERATIONS"))
                .approvalDecisions(countByAction(entries, "APPROVED") + countByAction(entries, "REJECTED"))
                .exportsGenerated(countByAction(entries, "AUDIT_EXPORT"))
                .lastArchiveAt(archiveBatchRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId)
                        .map(AuditArchiveBatch::getCreatedAt)
                        .map(value -> TimezoneConverter.toDisplay(value.toString(), tenant))
                        .orElse(null))
                .legalHold(policy.isLegalHold())
                .build();

        List<SoxControlEvidenceDto> controls = List.of(
                buildControlEvidence("SOX-ACC-01", "Access Administration",
                        "Security Administration",
                        entries,
                        record -> "SECURITY".equalsIgnoreCase(record.getSourceType()),
                        tenant),
                buildControlEvidence("SOX-CON-02", "Configuration And Policy Changes",
                        "Platform Operations",
                        entries,
                        record -> "CONFIGURATION".equalsIgnoreCase(record.getSourceType())
                                || "SLA".equalsIgnoreCase(record.getSourceType())
                                || "COMPLIANCE".equalsIgnoreCase(record.getSourceType()),
                        tenant),
                buildControlEvidence("SOX-EXC-03", "Exception Resolution Governance",
                        "Retail Operations",
                        entries,
                        record -> "EXCEPTION".equalsIgnoreCase(record.getSourceType()),
                        tenant),
                buildControlEvidence("SOX-OPS-04", "Operational Intervention Review",
                        "Integration Operations",
                        entries,
                        record -> "OPERATIONS".equalsIgnoreCase(record.getSourceType()),
                        tenant),
                SoxControlEvidenceDto.builder()
                        .controlId("SOX-RET-05")
                        .controlTitle("Retention And Archive Readiness")
                        .controlOwner("Compliance Administration")
                        .status(policy.isLegalHold() || archiveBatchRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId).isPresent()
                                ? "READY" : "ATTENTION")
                        .evidenceCount(archiveEntryRepository.countByTenantId(tenantId))
                        .lastEvidenceAt(archiveBatchRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId)
                                .map(AuditArchiveBatch::getCreatedAt)
                                .map(value -> TimezoneConverter.toDisplay(value.toString(), tenant))
                                .orElse(null))
                        .narrative(policy.isLegalHold()
                                ? "Legal hold is active and retention deletion risk is blocked."
                                : "Retention policy is configured and archive packages are available for auditor evidence.")
                        .sampleReference(policy.getPolicyName())
                        .build()
        );

        return SoxAuditReportResponse.builder()
                .summary(summary)
                .controls(controls)
                .recentEvidence(recentEvidence)
                .build();
    }

    private SoxControlEvidenceDto buildControlEvidence(String controlId,
                                                       String title,
                                                       String owner,
                                                       List<AuditLedgerRecordDto> entries,
                                                       java.util.function.Predicate<AuditLedgerRecordDto> predicate,
                                                       TenantConfig tenant) {
        List<AuditLedgerRecordDto> matching = entries.stream()
                .filter(predicate)
                .sorted(Comparator.comparing(AuditLedgerRecordDto::getEventTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        AuditLedgerRecordDto latest = matching.isEmpty() ? null : matching.get(0);
        return SoxControlEvidenceDto.builder()
                .controlId(controlId)
                .controlTitle(title)
                .controlOwner(owner)
                .status(matching.isEmpty() ? "ATTENTION" : "READY")
                .evidenceCount(matching.size())
                .lastEvidenceAt(latest != null && latest.getEventTimestamp() != null
                        ? TimezoneConverter.toDisplay(latest.getEventTimestamp().toString(), tenant)
                        : null)
                .narrative(matching.isEmpty()
                        ? "No audit evidence was found in the selected period."
                        : matching.size() + " audit records support this control in the selected period.")
                .sampleReference(latest != null ? latest.getReferenceKey() : null)
                .build();
    }

    private long countBySource(List<AuditLedgerRecordDto> entries, String sourceType) {
        return entries.stream()
                .filter(entry -> sourceType.equalsIgnoreCase(entry.getSourceType()))
                .count();
    }

    private long countByAction(List<AuditLedgerRecordDto> entries, String actionType) {
        return entries.stream()
                .filter(entry -> actionType.equalsIgnoreCase(entry.getActionType()))
                .count();
    }

    private byte[] buildJson(List<AuditLedgerRecordDto> entries) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(entries);
        } catch (Exception ex) {
            return "[]".getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] buildCsv(List<AuditLedgerRecordDto> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("when,source,module,entityType,entityKey,action,actor,title,summary,reason,status,referenceKey,controlFamily,archived,eventHash\n");
        for (AuditLedgerRecordDto entry : entries) {
            builder.append(csv(entry.getEventTimestamp() != null ? entry.getEventTimestamp().toString() : null)).append(',')
                    .append(csv(entry.getSourceType())).append(',')
                    .append(csv(entry.getModuleKey())).append(',')
                    .append(csv(entry.getEntityType())).append(',')
                    .append(csv(entry.getEntityKey())).append(',')
                    .append(csv(entry.getActionType())).append(',')
                    .append(csv(entry.getActor())).append(',')
                    .append(csv(entry.getTitle())).append(',')
                    .append(csv(entry.getSummary())).append(',')
                    .append(csv(entry.getReason())).append(',')
                    .append(csv(entry.getStatus())).append(',')
                    .append(csv(entry.getReferenceKey())).append(',')
                    .append(csv(entry.getControlFamily())).append(',')
                    .append(csv(String.valueOf(entry.isArchived()))).append(',')
                    .append(csv(entry.getEventHash()))
                    .append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        String safe = Objects.toString(value, "");
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String buildExportReason(String moduleKey,
                                     String sourceType,
                                     LocalDate fromDate,
                                     LocalDate toDate) {
        List<String> parts = new ArrayList<>();
        if (trimToNull(moduleKey) != null) {
            parts.add("module=" + moduleKey);
        }
        if (trimToNull(sourceType) != null) {
            parts.add("source=" + sourceType);
        }
        if (fromDate != null) {
            parts.add("from=" + fromDate);
        }
        if (toDate != null) {
            parts.add("to=" + toDate);
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private java.util.Map<String, Object> buildExportMetadata(String moduleKey,
                                                              String sourceType,
                                                              boolean includeArchived,
                                                              int entryCount) {
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (trimToNull(moduleKey) != null) {
            metadata.put("moduleKey", moduleKey);
        }
        if (trimToNull(sourceType) != null) {
            metadata.put("sourceType", sourceType);
        }
        metadata.put("includeArchived", includeArchived);
        metadata.put("entryCount", entryCount);
        return metadata;
    }

    private ActivityRecordDto toActivityDto(AuditLedgerRecordDto record) {
        return ActivityRecordDto.builder()
                .sourceType(record.getSourceType())
                .moduleKey(record.getModuleKey())
                .actionType(record.getActionType())
                .actor(record.getActor())
                .title(record.getTitle())
                .summary(record.getSummary())
                .reason(record.getReason())
                .referenceKey(record.getReferenceKey())
                .status(record.getStatus())
                .controlFamily(record.getControlFamily())
                .eventHash(record.getEventHash())
                .archived(record.isArchived())
                .eventTimestamp(record.getEventTimestamp())
                .build();
    }

    private AuditRetentionPolicy getOrDefaultPolicy(String tenantId, String actorUsername) {
        return retentionPolicyRepository.findByTenantId(tenantId)
                .orElseGet(() -> AuditRetentionPolicy.builder()
                        .tenantId(tenantId)
                        .policyName(defaultPolicyName(tenantId))
                        .minimumRetentionDays(2555)
                        .archiveAfterDays(90)
                        .defaultExportFormat("CSV")
                        .createdBy(actorUsername)
                        .updatedBy(actorUsername)
                        .build());
    }

    private AuditRetentionPolicyDto toDto(AuditRetentionPolicy policy, TenantConfig tenant) {
        return AuditRetentionPolicyDto.builder()
                .policyName(policy.getPolicyName())
                .minimumRetentionDays(policy.getMinimumRetentionDays())
                .archiveAfterDays(policy.getArchiveAfterDays())
                .legalHold(policy.isLegalHold())
                .defaultExportFormat(policy.getDefaultExportFormat())
                .notes(policy.getNotes())
                .createdBy(policy.getCreatedBy())
                .updatedBy(policy.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(policy.getCreatedAt()), tenant))
                .updatedAt(TimezoneConverter.toDisplay(valueOrNull(policy.getUpdatedAt()), tenant))
                .build();
    }

    private AuditArchiveBatchDto toDto(AuditArchiveBatch batch, TenantConfig tenant) {
        return AuditArchiveBatchDto.builder()
                .id(batch.getId())
                .policyName(batch.getPolicyName())
                .archiveReason(batch.getArchiveReason())
                .exportFormat(batch.getExportFormat())
                .entryCount(batch.getEntryCount())
                .fromEventAt(TimezoneConverter.toDisplay(valueOrNull(batch.getFromEventAt()), tenant))
                .toEventAt(TimezoneConverter.toDisplay(valueOrNull(batch.getToEventAt()), tenant))
                .createdBy(batch.getCreatedBy())
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(batch.getCreatedAt()), tenant))
                .build();
    }

    private int maxPositive(Integer requested, Integer current, int fallback) {
        if (requested != null && requested > 0) {
            return requested;
        }
        if (current != null && current > 0) {
            return current;
        }
        return fallback;
    }

    private String defaultPolicyName(String tenantId) {
        return tenantService.getTenant(tenantId).getTenantName() + " Audit Retention Policy";
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

    public record ExportBundle(String filename, String contentType, byte[] payload) {
    }
}
