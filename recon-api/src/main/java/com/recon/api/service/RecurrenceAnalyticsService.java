package com.recon.api.service;

import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.KnownIssueMatchDto;
import com.recon.api.domain.PreventionOpportunityDto;
import com.recon.api.domain.RecurrenceAnalyticsResponse;
import com.recon.api.domain.RecurrenceAnalyticsSummaryDto;
import com.recon.api.domain.RecurrenceBreakdownDto;
import com.recon.api.domain.RecurrenceTrendPointDto;
import com.recon.api.domain.RecurringIncidentPatternDto;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecurrenceAnalyticsService {

    private static final int DEFAULT_LOOKBACK_DAYS = 30;

    private final ExceptionCaseRepository exceptionCaseRepository;
    private final ExceptionScopeResolver exceptionScopeResolver;
    private final ExceptionBusinessValueService exceptionBusinessValueService;
    private final ExceptionIncidentClassifierService exceptionIncidentClassifierService;
    private final KnownIssueService knownIssueService;
    private final RootCauseTaxonomyService taxonomyService;
    private final TenantService tenantService;
    private final ReconModuleService reconModuleService;

    public RecurrenceAnalyticsResponse getAnalytics(String tenantId,
                                                    String reconView,
                                                    List<String> allowedReconViews,
                                                    java.util.Collection<String> accessibleStoreIds,
                                                    String storeId,
                                                    String fromBusinessDate,
                                                    String toBusinessDate) {
        LocalDate fromDate = normalizeDate(fromBusinessDate, LocalDate.now().minusDays(59));
        LocalDate toDate = normalizeDate(toBusinessDate, LocalDate.now());
        LocalDate lookbackFrom = fromDate.minusDays(DEFAULT_LOOKBACK_DAYS);

        List<ExceptionCase> rawCases = exceptionCaseRepository.findForRecurrenceAnalytics(
                tenantId,
                normalize(reconView),
                lookbackFrom.atStartOfDay()
        );

        List<String> scopedViews = resolveReconViews(reconView, allowedReconViews);
        String normalizedStore = normalizeStore(storeId);

        List<ExceptionCase> scopedCases = rawCases.stream()
                .filter(exceptionCase -> scopedViews.isEmpty()
                        || scopedViews.contains(normalize(exceptionCase.getReconView())))
                .filter(exceptionCase -> matchesStoreScope(exceptionCase, accessibleStoreIds))
                .filter(exceptionCase -> {
                    String resolvedStore = normalizeStore(exceptionScopeResolver.resolveStoreId(exceptionCase));
                    return normalizedStore == null || Objects.equals(normalizedStore, resolvedStore);
                })
                .filter(exceptionCase -> {
                    LocalDate effectiveDate = effectiveDate(exceptionCase);
                    return effectiveDate != null
                            && !effectiveDate.isBefore(lookbackFrom)
                            && !effectiveDate.isAfter(toDate);
                })
                .sorted(Comparator
                        .comparing(this::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ExceptionCase::getTransactionKey, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        Map<String, BusinessValueContextDto> businessValueByCase = exceptionBusinessValueService.enrichCases(tenantId, scopedCases);
        KnownIssueService.KnownIssueCatalogContext knownIssueContext = knownIssueService.loadActiveContext(
                tenantId,
                scopedCases.stream()
                        .map(ExceptionCase::getReconView)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );

        List<AnalyzedOccurrence> occurrences = analyze(scopedCases, businessValueByCase, knownIssueContext, fromDate, toDate);
        List<AnalyzedOccurrence> analysisCases = occurrences.stream()
                .filter(AnalyzedOccurrence::withinRange)
                .toList();
        List<AnalyzedOccurrence> repeatCases = analysisCases.stream()
                .filter(AnalyzedOccurrence::repeatWithin30Days)
                .toList();

        List<RecurringIncidentPatternDto> allRecurringIncidents = buildRecurringIncidents(tenantId, analysisCases);
        List<RecurringIncidentPatternDto> recurringIncidents = allRecurringIncidents.stream().limit(12).toList();
        List<PreventionOpportunityDto> preventionOpportunities = buildPreventionOpportunities(allRecurringIncidents);

        return RecurrenceAnalyticsResponse.builder()
                .summary(RecurrenceAnalyticsSummaryDto.builder()
                        .totalCases(analysisCases.size())
                        .repeatCases(repeatCases.size())
                        .repeatCaseRate(ratio(repeatCases.size(), analysisCases.size()))
                        .recurringIncidentPatterns(allRecurringIncidents.size())
                        .repeatAfterResolvedCases(analysisCases.stream().filter(AnalyzedOccurrence::recurredAfterResolved).count())
                        .repeatWithin7DaysCases(analysisCases.stream().filter(AnalyzedOccurrence::repeatWithin7Days).count())
                        .repeatWithin14DaysCases(analysisCases.stream().filter(AnalyzedOccurrence::repeatWithin14Days).count())
                        .repeatWithin30DaysCases(repeatCases.size())
                        .preventionOpportunityCount(preventionOpportunities.size())
                        .repeatBusinessValue(exceptionBusinessValueService.aggregateIncidentValue(
                                repeatCases.stream()
                                        .map(AnalyzedOccurrence::businessValue)
                                        .filter(Objects::nonNull)
                                        .toList(),
                                repeatCases.size()))
                        .build())
                .trend(buildTrend(analysisCases, fromDate, toDate))
                .recurringIncidents(recurringIncidents)
                .preventionOpportunities(preventionOpportunities)
                .topStores(buildBreakdown(
                        repeatCases,
                        occurrence -> defaultIfBlank(occurrence.storeId(), "UNSCOPED"),
                        key -> "UNSCOPED".equalsIgnoreCase(key) ? "Unscoped" : "Store " + key,
                        8))
                .topModules(buildBreakdown(
                        repeatCases,
                        occurrence -> defaultIfBlank(occurrence.reconView(), "UNKNOWN"),
                        this::moduleLabel,
                        6))
                .topReasons(buildBreakdown(
                        repeatCases,
                        AnalyzedOccurrence::reasonKey,
                        taxonomyService::labelForReason,
                        8))
                .topKnownIssues(buildBreakdown(
                        repeatCases,
                        occurrence -> occurrence.matchedKnownIssue() != null
                                ? defaultIfBlank(occurrence.matchedKnownIssue().getIssueKey(), "MATCHED")
                                : "UNMATCHED",
                        key -> resolveKnownIssueLabel(repeatCases, key),
                        8))
                .topOwnerTeams(buildBreakdown(
                        repeatCases,
                        occurrence -> defaultIfBlank(occurrence.ownerTeam(), "UNASSIGNED"),
                        key -> "UNASSIGNED".equalsIgnoreCase(key) ? "Unassigned" : key,
                        8))
                .build();
    }

    private List<AnalyzedOccurrence> analyze(List<ExceptionCase> scopedCases,
                                             Map<String, BusinessValueContextDto> businessValueByCase,
                                             KnownIssueService.KnownIssueCatalogContext knownIssueContext,
                                             LocalDate fromDate,
                                             LocalDate toDate) {
        List<CandidateOccurrence> candidates = scopedCases.stream()
                .map(exceptionCase -> toCandidate(exceptionCase, businessValueByCase, knownIssueContext))
                .sorted(Comparator
                        .comparing(CandidateOccurrence::clusterKey)
                        .thenComparing(CandidateOccurrence::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CandidateOccurrence::transactionKey, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        Map<String, List<CandidateOccurrence>> byCluster = candidates.stream()
                .collect(Collectors.groupingBy(CandidateOccurrence::clusterKey, LinkedHashMap::new, Collectors.toList()));

        List<AnalyzedOccurrence> results = new ArrayList<>();
        for (List<CandidateOccurrence> clusterOccurrences : byCluster.values()) {
            for (int index = 0; index < clusterOccurrences.size(); index++) {
                CandidateOccurrence current = clusterOccurrences.get(index);
                boolean repeatWithin7Days = false;
                boolean repeatWithin14Days = false;
                boolean repeatWithin30Days = false;
                boolean recurredAfterResolved = false;

                for (int previousIndex = index - 1; previousIndex >= 0; previousIndex--) {
                    CandidateOccurrence previous = clusterOccurrences.get(previousIndex);
                    long daysBetween = ChronoUnit.DAYS.between(previous.effectiveDate(), current.effectiveDate());
                    if (daysBetween <= 7) {
                        repeatWithin7Days = true;
                    }
                    if (daysBetween <= 14) {
                        repeatWithin14Days = true;
                    }
                    if (daysBetween <= 30) {
                        repeatWithin30Days = true;
                    }
                    if (isTerminal(previous.caseStatus())
                            && previous.resolutionAt() != null
                            && current.occurredAt() != null
                            && !previous.resolutionAt().isAfter(current.occurredAt())) {
                        recurredAfterResolved = true;
                    }
                }

                boolean withinRange = !current.effectiveDate().isBefore(fromDate)
                        && !current.effectiveDate().isAfter(toDate);

                results.add(new AnalyzedOccurrence(
                        current.exceptionCase(),
                        current.storeId(),
                        current.reconView(),
                        current.reasonKey(),
                        current.clusterKey(),
                        current.incidentTitle(),
                        current.effectiveDate(),
                        current.occurredAt(),
                        current.ownerTeam(),
                        current.businessValue(),
                        current.matchedKnownIssue(),
                        withinRange,
                        repeatWithin7Days,
                        repeatWithin14Days,
                        repeatWithin30Days,
                        recurredAfterResolved
                ));
            }
        }
        return results;
    }

    private CandidateOccurrence toCandidate(ExceptionCase exceptionCase,
                                            Map<String, BusinessValueContextDto> businessValueByCase,
                                            KnownIssueService.KnownIssueCatalogContext knownIssueContext) {
        ExceptionIncidentClassifierService.IncidentClassification classification = exceptionIncidentClassifierService.classify(exceptionCase);
        String storeId = defaultIfBlank(exceptionScopeResolver.resolveStoreId(exceptionCase), "Unscoped");
        String clusterKey = normalizeStore(storeId)
                + "::"
                + defaultIfBlank(normalize(exceptionCase.getReconView()), "UNKNOWN")
                + "::"
                + classification.code();
        return new CandidateOccurrence(
                exceptionCase,
                exceptionCase.getTransactionKey(),
                storeId,
                defaultIfBlank(normalize(exceptionCase.getReconView()), "UNKNOWN"),
                defaultIfBlank(taxonomyService.effectiveReasonCode(exceptionCase), "UNCLASSIFIED"),
                clusterKey,
                classification.title(),
                effectiveDate(exceptionCase),
                occurredAt(exceptionCase),
                resolutionAt(exceptionCase),
                resolveOwnerTeam(exceptionCase),
                businessValueByCase.get(exceptionBusinessValueService.caseKey(exceptionCase)),
                knownIssueService.matchCase(exceptionCase, knownIssueContext),
                defaultIfBlank(exceptionCase.getCaseStatus(), "OPEN")
        );
    }

    private List<RecurringIncidentPatternDto> buildRecurringIncidents(String tenantId,
                                                                      List<AnalyzedOccurrence> analysisCases) {
        if (analysisCases.isEmpty()) {
            return List.of();
        }
        var tenant = tenantService.getTenant(tenantId);
        return analysisCases.stream()
                .collect(Collectors.groupingBy(AnalyzedOccurrence::clusterKey, LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .filter(grouped -> grouped.size() >= 2 || grouped.stream().anyMatch(AnalyzedOccurrence::repeatWithin30Days))
                .map(grouped -> toRecurringIncident(grouped, tenant))
                .sorted(Comparator
                        .comparingLong(RecurringIncidentPatternDto::getRepeatAfterResolvedCases).reversed()
                        .thenComparing(RecurringIncidentPatternDto::getRepeatCases, Comparator.reverseOrder())
                        .thenComparing(pattern -> valueAtRisk(pattern.getBusinessValue()), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RecurringIncidentPatternDto::getIncidentTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private RecurringIncidentPatternDto toRecurringIncident(List<AnalyzedOccurrence> grouped,
                                                            com.recon.api.domain.TenantConfig tenant) {
        List<AnalyzedOccurrence> ordered = grouped.stream()
                .sorted(Comparator
                        .comparing(AnalyzedOccurrence::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AnalyzedOccurrence::transactionKey, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        AnalyzedOccurrence first = ordered.get(0);
        AnalyzedOccurrence latest = ordered.get(ordered.size() - 1);
        long repeatCases = grouped.stream().filter(AnalyzedOccurrence::repeatWithin30Days).count();
        long repeatAfterResolvedCases = grouped.stream().filter(AnalyzedOccurrence::recurredAfterResolved).count();
        long repeatWithin7DaysCases = grouped.stream().filter(AnalyzedOccurrence::repeatWithin7Days).count();
        long repeatWithin14DaysCases = grouped.stream().filter(AnalyzedOccurrence::repeatWithin14Days).count();
        long activeCases = grouped.stream().filter(occurrence -> isActive(occurrence.caseStatus())).count();
        KnownIssueMatchDto matchedKnownIssue = bestKnownIssueMatch(grouped);
        BusinessValueContextDto businessValue = exceptionBusinessValueService.aggregateIncidentValue(
                grouped.stream()
                        .map(AnalyzedOccurrence::businessValue)
                        .filter(Objects::nonNull)
                        .toList(),
                grouped.size()
        );

        String incidentSummary = grouped.size() + " cases in range"
                + " | " + repeatCases + " repeats within 30d"
                + (repeatAfterResolvedCases > 0 ? " | " + repeatAfterResolvedCases + " came back after resolution" : "");

        return RecurringIncidentPatternDto.builder()
                .recurrenceKey(first.clusterKey())
                .storeId(first.storeId())
                .reconView(first.reconView())
                .incidentTitle(first.incidentTitle())
                .incidentSummary(incidentSummary)
                .totalCases(grouped.size())
                .activeCases(activeCases)
                .repeatCases(repeatCases)
                .repeatAfterResolvedCases(repeatAfterResolvedCases)
                .repeatWithin7DaysCases(repeatWithin7DaysCases)
                .repeatWithin14DaysCases(repeatWithin14DaysCases)
                .repeatWithin30DaysCases(repeatCases)
                .ownerTeam(defaultIfBlank(latest.ownerTeam(), "Unassigned"))
                .latestCaseStatus(defaultIfBlank(latest.caseStatus(), "OPEN"))
                .latestSeenAt(TimezoneConverter.toDisplay(stringValue(latest.occurredAt()), tenant))
                .firstSeenAt(TimezoneConverter.toDisplay(stringValue(first.occurredAt()), tenant))
                .priorityReason(buildPatternPriorityReason(latest, repeatCases, repeatAfterResolvedCases, matchedKnownIssue))
                .preventionAction(resolvePreventionAction(first.incidentTitle(), matchedKnownIssue, latest.ownerTeam()))
                .businessValue(businessValue)
                .matchedKnownIssue(matchedKnownIssue)
                .build();
    }

    private List<PreventionOpportunityDto> buildPreventionOpportunities(List<RecurringIncidentPatternDto> recurringIncidents) {
        return recurringIncidents.stream()
                .filter(pattern -> pattern.getRepeatCases() > 0
                        || pattern.getRepeatAfterResolvedCases() > 0
                        || pattern.getMatchedKnownIssue() == null)
                .sorted(Comparator
                        .comparingLong(RecurringIncidentPatternDto::getRepeatAfterResolvedCases).reversed()
                        .thenComparing(RecurringIncidentPatternDto::getRepeatCases, Comparator.reverseOrder())
                        .thenComparing(pattern -> valueAtRisk(pattern.getBusinessValue()), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(pattern -> PreventionOpportunityDto.builder()
                        .recurrenceKey(pattern.getRecurrenceKey())
                        .title(pattern.getStoreId() + " | " + moduleLabel(pattern.getReconView()) + " | " + pattern.getIncidentTitle())
                        .storeId(pattern.getStoreId())
                        .reconView(pattern.getReconView())
                        .ownerTeam(pattern.getOwnerTeam())
                        .repeatCases(pattern.getRepeatCases())
                        .repeatAfterResolvedCases(pattern.getRepeatAfterResolvedCases())
                        .opportunityReason(buildOpportunityReason(pattern))
                        .recommendedAction(resolvePreventionAction(
                                pattern.getIncidentTitle(),
                                pattern.getMatchedKnownIssue(),
                                pattern.getOwnerTeam()))
                        .lastSeenAt(pattern.getLatestSeenAt())
                        .businessValue(pattern.getBusinessValue())
                        .matchedKnownIssue(pattern.getMatchedKnownIssue())
                        .build())
                .toList();
    }

    private List<RecurrenceTrendPointDto> buildTrend(List<AnalyzedOccurrence> analysisCases,
                                                     LocalDate fromDate,
                                                     LocalDate toDate) {
        if (analysisCases.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, List<AnalyzedOccurrence>> byWeek = analysisCases.stream()
                .filter(AnalyzedOccurrence::repeatWithin30Days)
                .collect(Collectors.groupingBy(
                        occurrence -> occurrence.effectiveDate()
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<RecurrenceTrendPointDto> trend = new ArrayList<>();
        LocalDate bucketStart = fromDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!bucketStart.isAfter(toDate)) {
            LocalDate bucketEnd = bucketStart.plusDays(6);
            List<AnalyzedOccurrence> bucketOccurrences = byWeek.getOrDefault(bucketStart, List.of());
            trend.add(RecurrenceTrendPointDto.builder()
                    .label(shortMonth(bucketStart.getMonthValue()) + " " + bucketStart.getDayOfMonth())
                    .startDate(bucketStart.toString())
                    .endDate(bucketEnd.toString())
                    .repeatCases(bucketOccurrences.size())
                    .recurringIncidentPatterns(bucketOccurrences.stream().map(AnalyzedOccurrence::clusterKey).distinct().count())
                    .repeatAfterResolvedCases(bucketOccurrences.stream().filter(AnalyzedOccurrence::recurredAfterResolved).count())
                    .repeatValueAtRisk(sumValueAtRisk(bucketOccurrences))
                    .build());
            bucketStart = bucketStart.plusWeeks(1);
        }
        return trend;
    }

    private List<RecurrenceBreakdownDto> buildBreakdown(List<AnalyzedOccurrence> repeatCases,
                                                        Function<AnalyzedOccurrence, String> keyExtractor,
                                                        Function<String, String> labelResolver,
                                                        int limit) {
        if (repeatCases.isEmpty()) {
            return List.of();
        }

        Map<String, List<AnalyzedOccurrence>> grouped = repeatCases.stream()
                .collect(Collectors.groupingBy(
                        occurrence -> defaultIfBlank(keyExtractor.apply(occurrence), "UNCLASSIFIED"),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> toBreakdown(entry.getKey(), labelResolver.apply(entry.getKey()), entry.getValue(), repeatCases.size()))
                .sorted(Comparator
                        .comparingLong(RecurrenceBreakdownDto::getRepeatCases).reversed()
                        .thenComparing(RecurrenceBreakdownDto::getValueAtRisk, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RecurrenceBreakdownDto::getLabel, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(limit)
                .toList();
    }

    private RecurrenceBreakdownDto toBreakdown(String key,
                                               String label,
                                               List<AnalyzedOccurrence> grouped,
                                               int totalRepeatCases) {
        return RecurrenceBreakdownDto.builder()
                .key(key)
                .label(label)
                .recurringIncidentCount(grouped.stream().map(AnalyzedOccurrence::clusterKey).distinct().count())
                .repeatCases(grouped.size())
                .repeatAfterResolvedCases(grouped.stream().filter(AnalyzedOccurrence::recurredAfterResolved).count())
                .repeatWithin7DaysCases(grouped.stream().filter(AnalyzedOccurrence::repeatWithin7Days).count())
                .repeatWithin14DaysCases(grouped.stream().filter(AnalyzedOccurrence::repeatWithin14Days).count())
                .repeatWithin30DaysCases(grouped.stream().filter(AnalyzedOccurrence::repeatWithin30Days).count())
                .repeatShare(ratio(grouped.size(), totalRepeatCases))
                .valueAtRisk(sumValueAtRisk(grouped))
                .build();
    }

    private KnownIssueMatchDto bestKnownIssueMatch(List<AnalyzedOccurrence> occurrences) {
        return occurrences.stream()
                .map(AnalyzedOccurrence::matchedKnownIssue)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(KnownIssueMatchDto::getMatchScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(KnownIssueMatchDto::getHelpfulCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(KnownIssueMatchDto::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .findFirst()
                .orElse(null);
    }

    private String buildPatternPriorityReason(AnalyzedOccurrence latest,
                                              long repeatCases,
                                              long repeatAfterResolvedCases,
                                              KnownIssueMatchDto matchedKnownIssue) {
        List<String> reasons = new ArrayList<>();
        reasons.add(repeatCases + " repeats within 30d");
        if (repeatAfterResolvedCases > 0) {
            reasons.add(repeatAfterResolvedCases + " returned after resolution");
        }
        if (trimToNull(latest.ownerTeam()) == null) {
            reasons.add("owner team missing");
        }
        if (matchedKnownIssue != null) {
            reasons.add("known fix available");
        }
        return reasons.stream().limit(3).collect(Collectors.joining(", "));
    }

    private String buildOpportunityReason(RecurringIncidentPatternDto pattern) {
        List<String> reasons = new ArrayList<>();
        reasons.add(pattern.getRepeatCases() + " repeat cases");
        if (pattern.getRepeatAfterResolvedCases() > 0) {
            reasons.add(pattern.getRepeatAfterResolvedCases() + " came back after teams closed them");
        }
        if (pattern.getMatchedKnownIssue() == null) {
            reasons.add("no standard guidance documented yet");
        } else {
            reasons.add("guidance exists but the issue is still recurring");
        }
        return reasons.stream().limit(3).collect(Collectors.joining(" | "));
    }

    private String resolvePreventionAction(String incidentTitle,
                                           KnownIssueMatchDto matchedKnownIssue,
                                           String ownerTeam) {
        if (matchedKnownIssue != null && trimToNull(matchedKnownIssue.getRecommendedAction()) != null) {
            return matchedKnownIssue.getRecommendedAction();
        }
        String normalizedTitle = Objects.toString(incidentTitle, "").toUpperCase(Locale.ROOT);
        if (normalizedTitle.contains("SYNC LAG")) {
            return "Review connector backlog, store connectivity, and recovery ownership before the next trading window.";
        }
        if (normalizedTitle.contains("PROCESSING FAILURE")) {
            return "Review recent release or configuration changes and standardize the recovery checklist with the support team.";
        }
        if (normalizedTitle.contains("TRANSACTION VARIANCE")) {
            return "Validate totals, item mapping, and tax or tender configuration, then publish the winning fix as standard work.";
        }
        if (normalizedTitle.contains("DUPLICATE PROCESSING")) {
            return "Tighten retry controls and confirm duplicate-suppression rules with the owner team.";
        }
        if (normalizedTitle.contains("ITEM SYNC GAP")) {
            return "Check item replication health and close the missing master-data gap before the next store wave.";
        }
        if (normalizedTitle.contains("CONFIGURATION ISSUE")) {
            return "Review store overrides and deployment differences, then lock the configuration baseline.";
        }
        if (trimToNull(ownerTeam) == null) {
            return "Assign an owner team and document the repeat-fix steps so stores do not solve this ad hoc again.";
        }
        return "Document the successful fix as standard guidance and confirm the owner team follows the same recovery path every time.";
    }

    private List<String> resolveReconViews(String reconView, List<String> allowedReconViews) {
        if (trimToNull(reconView) != null) {
            return List.of(normalize(reconView));
        }
        if (allowedReconViews == null) {
            return List.of();
        }
        return allowedReconViews.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String resolveKnownIssueLabel(List<AnalyzedOccurrence> repeatCases, String key) {
        if ("UNMATCHED".equalsIgnoreCase(key)) {
            return "No matched guidance";
        }
        return repeatCases.stream()
                .map(AnalyzedOccurrence::matchedKnownIssue)
                .filter(Objects::nonNull)
                .filter(match -> Objects.equals(defaultIfBlank(match.getIssueKey(), "MATCHED"), key))
                .map(KnownIssueMatchDto::getTitle)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(key);
    }

    private LocalDate effectiveDate(ExceptionCase exceptionCase) {
        LocalDate resolvedBusinessDate = exceptionScopeResolver.resolveBusinessDate(exceptionCase);
        if (resolvedBusinessDate != null) {
            return resolvedBusinessDate;
        }
        return exceptionCase.getCreatedAt() != null ? exceptionCase.getCreatedAt().toLocalDate() : null;
    }

    private LocalDateTime occurredAt(ExceptionCase exceptionCase) {
        if (exceptionCase.getCreatedAt() != null) {
            return exceptionCase.getCreatedAt();
        }
        if (exceptionCase.getUpdatedAt() != null) {
            return exceptionCase.getUpdatedAt();
        }
        LocalDate effectiveDate = effectiveDate(exceptionCase);
        return effectiveDate != null ? effectiveDate.atStartOfDay() : null;
    }

    private LocalDateTime resolutionAt(ExceptionCase exceptionCase) {
        return isTerminal(exceptionCase.getCaseStatus()) ? exceptionCase.getUpdatedAt() : null;
    }

    private boolean isTerminal(String caseStatus) {
        String normalized = normalize(caseStatus);
        return "RESOLVED".equals(normalized) || "IGNORED".equals(normalized);
    }

    private boolean isActive(String caseStatus) {
        return !isTerminal(caseStatus);
    }

    private String resolveOwnerTeam(ExceptionCase exceptionCase) {
        String team = trimToNull(exceptionCase.getAssignedRoleName());
        if (team != null) {
            return team;
        }
        String assignee = trimToNull(exceptionCase.getAssigneeUsername());
        return assignee != null ? assignee + " (individual)" : null;
    }

    private BigDecimal sumValueAtRisk(List<AnalyzedOccurrence> occurrences) {
        BigDecimal total = BigDecimal.ZERO;
        boolean found = false;
        for (AnalyzedOccurrence occurrence : occurrences) {
            BigDecimal current = valueAtRisk(occurrence.businessValue());
            if (current != null) {
                total = total.add(current);
                found = true;
            }
        }
        return found ? total : null;
    }

    private BigDecimal valueAtRisk(BusinessValueContextDto businessValue) {
        return businessValue != null ? businessValue.getValueAtRisk() : null;
    }

    private LocalDate normalizeDate(String value, LocalDate fallback) {
        return trimToNull(value) == null ? fallback : LocalDate.parse(value);
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeStore(String value) {
        return normalize(trimToNull(value));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(((double) numerator / denominator) * 10000.0) / 100.0;
    }

    private String moduleLabel(String reconView) {
        return reconModuleService.resolveModuleLabel(reconView, taxonomyService.labelForGenericKey(reconView));
    }

    private String shortMonth(int monthValue) {
        return switch (monthValue) {
            case 1 -> "Jan";
            case 2 -> "Feb";
            case 3 -> "Mar";
            case 4 -> "Apr";
            case 5 -> "May";
            case 6 -> "Jun";
            case 7 -> "Jul";
            case 8 -> "Aug";
            case 9 -> "Sep";
            case 10 -> "Oct";
            case 11 -> "Nov";
            default -> "Dec";
        };
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private boolean matchesStoreScope(ExceptionCase exceptionCase,
                                      java.util.Collection<String> accessibleStoreIds) {
        if (accessibleStoreIds == null || accessibleStoreIds.isEmpty()) {
            return true;
        }
        String storeId = normalizeStore(exceptionScopeResolver.resolveStoreId(exceptionCase));
        return storeId != null && accessibleStoreIds.stream()
                .map(this::normalizeStore)
                .filter(Objects::nonNull)
                .anyMatch(storeId::equals);
    }

    private record CandidateOccurrence(ExceptionCase exceptionCase,
                                       String transactionKey,
                                       String storeId,
                                       String reconView,
                                       String reasonKey,
                                       String clusterKey,
                                       String incidentTitle,
                                       LocalDate effectiveDate,
                                       LocalDateTime occurredAt,
                                       LocalDateTime resolutionAt,
                                       String ownerTeam,
                                       BusinessValueContextDto businessValue,
                                       KnownIssueMatchDto matchedKnownIssue,
                                       String caseStatus) {
    }

    private record AnalyzedOccurrence(ExceptionCase exceptionCase,
                                      String storeId,
                                      String reconView,
                                      String reasonKey,
                                      String clusterKey,
                                      String incidentTitle,
                                      LocalDate effectiveDate,
                                      LocalDateTime occurredAt,
                                      String ownerTeam,
                                      BusinessValueContextDto businessValue,
                                      KnownIssueMatchDto matchedKnownIssue,
                                      boolean withinRange,
                                      boolean repeatWithin7Days,
                                      boolean repeatWithin14Days,
                                      boolean repeatWithin30Days,
                                      boolean recurredAfterResolved) {
        private String transactionKey() {
            return exceptionCase != null ? exceptionCase.getTransactionKey() : null;
        }

        private String caseStatus() {
            return exceptionCase != null ? exceptionCase.getCaseStatus() : null;
        }
    }
}
