package com.recon.api.service;

import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionEscalationPolicy;
import com.recon.api.domain.ExceptionEscalationPolicyCenterResponse;
import com.recon.api.domain.ExceptionEscalationPolicyCenterSummaryDto;
import com.recon.api.domain.ExceptionEscalationPolicyDto;
import com.recon.api.domain.SaveExceptionEscalationPolicyRequest;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ExceptionEscalationPolicyRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExceptionEscalationService {

    private final ExceptionEscalationPolicyRepository policyRepository;
    private final ExceptionCaseRepository caseRepository;
    private final ExceptionBusinessValueService exceptionBusinessValueService;
    private final ExceptionScopeResolver exceptionScopeResolver;
    private final ExceptionSlaService exceptionSlaService;
    private final TenantService tenantService;
    private final ExceptionImpactScoringService exceptionImpactScoringService;
    private final ExceptionCaseAuditService exceptionCaseAuditService;
    private final ExceptionCollaborationService exceptionCollaborationService;
    private final ExceptionWorkflowService exceptionWorkflowService;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.exceptions.escalation-enabled:true}")
    private boolean escalationEnabled;

    @Transactional(readOnly = true)
    public ExceptionEscalationPolicyCenterResponse getCenter(String tenantId,
                                                             List<String> allowedReconViews,
                                                             String reconView) {
        String normalizedReconView = normalizeNullable(reconView);
        List<ExceptionEscalationPolicy> policies = policyRepository.findForCenter(tenantId, normalizedReconView).stream()
                .filter(policy -> allowedReconViews == null
                        || allowedReconViews.isEmpty()
                        || allowedReconViews.contains(policy.getReconView()))
                .toList();
        List<ExceptionCase> activeCases = caseRepository.findActiveCasesForAging(
                tenantId,
                normalizedReconView,
                LocalDateTime.now().minusDays(35)
        );

        return ExceptionEscalationPolicyCenterResponse.builder()
                .summary(ExceptionEscalationPolicyCenterSummaryDto.builder()
                        .activePolicies(policies.stream().filter(ExceptionEscalationPolicy::isActive).count())
                        .currentlyEscalatedCases(activeCases.stream()
                                .filter(exceptionImpactScoringService::isEscalated)
                                .count())
                        .slaTriggeredPolicies(policies.stream().filter(ExceptionEscalationPolicy::isTriggerOnSlaBreach).count())
                        .agingTriggeredPolicies(policies.stream().filter(policy -> positive(policy.getAgingHours())).count())
                        .inactivityTriggeredPolicies(policies.stream().filter(policy -> positive(policy.getInactivityHours())).count())
                        .build())
                .policies(policies.stream().map(this::toDto).toList())
                .build();
    }

    @Transactional
    public ExceptionEscalationPolicyDto savePolicy(String tenantId,
                                                   UUID policyId,
                                                   SaveExceptionEscalationPolicyRequest request,
                                                   String actorUsername) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String policyName = trimToNull(request != null ? request.getPolicyName() : null);
        String reconView = normalizeRequired(request != null ? request.getReconView() : null, "reconView");
        if (policyName == null) {
            throw new IllegalArgumentException("policyName is required");
        }
        if (!(Boolean.TRUE.equals(request != null && request.isTriggerOnSlaBreach())
                || positive(request != null ? request.getAgingHours() : null)
                || positive(request != null ? request.getInactivityHours() : null)
                || positive(request != null ? request.getMinImpactScore() : null)
                || trimToNull(request != null ? request.getMinSeverity() : null) != null)) {
            throw new IllegalArgumentException("Configure at least one escalation trigger");
        }
        if (trimToNull(request != null ? request.getEscalateToUsername() : null) == null
                && trimToNull(request != null ? request.getEscalateToRoleName() : null) == null
                && trimToNull(request != null ? request.getTargetSeverity() : null) == null
                && trimToNull(request != null ? request.getNoteTemplate() : null) == null) {
            throw new IllegalArgumentException("Configure at least one escalation action");
        }

        ExceptionEscalationPolicy policy = policyId == null
                ? ExceptionEscalationPolicy.builder()
                .tenantId(tenantId)
                .createdBy(actorUsername)
                .build()
                : policyRepository.findByIdAndTenantId(policyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Escalation policy not found"));

        policy.setPolicyName(policyName);
        policy.setReconView(reconView);
        policy.setMinSeverity(normalizeNullable(request.getMinSeverity()));
        policy.setMinImpactScore(normalizePositive(request.getMinImpactScore()));
        policy.setTriggerOnSlaBreach(request.isTriggerOnSlaBreach());
        policy.setAgingHours(normalizePositive(request.getAgingHours()));
        policy.setInactivityHours(normalizePositive(request.getInactivityHours()));
        policy.setEscalateToUsername(trimToNull(request.getEscalateToUsername()));
        policy.setEscalateToRoleName(trimToNull(request.getEscalateToRoleName()));
        policy.setTargetSeverity(normalizeNullable(request.getTargetSeverity()));
        policy.setNoteTemplate(trimToNull(request.getNoteTemplate()));
        policy.setActive(request.isActive());
        policy.setDescription(trimToNull(request.getDescription()));
        policy.setUpdatedBy(actorUsername);

        ExceptionEscalationPolicy saved = policyRepository.save(policy);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("COMPLIANCE")
                .moduleKey(saved.getReconView())
                .entityType("EXCEPTION_ESCALATION_POLICY")
                .entityKey(saved.getId().toString())
                .actionType(policyId == null ? "POLICY_CREATED" : "POLICY_UPDATED")
                .title("Exception escalation policy saved")
                .summary(saved.getPolicyName())
                .actor(actorUsername)
                .status(saved.isActive() ? "ACTIVE" : "INACTIVE")
                .referenceKey(saved.getId().toString())
                .controlFamily("SOX")
                .evidenceTags(java.util.List.of("EXCEPTION", "ESCALATION_POLICY"))
                .afterState(saved)
                .build());
        return toDto(saved);
    }

    @Transactional
    public void deletePolicy(String tenantId, UUID policyId) {
        ExceptionEscalationPolicy policy = policyRepository.findByIdAndTenantId(policyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Escalation policy not found"));
        policyRepository.delete(policy);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("COMPLIANCE")
                .moduleKey(policy.getReconView())
                .entityType("EXCEPTION_ESCALATION_POLICY")
                .entityKey(policy.getId().toString())
                .actionType("POLICY_DELETED")
                .title("Exception escalation policy deleted")
                .summary(policy.getPolicyName())
                .status("DELETED")
                .referenceKey(policy.getId().toString())
                .controlFamily("SOX")
                .evidenceTags(java.util.List.of("EXCEPTION", "ESCALATION_POLICY"))
                .beforeState(policy)
                .build());
    }

    @Scheduled(fixedDelayString = "${app.exceptions.escalation-interval-ms:300000}")
    @Transactional
    public void runEscalations() {
        if (!escalationEnabled) {
            return;
        }
        List<ExceptionCase> activeCases = caseRepository.findRecentActiveCases(LocalDateTime.now().minusDays(35));
        Map<String, List<ExceptionCase>> groupedCases = activeCases.stream()
                .collect(Collectors.groupingBy(this::contextKey));
        for (List<ExceptionCase> caseGroup : groupedCases.values()) {
            if (caseGroup.isEmpty()) {
                continue;
            }
            EvaluationContext context = buildContext(caseGroup);
            for (ExceptionCase exceptionCase : caseGroup) {
                evaluateCase(exceptionCase, "system", context);
            }
        }
    }

    @Transactional
    public ExceptionCase evaluateCase(ExceptionCase exceptionCase, String actorUsername) {
        if (exceptionCase == null || isTerminal(exceptionCase.getCaseStatus())) {
            return exceptionCase;
        }
        List<ExceptionCase> relatedCases = caseRepository.findActiveCasesForAging(
                exceptionCase.getTenantId(),
                exceptionCase.getReconView(),
                LocalDateTime.now().minusDays(35)
        );
        return evaluateCase(exceptionCase, actorUsername, buildContext(relatedCases));
    }

    @Transactional
    public void clearEscalationOnClosure(ExceptionCase exceptionCase) {
        if (exceptionCase == null || !exceptionImpactScoringService.isEscalated(exceptionCase)) {
            return;
        }
        exceptionCase.setEscalationState("NONE");
    }

    private ExceptionCase evaluateCase(ExceptionCase exceptionCase,
                                       String actorUsername,
                                       EvaluationContext context) {
        if (exceptionCase == null || isTerminal(exceptionCase.getCaseStatus())) {
            return exceptionCase;
        }
        String reconView = normalizeRequired(exceptionCase.getReconView(), "reconView");
        List<ExceptionEscalationPolicy> policies = context.policiesByReconView().getOrDefault(reconView, List.of());
        if (policies.isEmpty()) {
            return exceptionCase;
        }

        String storeKey = normalizeStoreKey(exceptionScopeResolver.resolveStoreId(exceptionCase));
        long storeOpenCaseCount = context.storeOpenCaseCounts().getOrDefault(storeKey, 1L);
        long repeatIssueCount = context.repeatIssueCounts().getOrDefault(
                repeatIssueKey(storeKey, exceptionCase.getReconStatus()),
                1L
        );
        BusinessValueContextDto businessValue = context.businessValueByCase().get(
                exceptionBusinessValueService.caseKey(exceptionCase)
        );
        boolean pendingApproval = exceptionWorkflowService.getPendingApproval(exceptionCase).isPresent();
        ExceptionImpactScoringService.ImpactAssessment impact = exceptionImpactScoringService.assess(
                exceptionCase,
                businessValue,
                storeOpenCaseCount,
                repeatIssueCount,
                pendingApproval
        );

        Optional<ExceptionEscalationPolicy> matchedPolicy = policies.stream()
                .filter(policy -> matches(policy, exceptionCase, impact.score()))
                .findFirst();
        if (matchedPolicy.isEmpty()) {
            return exceptionCase;
        }

        ExceptionEscalationPolicy policy = matchedPolicy.get();
        if (exceptionImpactScoringService.isEscalated(exceptionCase)
                && Objects.equals(exceptionCase.getEscalationPolicyId(), policy.getId())) {
            return exceptionCase;
        }

        String summary = buildEscalationSummary(policy, exceptionCase, impact);
        applyPolicy(policy, exceptionCase, actorUsername, summary);
        return exceptionCase;
    }

    private void applyPolicy(ExceptionEscalationPolicy policy,
                             ExceptionCase exceptionCase,
                             String actorUsername,
                             String summary) {
        if (trimToNull(policy.getEscalateToUsername()) != null) {
            exceptionCase.setAssigneeUsername(policy.getEscalateToUsername());
        }
        if (trimToNull(policy.getEscalateToRoleName()) != null) {
            exceptionCase.setAssignedRoleName(policy.getEscalateToRoleName());
        }
        if (trimToNull(policy.getTargetSeverity()) != null
                && severityRank(policy.getTargetSeverity()) > severityRank(exceptionCase.getSeverity())) {
            exceptionCase.setSeverity(policy.getTargetSeverity());
        }
        exceptionCase.setEscalationState("ESCALATED");
        exceptionCase.setEscalationCount(Optional.ofNullable(exceptionCase.getEscalationCount()).orElse(0) + 1);
        exceptionCase.setLastEscalatedBy(actorUsername);
        exceptionCase.setLastEscalatedAt(LocalDateTime.now());
        exceptionCase.setEscalationPolicyId(policy.getId());
        exceptionCase.setEscalationPolicyName(policy.getPolicyName());
        exceptionCase.setEscalationReason(summary);
        exceptionCase.setUpdatedBy(actorUsername);
        exceptionSlaService.applyRule(exceptionCase);
        caseRepository.save(exceptionCase);
        exceptionCaseAuditService.recordEscalation(exceptionCase, actorUsername, summary);
        exceptionCollaborationService.syncEscalatedCase(exceptionCase, actorUsername);
    }

    private EvaluationContext buildContext(List<ExceptionCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return new EvaluationContext(Map.of(), Map.of(), Map.of(), Map.of());
        }
        String tenantId = cases.get(0).getTenantId();
        Set<String> reconViews = cases.stream()
                .map(ExceptionCase::getReconView)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, List<ExceptionEscalationPolicy>> policiesByReconView = reconViews.stream()
                .collect(Collectors.toMap(
                        view -> view,
                        view -> policyRepository.findActivePolicies(tenantId, view).stream()
                                .sorted(policyPriority())
                                .toList()
                ));
        boolean requiresImpactContext = policiesByReconView.values().stream()
                .flatMap(List::stream)
                .anyMatch(policy -> positive(policy.getMinImpactScore()));
        return new EvaluationContext(
                cases.stream()
                        .map(exceptionCase -> normalizeStoreKey(exceptionScopeResolver.resolveStoreId(exceptionCase)))
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(storeKey -> storeKey, Collectors.counting())),
                cases.stream()
                        .collect(Collectors.groupingBy(
                                exceptionCase -> repeatIssueKey(
                                        normalizeStoreKey(exceptionScopeResolver.resolveStoreId(exceptionCase)),
                                        exceptionCase.getReconStatus()),
                                Collectors.counting())),
                requiresImpactContext ? safeBusinessValueContext(tenantId, cases) : Map.of(),
                policiesByReconView
        );
    }

    private Comparator<ExceptionEscalationPolicy> policyPriority() {
        return Comparator
                .comparingInt((ExceptionEscalationPolicy policy) -> Optional.ofNullable(policy.getMinImpactScore()).orElse(0))
                .reversed()
                .thenComparingInt(policy -> severityRank(policy.getMinSeverity()))
                .reversed()
                .thenComparing(ExceptionEscalationPolicy::isTriggerOnSlaBreach, Comparator.reverseOrder())
                .thenComparingInt(policy -> Optional.ofNullable(policy.getAgingHours()).orElse(0))
                .reversed()
                .thenComparingInt(policy -> Optional.ofNullable(policy.getInactivityHours()).orElse(0))
                .reversed()
                .thenComparing(ExceptionEscalationPolicy::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean matches(ExceptionEscalationPolicy policy, ExceptionCase exceptionCase, int impactScore) {
        if (!policy.isActive()) {
            return false;
        }
        if (trimToNull(policy.getMinSeverity()) != null
                && severityRank(exceptionCase.getSeverity()) < severityRank(policy.getMinSeverity())) {
            return false;
        }
        if (positive(policy.getMinImpactScore()) && impactScore < policy.getMinImpactScore()) {
            return false;
        }
        if (policy.isTriggerOnSlaBreach() && !"BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(exceptionCase))) {
            return false;
        }
        if (positive(policy.getAgingHours()) && exceptionSlaService.resolveAgeHours(exceptionCase) < policy.getAgingHours()) {
            return false;
        }
        if (positive(policy.getInactivityHours()) && resolveInactivityHours(exceptionCase) < policy.getInactivityHours()) {
            return false;
        }
        return true;
    }

    private long resolveInactivityHours(ExceptionCase exceptionCase) {
        LocalDateTime baseline = Optional.ofNullable(exceptionCase.getUpdatedAt())
                .orElse(exceptionCase.getCreatedAt());
        if (baseline == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(baseline, LocalDateTime.now()).toHours());
    }

    private String buildEscalationSummary(ExceptionEscalationPolicy policy,
                                          ExceptionCase exceptionCase,
                                          ExceptionImpactScoringService.ImpactAssessment impact) {
        List<String> reasons = new java.util.ArrayList<>();
        if (policy.isTriggerOnSlaBreach() && "BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(exceptionCase))) {
            reasons.add("SLA breached");
        }
        if (positive(policy.getAgingHours())) {
            reasons.add("age " + exceptionSlaService.resolveAgeHours(exceptionCase) + "h");
        }
        if (positive(policy.getInactivityHours())) {
            reasons.add("inactive " + resolveInactivityHours(exceptionCase) + "h");
        }
        if (positive(policy.getMinImpactScore())) {
            reasons.add("impact " + impact.score() + " (" + impact.band() + ")");
        }
        if (trimToNull(policy.getNoteTemplate()) != null) {
            reasons.add(policy.getNoteTemplate());
        }
        if (reasons.isEmpty()) {
            reasons.add("Escalation policy matched");
        }
        return policy.getPolicyName() + " / " + String.join(" / ", reasons);
    }

    private Map<String, BusinessValueContextDto> safeBusinessValueContext(String tenantId, List<ExceptionCase> cases) {
        try {
            return exceptionBusinessValueService.enrichCases(tenantId, cases);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private ExceptionEscalationPolicyDto toDto(ExceptionEscalationPolicy policy) {
        return ExceptionEscalationPolicyDto.builder()
                .id(policy.getId())
                .policyName(policy.getPolicyName())
                .reconView(policy.getReconView())
                .minSeverity(policy.getMinSeverity())
                .minImpactScore(policy.getMinImpactScore())
                .triggerOnSlaBreach(policy.isTriggerOnSlaBreach())
                .agingHours(policy.getAgingHours())
                .inactivityHours(policy.getInactivityHours())
                .escalateToUsername(policy.getEscalateToUsername())
                .escalateToRoleName(policy.getEscalateToRoleName())
                .targetSeverity(policy.getTargetSeverity())
                .noteTemplate(policy.getNoteTemplate())
                .active(policy.isActive())
                .description(policy.getDescription())
                .createdBy(policy.getCreatedBy())
                .updatedBy(policy.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(policy.getCreatedAt()), tenantService.getTenant(policy.getTenantId())))
                .updatedAt(TimezoneConverter.toDisplay(valueOrNull(policy.getUpdatedAt()), tenantService.getTenant(policy.getTenantId())))
                .build();
    }

    private boolean isTerminal(String status) {
        String normalized = normalizeNullable(status);
        return "RESOLVED".equals(normalized) || "IGNORED".equals(normalized);
    }

    private int severityRank(String severity) {
        return switch (normalizeNullable(severity)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private Integer normalizePositive(Integer value) {
        return positive(value) ? value : null;
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeStoreKey(String storeId) {
        String trimmed = trimToNull(storeId);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String repeatIssueKey(String storeKey, String reconStatus) {
        return (storeKey == null ? "UNKNOWN" : storeKey)
                + "::"
                + (trimToNull(reconStatus) == null ? "UNKNOWN" : reconStatus.trim().toUpperCase(Locale.ROOT));
    }

    private String contextKey(ExceptionCase exceptionCase) {
        return defaultIfBlank(exceptionCase.getTenantId(), "TENANT")
                + "::"
                + defaultIfBlank(exceptionCase.getReconView(), "RECON_VIEW");
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String valueOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    private record EvaluationContext(Map<String, Long> storeOpenCaseCounts,
                                     Map<String, Long> repeatIssueCounts,
                                     Map<String, BusinessValueContextDto> businessValueByCase,
                                     Map<String, List<ExceptionEscalationPolicy>> policiesByReconView) {
    }
}
