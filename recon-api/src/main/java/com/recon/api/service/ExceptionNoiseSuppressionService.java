package com.recon.api.service;

import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionSuppressionAudit;
import com.recon.api.domain.ExceptionSuppressionAuditDto;
import com.recon.api.domain.ExceptionSuppressionRule;
import com.recon.api.domain.ExceptionSuppressionRuleDto;
import com.recon.api.domain.ExceptionSuppressionSummaryDto;
import com.recon.api.domain.SaveExceptionSuppressionRuleRequest;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ExceptionSuppressionAuditRepository;
import com.recon.api.repository.ExceptionSuppressionRuleRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExceptionNoiseSuppressionService {

    private final ExceptionSuppressionRuleRepository ruleRepository;
    private final ExceptionSuppressionAuditRepository auditRepository;
    private final ExceptionCaseRepository caseRepository;
    private final TenantService tenantService;

    @Transactional(readOnly = true)
    public List<ExceptionSuppressionRuleDto> getRules(String tenantId,
                                                      String reconView,
                                                      Collection<String> allowedReconViews) {
        String normalizedReconView = normalizeNullable(reconView);
        Set<String> normalizedAllowedViews = normalizeReconViews(allowedReconViews);
        boolean enforceAllowedScope = normalizedReconView == null && allowedReconViews != null;
        return ruleRepository.findForAutomationCenter(tenantId, normalizedReconView).stream()
                .filter(rule -> matchesAllowedReconView(rule.getReconView(), normalizedReconView, normalizedAllowedViews, enforceAllowedScope))
                .map(this::toRuleDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExceptionSuppressionRuleDto> getRules(String tenantId, String reconView) {
        return getRules(tenantId, reconView, null);
    }

    @Transactional
    public ExceptionSuppressionRuleDto saveRule(String tenantId,
                                                UUID ruleId,
                                                SaveExceptionSuppressionRuleRequest request,
                                                String actorUsername,
                                                Collection<String> allowedReconViews) {
        String ruleName = trimToNull(request != null ? request.getRuleName() : null);
        if (ruleName == null) {
            throw new IllegalArgumentException("ruleName is required");
        }
        String reconView = normalizeRequired(request != null ? request.getReconView() : null, "reconView");
        requireAllowedReconView(reconView, allowedReconViews);
        String actionType = normalizeActionType(request != null ? request.getActionType() : null);
        String maxSeverity = normalizeSeverity(request != null ? request.getMaxSeverity() : null);

        ExceptionSuppressionRule rule = ruleId == null
                ? ExceptionSuppressionRule.builder()
                .tenantId(tenantId)
                .createdBy(actorUsername)
                .build()
                : ruleRepository.findByIdAndTenantId(ruleId, tenantId)
                .map(existing -> {
                    requireAllowedReconView(existing.getReconView(), allowedReconViews);
                    return existing;
                })
                .orElseThrow(() -> new IllegalArgumentException("Suppression rule not found"));

        rule.setRuleName(ruleName);
        rule.setReconView(reconView);
        rule.setReconStatus(normalizeNullable(request != null ? request.getReconStatus() : null));
        rule.setMaxSeverity(maxSeverity);
        rule.setRootCauseCategory(normalizeNullable(request != null ? request.getRootCauseCategory() : null));
        rule.setReasonCode(normalizeNullable(request != null ? request.getReasonCode() : null));
        rule.setStoreId(trimToNull(request != null ? request.getStoreId() : null));
        rule.setActionType(actionType);
        rule.setMaxValueAtRisk(positiveOrNull(request != null ? request.getMaxValueAtRisk() : null));
        rule.setMinRepeatCount(positiveOrNull(request != null ? request.getMinRepeatCount() : null));
        rule.setActive(request == null || request.isActive());
        rule.setDescription(trimToNull(request != null ? request.getDescription() : null));
        rule.setUpdatedBy(actorUsername);

        return toRuleDto(ruleRepository.save(rule));
    }

    @Transactional
    public ExceptionSuppressionRuleDto saveRule(String tenantId,
                                                UUID ruleId,
                                                SaveExceptionSuppressionRuleRequest request,
                                                String actorUsername) {
        return saveRule(tenantId, ruleId, request, actorUsername, null);
    }

    @Transactional
    public void deleteRule(String tenantId, UUID ruleId, Collection<String> allowedReconViews) {
        ExceptionSuppressionRule rule = ruleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Suppression rule not found"));
        requireAllowedReconView(rule.getReconView(), allowedReconViews);
        ruleRepository.delete(rule);
    }

    @Transactional
    public void deleteRule(String tenantId, UUID ruleId) {
        deleteRule(tenantId, ruleId, null);
    }

    @Transactional(readOnly = true)
    public ExceptionSuppressionSummaryDto getSummary(String tenantId,
                                                     String reconView,
                                                     Collection<String> allowedReconViews) {
        String normalizedReconView = normalizeNullable(reconView);
        Set<String> normalizedAllowedViews = normalizeReconViews(allowedReconViews);
        boolean enforceAllowedScope = normalizedReconView == null && allowedReconViews != null;
        List<ExceptionSuppressionRule> rules = ruleRepository.findForAutomationCenter(tenantId, null);
        List<ExceptionSuppressionAudit> recent = auditRepository.findRecentByTenantId(
                tenantId,
                LocalDateTime.now().minusDays(7));
        List<ExceptionSuppressionRule> scopedRules = rules.stream()
                .filter(rule -> matchesAllowedReconView(rule.getReconView(), normalizedReconView, normalizedAllowedViews, enforceAllowedScope))
                .toList();
        List<ExceptionSuppressionAudit> scopedRecent = recent.stream()
                .filter(audit -> matchesAllowedReconView(audit.getReconView(), normalizedReconView, normalizedAllowedViews, enforceAllowedScope))
                .toList();
        return ExceptionSuppressionSummaryDto.builder()
                .ruleCount(scopedRules.size())
                .activeRuleCount(scopedRules.stream().filter(ExceptionSuppressionRule::isActive).count())
                .autoResolvedLast7Days(scopedRecent.stream()
                        .filter(audit -> "AUTO_RESOLVE".equalsIgnoreCase(audit.getActionType()))
                        .count())
                .suppressedLast7Days(scopedRecent.stream()
                        .filter(audit -> "SUPPRESS_QUEUE".equalsIgnoreCase(audit.getActionType()))
                        .count())
                .build();
    }

    @Transactional(readOnly = true)
    public ExceptionSuppressionSummaryDto getSummary(String tenantId) {
        return getSummary(tenantId, null, null);
    }

    @Transactional(readOnly = true)
    public List<ExceptionSuppressionAuditDto> getRecentActivity(String tenantId,
                                                                String reconView,
                                                                Collection<String> allowedReconViews) {
        String normalizedReconView = normalizeNullable(reconView);
        Set<String> normalizedAllowedViews = normalizeReconViews(allowedReconViews);
        boolean enforceAllowedScope = normalizedReconView == null && allowedReconViews != null;
        return auditRepository.findTop25ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(audit -> matchesAllowedReconView(audit.getReconView(), normalizedReconView, normalizedAllowedViews, enforceAllowedScope))
                .map(this::toAuditDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExceptionSuppressionAuditDto> getRecentActivity(String tenantId) {
        return getRecentActivity(tenantId, null, null);
    }

    @Transactional(readOnly = true)
    public Optional<SuppressionDecision> evaluateSuppression(ExceptionCase exceptionCase,
                                                             BusinessValueContextDto businessValue) {
        if (exceptionCase == null
                || isClosed(exceptionCase)
                || trimToNull(exceptionCase.getTenantId()) == null
                || trimToNull(exceptionCase.getReconView()) == null) {
            return Optional.empty();
        }

        List<ExceptionSuppressionRule> rules = ruleRepository.findActiveRules(
                exceptionCase.getTenantId(),
                normalizeNullable(exceptionCase.getReconView()));
        if (rules.isEmpty()) {
            return Optional.empty();
        }

        int repeatCount = resolveRepeatCount(exceptionCase);
        return rules.stream()
                .filter(rule -> matchesRule(rule, exceptionCase, businessValue, repeatCount))
                .sorted(Comparator
                        .comparingInt(this::specificity).reversed()
                        .thenComparing(ExceptionSuppressionRule::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .map(rule -> new SuppressionDecision(
                        rule.getId(),
                        rule.getRuleName(),
                        normalizeActionType(rule.getActionType()),
                        "AUTO_RESOLVE".equalsIgnoreCase(rule.getActionType()) ? "RESOLVED" : "IGNORED",
                        repeatCount,
                        buildResultMessage(rule, businessValue, repeatCount)
                ));
    }

    @Transactional
    public void recordApplied(ExceptionCase exceptionCase,
                              SuppressionDecision decision,
                              String actorUsername) {
        if (exceptionCase == null || decision == null) {
            return;
        }
        auditRepository.save(ExceptionSuppressionAudit.builder()
                .tenantId(exceptionCase.getTenantId())
                .caseId(exceptionCase.getId())
                .transactionKey(exceptionCase.getTransactionKey())
                .reconView(exceptionCase.getReconView())
                .ruleId(decision.ruleId())
                .ruleName(decision.ruleName())
                .actionType(decision.actionType())
                .resultStatus("APPLIED")
                .resultMessage(decision.resultMessage())
                .createdBy(actorUsername)
                .build());
    }

    public ExceptionSuppressionRuleDto toRuleDto(ExceptionSuppressionRule rule) {
        return ExceptionSuppressionRuleDto.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .reconView(rule.getReconView())
                .reconStatus(rule.getReconStatus())
                .maxSeverity(rule.getMaxSeverity())
                .rootCauseCategory(rule.getRootCauseCategory())
                .reasonCode(rule.getReasonCode())
                .storeId(rule.getStoreId())
                .actionType(rule.getActionType())
                .maxValueAtRisk(rule.getMaxValueAtRisk())
                .minRepeatCount(rule.getMinRepeatCount())
                .active(rule.isActive())
                .description(rule.getDescription())
                .createdBy(rule.getCreatedBy())
                .updatedBy(rule.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(rule.getCreatedAt()), tenantService.getTenant(rule.getTenantId())))
                .updatedAt(TimezoneConverter.toDisplay(valueOrNull(rule.getUpdatedAt()), tenantService.getTenant(rule.getTenantId())))
                .build();
    }

    public ExceptionSuppressionAuditDto toAuditDto(ExceptionSuppressionAudit audit) {
        return ExceptionSuppressionAuditDto.builder()
                .id(audit.getId())
                .caseId(audit.getCaseId())
                .transactionKey(audit.getTransactionKey())
                .reconView(audit.getReconView())
                .ruleId(audit.getRuleId())
                .ruleName(audit.getRuleName())
                .actionType(audit.getActionType())
                .resultStatus(audit.getResultStatus())
                .resultMessage(audit.getResultMessage())
                .createdBy(audit.getCreatedBy())
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(audit.getCreatedAt()), tenantService.getTenant(audit.getTenantId())))
                .build();
    }

    private boolean matchesRule(ExceptionSuppressionRule rule,
                                ExceptionCase exceptionCase,
                                BusinessValueContextDto businessValue,
                                int repeatCount) {
        return severityRank(exceptionCase.getSeverity()) <= severityRank(rule.getMaxSeverity())
                && matchesNullable(rule.getReconStatus(), exceptionCase.getReconStatus())
                && matchesNullable(rule.getRootCauseCategory(), exceptionCase.getRootCauseCategory())
                && matchesNullable(rule.getReasonCode(), exceptionCase.getReasonCode())
                && matchesNullable(rule.getStoreId(), exceptionCase.getStoreId())
                && matchesValueThreshold(rule.getMaxValueAtRisk(), businessValue != null ? businessValue.getValueAtRisk() : null)
                && matchesRepeatThreshold(rule.getMinRepeatCount(), repeatCount);
    }

    private boolean matchesValueThreshold(BigDecimal maxValueAtRisk, BigDecimal valueAtRisk) {
        return maxValueAtRisk == null
                || valueAtRisk == null
                || valueAtRisk.compareTo(maxValueAtRisk) <= 0;
    }

    private boolean matchesRepeatThreshold(Integer minRepeatCount, int repeatCount) {
        return minRepeatCount == null || repeatCount >= minRepeatCount;
    }

    private int resolveRepeatCount(ExceptionCase exceptionCase) {
        return (int) caseRepository.findForRecurrenceAnalytics(
                        exceptionCase.getTenantId(),
                        normalizeNullable(exceptionCase.getReconView()),
                        LocalDateTime.now().minusDays(30))
                .stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), exceptionCase.getId()))
                .filter(candidate -> Objects.equals(normalizeNullable(candidate.getReconStatus()), normalizeNullable(exceptionCase.getReconStatus())))
                .filter(candidate -> Objects.equals(normalizeNullable(candidate.getReasonCode()), normalizeNullable(exceptionCase.getReasonCode())))
                .filter(candidate -> Objects.equals(trimToNull(candidate.getStoreId()), trimToNull(exceptionCase.getStoreId())))
                .count() + 1;
    }

    private String buildResultMessage(ExceptionSuppressionRule rule,
                                      BusinessValueContextDto businessValue,
                                      int repeatCount) {
        String statusText = "AUTO_RESOLVE".equalsIgnoreCase(rule.getActionType())
                ? "Auto-resolved by suppression rule"
                : "Noise-suppressed by rule";
        StringBuilder builder = new StringBuilder(statusText).append(": ").append(rule.getRuleName());
        if (repeatCount > 1) {
            builder.append(" | repeat count ").append(repeatCount);
        }
        if (businessValue != null && businessValue.getValueAtRisk() != null) {
            builder.append(" | value at risk ").append(businessValue.getValueAtRisk().stripTrailingZeros().toPlainString());
        }
        return builder.toString();
    }

    private int specificity(ExceptionSuppressionRule rule) {
        int score = 0;
        if (trimToNull(rule.getReconStatus()) != null) score++;
        if (trimToNull(rule.getRootCauseCategory()) != null) score++;
        if (trimToNull(rule.getReasonCode()) != null) score++;
        if (trimToNull(rule.getStoreId()) != null) score++;
        if (rule.getMaxValueAtRisk() != null) score++;
        if (rule.getMinRepeatCount() != null) score++;
        return score;
    }

    private int severityRank(String severity) {
        return switch (normalizeNullable(severity)) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }

    private boolean isClosed(ExceptionCase exceptionCase) {
        String status = normalizeNullable(exceptionCase.getCaseStatus());
        return "RESOLVED".equals(status) || "IGNORED".equals(status);
    }

    private boolean matchesNullable(String expected, String actual) {
        String normalizedExpected = normalizeNullable(expected);
        return normalizedExpected == null || Objects.equals(normalizedExpected, normalizeNullable(actual));
    }

    private Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private BigDecimal positiveOrNull(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    private String normalizeActionType(String actionType) {
        String normalized = normalizeNullable(actionType);
        if (!"AUTO_RESOLVE".equals(normalized) && !"SUPPRESS_QUEUE".equals(normalized)) {
            return "SUPPRESS_QUEUE";
        }
        return normalized;
    }

    private String normalizeSeverity(String severity) {
        String normalized = normalizeNullable(severity);
        return normalized == null ? "LOW" : normalized;
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

    private boolean matchesAllowedReconView(String value,
                                            String requestedReconView,
                                            Set<String> allowedReconViews,
                                            boolean enforceAllowedScope) {
        String normalizedValue = normalizeNullable(value);
        if (requestedReconView != null) {
            return Objects.equals(requestedReconView, normalizedValue);
        }
        if (!enforceAllowedScope) {
            return true;
        }
        return normalizedValue != null && allowedReconViews.contains(normalizedValue);
    }

    private Set<String> normalizeReconViews(Collection<String> reconViews) {
        if (reconViews == null) {
            return Set.of();
        }
        return reconViews.stream()
                .map(this::normalizeNullable)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void requireAllowedReconView(String reconView, Collection<String> allowedReconViews) {
        if (allowedReconViews == null) {
            return;
        }
        String normalizedReconView = normalizeRequired(reconView, "reconView");
        Set<String> normalizedAllowedViews = normalizeReconViews(allowedReconViews);
        if (!normalizedAllowedViews.contains(normalizedReconView)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized for this reconciliation module");
        }
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String valueOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    public record SuppressionDecision(UUID ruleId,
                                      String ruleName,
                                      String actionType,
                                      String targetStatus,
                                      int repeatCount,
                                      String resultMessage) {
    }
}
