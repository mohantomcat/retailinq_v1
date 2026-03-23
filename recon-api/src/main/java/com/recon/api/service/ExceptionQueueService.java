package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionQueueItemDto;
import com.recon.api.domain.ExceptionQueueResponse;
import com.recon.api.domain.ExceptionQueueSummaryDto;
import com.recon.api.domain.ExceptionStoreIncidentDto;
import com.recon.api.domain.User;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.UserRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExceptionQueueService {

    private final ExceptionCaseRepository caseRepository;
    private final ExceptionSlaService exceptionSlaService;
    private final TenantService tenantService;
    private final UserRepository userRepository;
    private final ExceptionScopeResolver exceptionScopeResolver;
    private final ExceptionWorkflowService exceptionWorkflowService;
    private final ExceptionBusinessValueService exceptionBusinessValueService;
    private final ExceptionOperationalOwnershipService exceptionOperationalOwnershipService;
    private final ExceptionIncidentClassifierService exceptionIncidentClassifierService;
    private final KnownIssueService knownIssueService;
    private final ExceptionImpactScoringService exceptionImpactScoringService;

    @Transactional(readOnly = true)
    public ExceptionQueueResponse getQueue(String tenantId,
                                           String username,
                                           java.util.Collection<String> accessibleStoreIds,
                                           String reconView,
                                           String queueType,
                                           String caseStatus,
                                           String severity,
                                           String assignee,
                                           String assignedRole,
                                           String search) {
        List<ExceptionCase> cases = caseRepository.findActiveCasesForAging(
                tenantId,
                reconView == null || reconView.isBlank() ? null : reconView.toUpperCase(Locale.ROOT),
                LocalDateTime.now().minusDays(35)
        ).stream()
                .filter(exceptionCase -> matchesStoreScope(exceptionCase, accessibleStoreIds))
                .toList();
        Set<String> userRoles = resolveUserRoles(tenantId, username);
        Map<String, Long> storeOpenCaseCounts = buildStoreOpenCaseCounts(cases);
        Map<String, Long> repeatIssueCounts = buildRepeatIssueCounts(cases);
        Map<String, BusinessValueContextDto> businessValueByCase = exceptionBusinessValueService.enrichCases(tenantId, cases);
        KnownIssueService.KnownIssueCatalogContext knownIssueContext = knownIssueService.loadActiveContext(
                tenantId,
                cases.stream().map(ExceptionCase::getReconView).collect(Collectors.toSet())
        );

        List<RankedCase> rankedCases = cases.stream()
                .map(exceptionCase -> rankCase(
                        exceptionCase,
                        businessValueByCase.get(exceptionBusinessValueService.caseKey(exceptionCase)),
                        storeOpenCaseCounts,
                        repeatIssueCounts))
                .toList();

        Predicate<RankedCase> filter = rankedCase -> true;
        filter = filter.and(matchQueueType(username, userRoles, queueType));
        filter = filter.and(matchValue(ExceptionCase::getCaseStatus, caseStatus));
        filter = filter.and(matchValue(ExceptionCase::getSeverity, severity));
        filter = filter.and(matchValue(ExceptionCase::getAssigneeUsername, assignee));
        filter = filter.and(matchValue(ExceptionCase::getAssignedRoleName, assignedRole));
        filter = filter.and(matchSearch(search));

        List<RankedCase> filtered = rankedCases.stream()
                .filter(filter)
                .sorted(Comparator
                        .comparingInt(RankedCase::impactScore).reversed()
                        .thenComparing((RankedCase rankedCase) -> isOverdue(rankedCase.exceptionCase()) ? 0 : 1)
                        .thenComparing(rankedCase -> exceptionSlaService.resolveDueAt(rankedCase.exceptionCase()),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(rankedCase -> rankedCase.exceptionCase().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return ExceptionQueueResponse.builder()
                .summary(buildSummary(rankedCases, username, userRoles))
                .storeIncidents(buildStoreIncidents(filtered, knownIssueContext))
                .items(filtered.stream().map(rankedCase -> toQueueItem(rankedCase, knownIssueContext)).toList())
                .build();
    }

    private ExceptionQueueSummaryDto buildSummary(List<RankedCase> rankedCases,
                                                  String username,
                                                  Set<String> userRoles) {
        List<ExceptionCase> cases = rankedCases.stream().map(RankedCase::exceptionCase).toList();
        return ExceptionQueueSummaryDto.builder()
                .openCases(cases.size())
                .unassignedCases(cases.stream().filter(this::isUnassigned).count())
                .myCases(cases.stream().filter(exceptionCase -> equalsIgnoreCase(exceptionCase.getAssigneeUsername(), username)).count())
                .myTeamCases(cases.stream().filter(exceptionCase ->
                        exceptionCase.getAssignedRoleName() != null && userRoles.stream()
                                .anyMatch(role -> equalsIgnoreCase(role, exceptionCase.getAssignedRoleName()))).count())
                .overdueCases(cases.stream().filter(this::isOverdue).count())
                .highSeverityCases(cases.stream().filter(exceptionCase ->
                        "HIGH".equalsIgnoreCase(exceptionCase.getSeverity())
                                || "CRITICAL".equalsIgnoreCase(exceptionCase.getSeverity())).count())
                .highImpactCases(rankedCases.stream().filter(rankedCase -> rankedCase.impactScore() >= exceptionImpactScoringService.highImpactThreshold()).count())
                .escalatedCases(cases.stream().filter(exceptionImpactScoringService::isEscalated).count())
                .unassignedHighImpactCases(rankedCases.stream()
                        .filter(rankedCase -> rankedCase.impactScore() >= exceptionImpactScoringService.highImpactThreshold())
                        .filter(rankedCase -> isUnassigned(rankedCase.exceptionCase()))
                        .count())
                .storeIncidentCount(countStoreIncidents(rankedCases))
                .storesAtRisk(rankedCases.stream()
                        .filter(rankedCase -> rankedCase.impactScore() >= exceptionImpactScoringService.highImpactThreshold()
                                || rankedCase.storeOpenCaseCount() >= 3)
                        .map(rankedCase -> normalizeStoreKey(exceptionScopeResolver.resolveStoreId(rankedCase.exceptionCase())))
                        .filter(Objects::nonNull)
                        .distinct()
                        .count())
                .ownershipGapCases(cases.stream().filter(exceptionOperationalOwnershipService::hasOwnershipGap).count())
                .actionDueCases(cases.stream().filter(exceptionOperationalOwnershipService::isActionDue).count())
                .build();
    }

    private Predicate<RankedCase> matchQueueType(String username,
                                                 Set<String> userRoles,
                                                 String queueType) {
        String normalized = normalizeQueueType(queueType);
        return switch (normalized) {
            case "UNASSIGNED" -> rankedCase -> isUnassigned(rankedCase.exceptionCase());
            case "MINE" -> rankedCase -> equalsIgnoreCase(rankedCase.exceptionCase().getAssigneeUsername(), username);
            case "MY_TEAM" -> rankedCase -> rankedCase.exceptionCase().getAssignedRoleName() != null
                    && userRoles.stream().anyMatch(role -> equalsIgnoreCase(role, rankedCase.exceptionCase().getAssignedRoleName()));
            case "OVERDUE" -> rankedCase -> isOverdue(rankedCase.exceptionCase());
            case "HIGH_SEVERITY" -> rankedCase -> "HIGH".equalsIgnoreCase(rankedCase.exceptionCase().getSeverity())
                    || "CRITICAL".equalsIgnoreCase(rankedCase.exceptionCase().getSeverity());
            case "HIGH_IMPACT" -> rankedCase -> rankedCase.impactScore() >= exceptionImpactScoringService.highImpactThreshold();
            case "ESCALATED" -> rankedCase -> exceptionImpactScoringService.isEscalated(rankedCase.exceptionCase());
            case "OWNERSHIP_GAP" -> rankedCase -> exceptionOperationalOwnershipService.hasOwnershipGap(rankedCase.exceptionCase());
            case "ACTION_DUE" -> rankedCase -> exceptionOperationalOwnershipService.isActionDue(rankedCase.exceptionCase());
            default -> rankedCase -> true;
        };
    }

    private Predicate<RankedCase> matchValue(java.util.function.Function<ExceptionCase, String> getter,
                                             String expected) {
        if (expected == null || expected.isBlank()) {
            return rankedCase -> true;
        }
        return rankedCase -> equalsIgnoreCase(getter.apply(rankedCase.exceptionCase()), expected);
    }

    private Predicate<RankedCase> matchSearch(String search) {
        if (search == null || search.isBlank()) {
            return rankedCase -> true;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        return rankedCase -> java.util.stream.Stream.of(
                        rankedCase.exceptionCase().getTransactionKey(),
                        exceptionScopeResolver.resolveStoreId(rankedCase.exceptionCase()),
                        exceptionScopeResolver.resolveWkstnId(rankedCase.exceptionCase()),
                        rankedCase.exceptionCase().getAssigneeUsername(),
                        rankedCase.exceptionCase().getAssignedRoleName(),
                        rankedCase.exceptionCase().getNextAction(),
                        rankedCase.exceptionCase().getHandoffNote(),
                        rankedCase.exceptionCase().getReasonCode(),
                        rankedCase.exceptionCase().getNotes(),
                        resolveIncidentBucket(rankedCase.exceptionCase()).title(),
                        rankedCase.priorityReason(),
                        rankedCase.impactBand())
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalized));
    }

    private ExceptionQueueItemDto toQueueItem(RankedCase rankedCase,
                                              KnownIssueService.KnownIssueCatalogContext knownIssueContext) {
        ExceptionCase exceptionCase = rankedCase.exceptionCase();
        var tenant = tenantService.getTenant(exceptionCase.getTenantId());
        IncidentBucket incidentBucket = resolveIncidentBucket(exceptionCase);
        return ExceptionQueueItemDto.builder()
                .id(exceptionCase.getId())
                .transactionKey(exceptionCase.getTransactionKey())
                .reconView(exceptionCase.getReconView())
                .reconStatus(exceptionCase.getReconStatus())
                .caseStatus(exceptionCase.getCaseStatus())
                .reasonCode(exceptionCase.getReasonCode())
                .severity(exceptionCase.getSeverity())
                .assigneeUsername(exceptionCase.getAssigneeUsername())
                .assignedRoleName(exceptionCase.getAssignedRoleName())
                .nextAction(exceptionCase.getNextAction())
                .nextActionDueAt(TimezoneConverter.toDisplay(valueOrNull(exceptionCase.getNextActionDueAt()), tenant))
                .lastHandoffAt(TimezoneConverter.toDisplay(valueOrNull(exceptionCase.getLastHandoffAt()), tenant))
                .ownershipStatus(exceptionOperationalOwnershipService.resolveOwnershipStatus(exceptionCase))
                .storeId(exceptionScopeResolver.resolveStoreId(exceptionCase))
                .wkstnId(exceptionScopeResolver.resolveWkstnId(exceptionCase))
                .businessDate(exceptionScopeResolver.resolveBusinessDate(exceptionCase) != null
                        ? exceptionScopeResolver.resolveBusinessDate(exceptionCase).toString()
                        : null)
                .autoAssigned(exceptionCase.isAutoAssigned())
                .routingRuleName(exceptionCase.getRoutingRuleName())
                .playbookName(exceptionCase.getPlaybookName())
                .notes(exceptionCase.getNotes())
                .escalationState(exceptionCase.getEscalationState())
                .escalationCount(exceptionCase.getEscalationCount())
                .lastEscalatedAt(TimezoneConverter.toDisplay(valueOrNull(exceptionCase.getLastEscalatedAt()), tenant))
                .escalationPolicyName(exceptionCase.getEscalationPolicyName())
                .slaStatus(exceptionSlaService.evaluateSlaStatus(exceptionCase))
                .approvalState(rankedCase.pendingApproval() ? "PENDING" : "NONE")
                .slaTargetMinutes(exceptionCase.getSlaTargetMinutes())
                .dueAt(TimezoneConverter.toDisplay(valueOrNull(exceptionSlaService.resolveDueAt(exceptionCase)), tenant))
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(exceptionCase.getCreatedAt()), tenant))
                .updatedAt(TimezoneConverter.toDisplay(valueOrNull(exceptionCase.getUpdatedAt()), tenant))
                .storeIncidentKey(storeIncidentKey(exceptionCase, incidentBucket))
                .storeIncidentTitle(incidentBucket.title())
                .businessValue(rankedCase.businessValue())
                .matchedKnownIssue(knownIssueService.matchCase(exceptionCase, knownIssueContext))
                .impactScore(rankedCase.impactScore())
                .impactBand(rankedCase.impactBand())
                .priorityReason(rankedCase.priorityReason())
                .storeOpenCaseCount(rankedCase.storeOpenCaseCount())
                .repeatIssueCount(rankedCase.repeatIssueCount())
                .caseAgeHours(rankedCase.caseAgeHours())
                .build();
    }

    private List<ExceptionStoreIncidentDto> buildStoreIncidents(List<RankedCase> rankedCases,
                                                                KnownIssueService.KnownIssueCatalogContext knownIssueContext) {
        if (rankedCases.isEmpty()) {
            return List.of();
        }

        Map<String, List<RankedCase>> grouped = rankedCases.stream()
                .collect(Collectors.groupingBy(this::storeIncidentKey, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> rankIncident(entry.getKey(), entry.getValue(), knownIssueContext))
                .sorted(Comparator
                        .comparingInt((RankedIncident incident) -> incident.dto().getImpactScore()).reversed()
                        .thenComparing(RankedIncident::startedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RankedIncident::latestUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(RankedIncident::dto)
                .toList();
    }

    private RankedIncident rankIncident(String incidentKey,
                                        List<RankedCase> rankedCases,
                                        KnownIssueService.KnownIssueCatalogContext knownIssueContext) {
        List<RankedCase> ordered = rankedCases.stream()
                .sorted(Comparator
                        .comparingInt(RankedCase::impactScore).reversed()
                        .thenComparing((RankedCase rankedCase) -> severityRank(rankedCase.exceptionCase().getSeverity()), Comparator.reverseOrder())
                        .thenComparing(rankedCase -> rankedCase.exceptionCase().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        RankedCase topCase = ordered.get(0);
        ExceptionCase representativeCase = topCase.exceptionCase();
        IncidentBucket incidentBucket = resolveIncidentBucket(representativeCase);
        var tenant = tenantService.getTenant(representativeCase.getTenantId());
        long openCaseCount = rankedCases.size();
        long impactedTransactions = rankedCases.stream()
                .map(rankedCase -> rankedCase.exceptionCase().getTransactionKey())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long affectedRegisters = rankedCases.stream()
                .map(rankedCase -> trimToNull(exceptionScopeResolver.resolveWkstnId(rankedCase.exceptionCase())))
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long overdueCases = rankedCases.stream()
                .filter(rankedCase -> isOverdue(rankedCase.exceptionCase()))
                .count();
        long unassignedCases = rankedCases.stream()
                .filter(rankedCase -> isUnassigned(rankedCase.exceptionCase()))
                .count();
        LocalDateTime nextActionDueAt = rankedCases.stream()
                .map(rankedCase -> rankedCase.exceptionCase().getNextActionDueAt())
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime lastHandoffAt = rankedCases.stream()
                .map(rankedCase -> rankedCase.exceptionCase().getLastHandoffAt())
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        String lastHandoffBy = lastHandoffAt == null
                ? null
                : rankedCases.stream()
                .filter(rankedCase -> Objects.equals(rankedCase.exceptionCase().getLastHandoffAt(), lastHandoffAt))
                .map(rankedCase -> trimToNull(rankedCase.exceptionCase().getLastHandoffBy()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        BusinessValueContextDto incidentBusinessValue = exceptionBusinessValueService.aggregateIncidentValue(
                rankedCases.stream()
                        .map(RankedCase::businessValue)
                        .filter(Objects::nonNull)
                        .toList(),
                openCaseCount
        );
        LocalDateTime startedAt = rankedCases.stream()
                .map(rankedCase -> firstNonNullTimestamp(rankedCase.exceptionCase()))
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime latestUpdatedAt = rankedCases.stream()
                .map(rankedCase -> rankedCase.exceptionCase().getUpdatedAt())
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(startedAt);

        int incidentImpactScore = incidentImpactScore(
                topCase.impactScore(),
                impactedTransactions,
                overdueCases,
                unassignedCases,
                affectedRegisters,
                exceptionBusinessValueService.impactBoost(incidentBusinessValue)
        );

        String summary = incidentBucket.title() + " affecting " + impactedTransactions + " transactions";
        if (affectedRegisters > 1) {
            summary += " across " + affectedRegisters + " registers";
        }
        var matchedKnownIssue = knownIssueService.matchIncident(
                representativeCase,
                incidentBucket.title(),
                summary,
                knownIssueContext
        );

        return new RankedIncident(
                startedAt,
                latestUpdatedAt,
                ExceptionStoreIncidentDto.builder()
                        .incidentKey(incidentKey)
                        .storeId(defaultIfBlank(exceptionScopeResolver.resolveStoreId(representativeCase), "Unscoped"))
                        .reconView(representativeCase.getReconView())
                        .incidentTitle(incidentBucket.title())
                        .incidentSummary(summary)
                        .severity(defaultIfBlank(representativeCase.getSeverity(), "MEDIUM"))
                        .impactScore(incidentImpactScore)
                        .impactBand(exceptionImpactScoringService.impactBand(incidentImpactScore))
                        .openCaseCount(openCaseCount)
                        .impactedTransactions(impactedTransactions)
                        .affectedRegisters(affectedRegisters)
                        .overdueCases(overdueCases)
                        .unassignedCases(unassignedCases)
                        .ownerSummary(summarizeOwner(rankedCases, topCase))
                        .nextAction(summarizeNextAction(rankedCases, topCase))
                        .nextActionDueAt(TimezoneConverter.toDisplay(valueOrNull(nextActionDueAt), tenant))
                        .lastHandoffAt(TimezoneConverter.toDisplay(valueOrNull(lastHandoffAt), tenant))
                        .lastHandoffBy(lastHandoffBy)
                        .ownershipStatus(summarizeOwnershipStatus(rankedCases))
                        .priorityReason(topCase.priorityReason())
                        .startedAt(TimezoneConverter.toDisplay(valueOrNull(startedAt), tenant))
                        .latestUpdatedAt(TimezoneConverter.toDisplay(valueOrNull(latestUpdatedAt), tenant))
                        .businessValue(incidentBusinessValue)
                        .matchedKnownIssue(matchedKnownIssue)
                        .build()
        );
    }

    private RankedCase rankCase(ExceptionCase exceptionCase,
                                BusinessValueContextDto businessValue,
                                Map<String, Long> storeOpenCaseCounts,
                                Map<String, Long> repeatIssueCounts) {
        String storeId = exceptionScopeResolver.resolveStoreId(exceptionCase);
        long storeOpenCaseCount = storeOpenCaseCounts.getOrDefault(normalizeStoreKey(storeId), 1L);
        long repeatIssueCount = repeatIssueCounts.getOrDefault(repeatIssueKey(storeId, exceptionCase.getReconStatus()), 1L);
        long caseAgeHours = resolveCaseAgeHours(exceptionCase);
        boolean pendingApproval = exceptionWorkflowService.getPendingApproval(exceptionCase).isPresent();

        var impact = exceptionImpactScoringService.assess(
                exceptionCase,
                businessValue,
                storeOpenCaseCount,
                repeatIssueCount,
                pendingApproval
        );

        return new RankedCase(
                exceptionCase,
                businessValue,
                impact.score(),
                impact.band(),
                buildPriorityReason(exceptionCase, businessValue, storeOpenCaseCount, repeatIssueCount, caseAgeHours, pendingApproval),
                storeOpenCaseCount,
                repeatIssueCount,
                caseAgeHours,
                pendingApproval
        );
    }

    private long countStoreIncidents(List<RankedCase> rankedCases) {
        return rankedCases.stream()
                .map(this::storeIncidentKey)
                .distinct()
                .count();
    }

    private Map<String, Long> buildStoreOpenCaseCounts(List<ExceptionCase> cases) {
        return cases.stream()
                .map(exceptionCase -> normalizeStoreKey(exceptionScopeResolver.resolveStoreId(exceptionCase)))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(storeId -> storeId, Collectors.counting()));
    }

    private Map<String, Long> buildRepeatIssueCounts(List<ExceptionCase> cases) {
        return cases.stream()
                .collect(Collectors.groupingBy(
                        exceptionCase -> repeatIssueKey(
                                exceptionScopeResolver.resolveStoreId(exceptionCase),
                                exceptionCase.getReconStatus()),
                        Collectors.counting()));
    }

    private long resolveCaseAgeHours(ExceptionCase exceptionCase) {
        return exceptionSlaService.resolveAgeHours(exceptionCase);
    }

    private int incidentImpactScore(int topCaseImpactScore,
                                    long impactedTransactions,
                                    long overdueCases,
                                    long unassignedCases,
                                    long affectedRegisters,
                                    int businessValueBoost) {
        int score = topCaseImpactScore
                + (int) Math.min(18L, Math.max(0L, impactedTransactions - 1L) * 4L)
                + (int) Math.min(12L, overdueCases * 4L)
                + (int) Math.min(8L, unassignedCases * 2L)
                + businessValueBoost
                + (int) Math.min(6L, Math.max(0L, affectedRegisters - 1L) * 2L);
        return Math.min(100, score);
    }

    private String buildPriorityReason(ExceptionCase exceptionCase,
                                       BusinessValueContextDto businessValue,
                                       long storeOpenCaseCount,
                                       long repeatIssueCount,
                                       long caseAgeHours,
                                       boolean pendingApproval) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (businessValue != null && businessValue.getValueAtRisk() != null) {
            reasons.add("value at risk " + businessValue.getValueAtRisk().stripTrailingZeros().toPlainString());
        }
        if (isOverdue(exceptionCase)) {
            reasons.add("SLA breached");
        }
        if (isUnassigned(exceptionCase)) {
            reasons.add("unassigned");
        }
        if (exceptionOperationalOwnershipService.hasOwnershipGap(exceptionCase)) {
            reasons.add(trimToNull(exceptionCase.getAssigneeUsername()) == null
                    && trimToNull(exceptionCase.getAssignedRoleName()) == null
                    ? "owner missing"
                    : "next action missing");
        } else if (exceptionOperationalOwnershipService.isActionOverdue(exceptionCase)) {
            reasons.add("next action overdue");
        } else if (exceptionOperationalOwnershipService.isActionDue(exceptionCase)) {
            reasons.add("next action due soon");
        }
        if ("CRITICAL".equalsIgnoreCase(exceptionCase.getSeverity()) || "HIGH".equalsIgnoreCase(exceptionCase.getSeverity())) {
            reasons.add(exceptionCase.getSeverity() + " severity");
        }
        if (storeOpenCaseCount >= 3) {
            reasons.add("store has " + storeOpenCaseCount + " open cases");
        }
        if (repeatIssueCount >= 2) {
            reasons.add("repeat issue count " + repeatIssueCount);
        }
        if (businessValue != null && businessValue.getAffectedItemCount() != null && businessValue.getAffectedItemCount() >= 2) {
            reasons.add(businessValue.getAffectedItemCount() + " items affected");
        }
        if (caseAgeHours >= 24) {
            reasons.add("open for " + caseAgeHours + "h");
        }
        if (pendingApproval) {
            reasons.add("awaiting approval");
        }
        if (reasons.isEmpty()) {
            reasons.add("requires operational review");
        }
        return reasons.stream().limit(3).collect(Collectors.joining(", "));
    }

    private Set<String> resolveUserRoles(String tenantId, String username) {
        return userRepository.findByUsernameAndTenantId(username, tenantId)
                .map(User::getRoles)
                .orElse(Set.of())
                .stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());
    }

    private boolean isUnassigned(ExceptionCase exceptionCase) {
        return (exceptionCase.getAssigneeUsername() == null || exceptionCase.getAssigneeUsername().isBlank())
                && (exceptionCase.getAssignedRoleName() == null || exceptionCase.getAssignedRoleName().isBlank());
    }

    private boolean isOverdue(ExceptionCase exceptionCase) {
        return "BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(exceptionCase));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return Objects.toString(left, "").equalsIgnoreCase(Objects.toString(right, ""));
    }

    private boolean matchesStoreScope(ExceptionCase exceptionCase, java.util.Collection<String> accessibleStoreIds) {
        if (accessibleStoreIds == null || accessibleStoreIds.isEmpty()) {
            return true;
        }
        String storeId = normalizeStoreKey(exceptionScopeResolver.resolveStoreId(exceptionCase));
        return storeId != null && accessibleStoreIds.stream()
                .map(this::normalizeStoreKey)
                .filter(Objects::nonNull)
                .anyMatch(storeId::equals);
    }

    private int severityRank(String severity) {
        return switch (Objects.toString(severity, "").toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private String normalizeQueueType(String queueType) {
        return queueType == null || queueType.isBlank()
                ? "ALL"
                : queueType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStoreKey(String storeId) {
        String normalized = trimToNull(storeId);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String repeatIssueKey(String storeId, String reconStatus) {
        String normalizedStore = normalizeStoreKey(storeId);
        String normalizedStatus = trimToNull(reconStatus);
        return (normalizedStore == null ? "UNKNOWN" : normalizedStore)
                + "::"
                + (normalizedStatus == null ? "UNKNOWN" : normalizedStatus.toUpperCase(Locale.ROOT));
    }

    private String storeIncidentKey(RankedCase rankedCase) {
        return storeIncidentKey(rankedCase.exceptionCase(), resolveIncidentBucket(rankedCase.exceptionCase()));
    }

    private String storeIncidentKey(ExceptionCase exceptionCase, IncidentBucket incidentBucket) {
        String storeKey = normalizeStoreKey(exceptionScopeResolver.resolveStoreId(exceptionCase));
        return (storeKey == null ? "UNSCOPED" : storeKey)
                + "::"
                + defaultIfBlank(exceptionCase.getReconView(), "UNKNOWN")
                + "::"
                + incidentBucket.code();
    }

    private IncidentBucket resolveIncidentBucket(ExceptionCase exceptionCase) {
        ExceptionIncidentClassifierService.IncidentClassification classification = exceptionIncidentClassifierService.classify(exceptionCase);
        return new IncidentBucket(classification.code(), classification.title());
    }

    private String summarizeOwner(List<RankedCase> rankedCases, RankedCase topCase) {
        List<String> owners = rankedCases.stream()
                .map(rankedCase -> trimToNull(rankedCase.exceptionCase().getAssigneeUsername()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (owners.size() == 1) {
            return owners.get(0);
        }
        List<String> teams = rankedCases.stream()
                .map(rankedCase -> trimToNull(rankedCase.exceptionCase().getAssignedRoleName()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (owners.isEmpty() && teams.size() == 1) {
            return teams.get(0) + " team";
        }
        if (owners.size() > 1 || teams.size() > 1) {
            return "Mixed ownership";
        }
        return defaultIfBlank(trimToNull(topCase.exceptionCase().getAssigneeUsername()),
                defaultIfBlank(trimToNull(topCase.exceptionCase().getAssignedRoleName()), "Unassigned"));
    }

    private String summarizeNextAction(List<RankedCase> rankedCases, RankedCase topCase) {
        List<String> actions = rankedCases.stream()
                .map(rankedCase -> trimToNull(rankedCase.exceptionCase().getNextAction()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (actions.isEmpty()) {
            return null;
        }
        if (actions.size() == 1) {
            return actions.get(0);
        }
        return defaultIfBlank(trimToNull(topCase.exceptionCase().getNextAction()), "Multiple next actions");
    }

    private String summarizeOwnershipStatus(List<RankedCase> rankedCases) {
        if (rankedCases.stream().anyMatch(rankedCase -> exceptionOperationalOwnershipService.isActionOverdue(rankedCase.exceptionCase()))) {
            return "ACTION_OVERDUE";
        }
        if (rankedCases.stream().anyMatch(rankedCase -> exceptionOperationalOwnershipService.hasOwnershipGap(rankedCase.exceptionCase()))) {
            return "OWNERSHIP_GAP";
        }
        if (rankedCases.stream().anyMatch(rankedCase -> exceptionOperationalOwnershipService.isActionDue(rankedCase.exceptionCase()))) {
            return "ACTION_DUE_SOON";
        }
        return "ON_TRACK";
    }

    private LocalDateTime firstNonNullTimestamp(ExceptionCase exceptionCase) {
        return exceptionCase.getCreatedAt() != null
                ? exceptionCase.getCreatedAt()
                : exceptionCase.getUpdatedAt();
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

    private record RankedIncident(LocalDateTime startedAt,
                                  LocalDateTime latestUpdatedAt,
                                  ExceptionStoreIncidentDto dto) {
    }

    private record IncidentBucket(String code, String title) {
    }

    private record RankedCase(ExceptionCase exceptionCase,
                              BusinessValueContextDto businessValue,
                              int impactScore,
                              String impactBand,
                              String priorityReason,
                              long storeOpenCaseCount,
                              long repeatIssueCount,
                              long caseAgeHours,
                              boolean pendingApproval) {
    }
}
