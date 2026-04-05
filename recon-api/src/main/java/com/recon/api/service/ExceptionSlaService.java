package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionSlaRule;
import com.recon.api.domain.ExceptionSlaRuleDto;
import com.recon.api.domain.SaveExceptionSlaRuleRequest;
import com.recon.api.domain.SlaAgingBreakdownDto;
import com.recon.api.domain.SlaManagementResponse;
import com.recon.api.domain.SlaSummaryDto;
import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ExceptionSlaRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExceptionSlaService {

    private static final Map<String, Integer> DEFAULT_TARGET_MINUTES = Map.ofEntries(
            Map.entry("XSTORE_SIM|LOW", 1440),
            Map.entry("XSTORE_SIM|MEDIUM", 480),
            Map.entry("XSTORE_SIM|HIGH", 240),
            Map.entry("XSTORE_SIM|CRITICAL", 60),
            Map.entry("XSTORE_SIOCS|LOW", 720),
            Map.entry("XSTORE_SIOCS|MEDIUM", 240),
            Map.entry("XSTORE_SIOCS|HIGH", 120),
            Map.entry("XSTORE_SIOCS|CRITICAL", 60),
            Map.entry("SIOCS_MFCS|LOW", 720),
            Map.entry("SIOCS_MFCS|MEDIUM", 240),
            Map.entry("SIOCS_MFCS|HIGH", 120),
            Map.entry("SIOCS_MFCS|CRITICAL", 60),
            Map.entry("SIM_RMS|LOW", 720),
            Map.entry("SIM_RMS|MEDIUM", 240),
            Map.entry("SIM_RMS|HIGH", 120),
            Map.entry("SIM_RMS|CRITICAL", 60),
            Map.entry("SIM_MFCS|LOW", 720),
            Map.entry("SIM_MFCS|MEDIUM", 240),
            Map.entry("SIM_MFCS|HIGH", 120),
            Map.entry("SIM_MFCS|CRITICAL", 60),
            Map.entry("SIOCS_RMS|LOW", 720),
            Map.entry("SIOCS_RMS|MEDIUM", 240),
            Map.entry("SIOCS_RMS|HIGH", 120),
            Map.entry("SIOCS_RMS|CRITICAL", 60),
            Map.entry("XSTORE_XOCS|LOW", 720),
            Map.entry("XSTORE_XOCS|MEDIUM", 240),
            Map.entry("XSTORE_XOCS|HIGH", 120),
            Map.entry("XSTORE_XOCS|CRITICAL", 60)
    );

    private final ExceptionSlaRuleRepository slaRuleRepository;
    private final ExceptionCaseRepository caseRepository;
    private final TenantService tenantService;
    private final TenantOperatingModelService tenantOperatingModelService;
    private final AuditLedgerService auditLedgerService;
    private final ReconModuleService reconModuleService;

    @Transactional(readOnly = true)
    public SlaManagementResponse getManagementData(String tenantId,
                                                   String reconView,
                                                   Collection<String> allowedReconViews) {
        TenantConfig tenant = tenantService.getTenant(tenantId);
        Set<String> normalizedAllowedReconViews = normalizeAllowedReconViews(allowedReconViews);
        List<ExceptionCase> activeCases = getActiveCases(
                tenantId,
                reconView,
                List.of(),
                List.of(),
                normalizedAllowedReconViews);
        return SlaManagementResponse.builder()
                .rules(getRules(tenantId, reconView, normalizedAllowedReconViews))
                .summary(buildSummary(activeCases))
                .agingByAssignee(buildBreakdown(activeCases,
                        exceptionCase -> defaultLabel(exceptionCase.getAssigneeUsername(), "Unassigned"),
                        Function.identity(),
                        5))
                .agingByStore(buildBreakdown(activeCases,
                        exceptionCase -> defaultLabel(exceptionCase.getStoreId(), "Unknown Store"),
                        label -> "Store " + label,
                        5))
                .agingByModule(buildBreakdown(activeCases,
                        ExceptionCase::getReconView,
                        this::moduleLabel,
                        3))
                .operatingModel(tenantOperatingModelService.toDto(tenant))
                .build();
    }

    @Transactional(readOnly = true)
    public List<ExceptionSlaRuleDto> getRules(String tenantId, String reconView) {
        return getRules(tenantId, reconView, null);
    }

    @Transactional(readOnly = true)
    public List<ExceptionSlaRuleDto> getRules(String tenantId,
                                              String reconView,
                                              Collection<String> allowedReconViews) {
        List<ExceptionSlaRule> rules = reconView == null || reconView.isBlank()
                ? slaRuleRepository.findByTenantIdOrderByReconViewAscSeverityAsc(tenantId)
                : slaRuleRepository.findByTenantIdAndReconViewOrderBySeverityAsc(tenantId, normalizeReconView(reconView));
        Set<String> normalizedAllowedReconViews = allowedReconViews == null ? null : normalizeAllowedReconViews(allowedReconViews);
        return rules.stream()
                .filter(rule -> isAllowedReconView(rule.getReconView(), normalizedAllowedReconViews))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<ExceptionSlaRuleDto> saveRule(String tenantId,
                                              String reconView,
                                              String severity,
                                              SaveExceptionSlaRuleRequest request,
                                              String actorUsername) {
        String normalizedReconView = normalizeReconView(reconView);
        String normalizedSeverity = normalizeSeverity(severity);
        int targetMinutes = request != null && request.getTargetMinutes() != null
                ? request.getTargetMinutes()
                : resolveTargetMinutes(tenantId, normalizedReconView, normalizedSeverity);
        if (targetMinutes <= 0) {
            throw new IllegalArgumentException("targetMinutes must be greater than zero");
        }

        ExceptionSlaRule rule = slaRuleRepository
                .findByTenantIdAndReconViewAndSeverity(tenantId, normalizedReconView, normalizedSeverity)
                .orElseGet(() -> ExceptionSlaRule.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .reconView(normalizedReconView)
                        .severity(normalizedSeverity)
                        .createdBy(actorUsername)
                        .updatedBy(actorUsername)
                        .build());

        rule.setTargetMinutes(targetMinutes);
        rule.setDescription(trimToNull(request != null ? request.getDescription() : null));
        if (rule.getCreatedBy() == null) {
            rule.setCreatedBy(actorUsername);
        }
        rule.setUpdatedBy(actorUsername);
        ExceptionSlaRule savedRule = slaRuleRepository.save(rule);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SLA")
                .moduleKey(normalizedReconView)
                .entityType("EXCEPTION_SLA_RULE")
                .entityKey(normalizedReconView + ":" + normalizedSeverity)
                .actionType("SLA_RULE_UPDATED")
                .title("SLA rule updated")
                .summary(normalizedSeverity + " -> " + targetMinutes + " minutes")
                .actor(actorUsername)
                .status("ACTIVE")
                .referenceKey(normalizedReconView + ":" + normalizedSeverity)
                .controlFamily("SOX")
                .evidenceTags(java.util.List.of("SLA", "POLICY"))
                .afterState(savedRule)
                .build());

        return getRules(tenantId, normalizedReconView);
    }

    public void applyRule(ExceptionCase exceptionCase) {
        applyRule(exceptionCase,
                exceptionCase.getTenantId(),
                exceptionCase.getReconView(),
                exceptionCase.getSeverity());
    }

    public String evaluateSlaStatus(ExceptionCase exceptionCase) {
        if (exceptionCase == null || resolveDueAt(exceptionCase) == null) {
            return "NO_SLA";
        }
        if (isBreached(exceptionCase)) {
            return "BREACHED";
        }
        if (isDueSoon(exceptionCase)) {
            return "DUE_SOON";
        }
        return "WITHIN_SLA";
    }

    @Transactional(readOnly = true)
    public SlaSummaryDto getSlaSummary(String tenantId,
                                       String reconView,
                                       List<String> storeIds,
                                       List<String> wkstnIds) {
        return getSlaSummary(tenantId, reconView, storeIds, wkstnIds, null);
    }

    @Transactional(readOnly = true)
    public SlaSummaryDto getSlaSummary(String tenantId,
                                       String reconView,
                                       List<String> storeIds,
                                       List<String> wkstnIds,
                                       Collection<String> allowedReconViews) {
        return buildSummary(getActiveCases(tenantId, reconView, storeIds, wkstnIds, allowedReconViews));
    }

    private List<ExceptionCase> getActiveCases(String tenantId,
                                               String reconView,
                                               List<String> storeIds,
                                               List<String> wkstnIds) {
        return getActiveCases(tenantId, reconView, storeIds, wkstnIds, null);
    }

    private List<ExceptionCase> getActiveCases(String tenantId,
                                               String reconView,
                                               List<String> storeIds,
                                               List<String> wkstnIds,
                                               Collection<String> allowedReconViews) {
        Set<String> normalizedAllowedReconViews = allowedReconViews == null ? null : normalizeAllowedReconViews(allowedReconViews);
        List<ExceptionCase> cases = caseRepository.findActiveCasesForAging(
                tenantId,
                reconView == null || reconView.isBlank() ? null : normalizeReconView(reconView),
                LocalDateTime.now().minusDays(35)
        );
        return cases.stream()
                .filter(exceptionCase -> isAllowedReconView(exceptionCase.getReconView(), normalizedAllowedReconViews))
                .filter(exceptionCase -> storeIds == null || storeIds.isEmpty()
                        || storeIds.contains(exceptionCase.getStoreId()))
                .filter(exceptionCase -> wkstnIds == null || wkstnIds.isEmpty()
                        || wkstnIds.contains(exceptionCase.getWkstnId()))
                .toList();
    }

    private void applyRule(ExceptionCase exceptionCase,
                           String tenantId,
                           String reconView,
                           String severity) {
        String normalizedReconView = normalizeReconView(reconView);
        String normalizedSeverity = normalizeSeverity(severity);
        int targetMinutes = resolveTargetMinutes(tenantId, normalizedReconView, normalizedSeverity);
        LocalDateTime baseline = Optional.ofNullable(exceptionCase.getCreatedAt()).orElse(LocalDateTime.now());
        TenantConfig tenant = tenantService.getTenant(tenantId);
        LocalDateTime dueAt = tenantOperatingModelService.addBusinessMinutes(baseline, targetMinutes, tenant);
        LocalDateTime now = LocalDateTime.now();

        exceptionCase.setSeverity(normalizedSeverity);
        exceptionCase.setSlaTargetMinutes(targetMinutes);
        exceptionCase.setDueAt(dueAt);

        if (dueAt != null && dueAt.isBefore(now) && !isClosed(exceptionCase)) {
            if (exceptionCase.getBreachedAt() == null) {
                exceptionCase.setBreachedAt(now);
            }
        } else if (dueAt != null && !dueAt.isBefore(now)) {
            exceptionCase.setBreachedAt(null);
        }
    }

    public LocalDateTime resolveDueAt(ExceptionCase exceptionCase) {
        if (exceptionCase == null) {
            return null;
        }
        LocalDateTime baseline = Optional.ofNullable(exceptionCase.getCreatedAt())
                .orElse(exceptionCase.getUpdatedAt());
        if (baseline == null) {
            return exceptionCase.getDueAt();
        }
        int targetMinutes = Optional.ofNullable(exceptionCase.getSlaTargetMinutes())
                .filter(minutes -> minutes > 0)
                .orElseGet(() -> resolveTargetMinutes(
                        exceptionCase.getTenantId(),
                        normalizeReconView(exceptionCase.getReconView()),
                        normalizeSeverity(exceptionCase.getSeverity())));
        return tenantOperatingModelService.addBusinessMinutes(
                baseline,
                targetMinutes,
                tenantService.getTenant(exceptionCase.getTenantId()));
    }

    public long resolveAgeHours(ExceptionCase exceptionCase) {
        return ageHours(exceptionCase);
    }

    private int resolveTargetMinutes(String tenantId, String reconView, String severity) {
        return slaRuleRepository.findByTenantIdAndReconViewAndSeverity(tenantId, reconView, severity)
                .map(ExceptionSlaRule::getTargetMinutes)
                .orElseGet(() -> DEFAULT_TARGET_MINUTES.getOrDefault(reconView + "|" + severity, 240));
    }

    private Set<String> normalizeAllowedReconViews(Collection<String> allowedReconViews) {
        return allowedReconViews.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isAllowedReconView(String reconView, Set<String> allowedReconViews) {
        if (allowedReconViews == null || allowedReconViews.isEmpty()) {
            return allowedReconViews == null;
        }
        String normalizedReconView = trimToNull(reconView);
        if (normalizedReconView == null) {
            return false;
        }
        return allowedReconViews.contains(normalizedReconView.toUpperCase());
    }

    private ExceptionSlaRuleDto toDto(ExceptionSlaRule rule) {
        return ExceptionSlaRuleDto.builder()
                .id(rule.getId())
                .reconView(rule.getReconView())
                .severity(rule.getSeverity())
                .targetMinutes(rule.getTargetMinutes())
                .description(rule.getDescription())
                .createdBy(rule.getCreatedBy())
                .updatedBy(rule.getUpdatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private SlaSummaryDto buildSummary(List<ExceptionCase> cases) {
        long activeCases = cases.size();
        long breachedCases = cases.stream().filter(this::isBreached).count();
        long dueSoonCases = cases.stream().filter(this::isDueSoon).count();
        long withinSlaCases = activeCases - breachedCases - dueSoonCases;
        double breachRate = activeCases > 0
                ? Math.round((double) breachedCases / activeCases * 10000.0) / 100.0
                : 0.0;
        return SlaSummaryDto.builder()
                .activeCases(activeCases)
                .breachedCases(breachedCases)
                .dueSoonCases(dueSoonCases)
                .withinSlaCases(Math.max(withinSlaCases, 0))
                .breachRate(breachRate)
                .build();
    }

    private List<SlaAgingBreakdownDto> buildBreakdown(List<ExceptionCase> cases,
                                                      Function<ExceptionCase, String> keyResolver,
                                                      Function<String, String> labelResolver,
                                                      int limit) {
        Map<String, List<ExceptionCase>> grouped = cases.stream()
                .collect(Collectors.groupingBy(keyResolver, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ExceptionCase> groupCases = entry.getValue();
                    double averageAgeHours = groupCases.stream()
                            .mapToLong(this::ageHours)
                            .average()
                            .orElse(0.0);
                    return SlaAgingBreakdownDto.builder()
                            .key(entry.getKey())
                            .label(labelResolver.apply(entry.getKey()))
                            .openCases(groupCases.size())
                            .breachedCases(groupCases.stream().filter(this::isBreached).count())
                            .dueSoonCases(groupCases.stream().filter(this::isDueSoon).count())
                            .averageAgeHours(Math.round(averageAgeHours * 10.0) / 10.0)
                            .build();
                })
                .sorted(Comparator
                        .comparingLong(SlaAgingBreakdownDto::getBreachedCases).reversed()
                        .thenComparingLong(SlaAgingBreakdownDto::getOpenCases).reversed()
                        .thenComparingDouble(SlaAgingBreakdownDto::getAverageAgeHours).reversed())
                .limit(limit)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean isBreached(ExceptionCase exceptionCase) {
        LocalDateTime dueAt = resolveDueAt(exceptionCase);
        if (exceptionCase == null || dueAt == null || isClosed(exceptionCase)) {
            return false;
        }
        return dueAt.isBefore(LocalDateTime.now());
    }

    private boolean isDueSoon(ExceptionCase exceptionCase) {
        LocalDateTime dueAt = resolveDueAt(exceptionCase);
        if (exceptionCase == null || dueAt == null || isClosed(exceptionCase)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (dueAt.isBefore(now)) {
            return false;
        }
        int targetMinutes = Math.max(1, Optional.ofNullable(exceptionCase.getSlaTargetMinutes()).orElse(240));
        long soonWindowMinutes = Math.max(30, Math.min(targetMinutes / 4L, 240L));
        return Duration.between(now, dueAt).toMinutes() <= soonWindowMinutes;
    }

    private boolean isClosed(ExceptionCase exceptionCase) {
        return exceptionCase != null
                && exceptionCase.getCaseStatus() != null
                && List.of("RESOLVED", "IGNORED").contains(exceptionCase.getCaseStatus().toUpperCase());
    }

    private long ageHours(ExceptionCase exceptionCase) {
        LocalDateTime start = Optional.ofNullable(exceptionCase.getCreatedAt())
                .orElse(exceptionCase.getUpdatedAt());
        if (start == null) {
            return 0L;
        }
        return tenantOperatingModelService.businessAgeHours(
                start,
                tenantService.getTenant(exceptionCase.getTenantId()));
    }

    private String moduleLabel(String reconView) {
        return reconModuleService.resolveModuleLabel(reconView, defaultLabel(reconView, "Unknown Module"));
    }

    private String normalizeReconView(String reconView) {
        String normalized = trimToNull(reconView);
        if (normalized == null) {
            throw new IllegalArgumentException("reconView is required");
        }
        return normalized.toUpperCase();
    }

    private String normalizeSeverity(String severity) {
        String normalized = trimToNull(severity);
        return normalized == null ? "MEDIUM" : normalized.toUpperCase();
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
