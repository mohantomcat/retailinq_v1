package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.RootCauseAnalyticsResponse;
import com.recon.api.domain.RootCauseBreakdownDto;
import com.recon.api.domain.RootCauseSummaryDto;
import com.recon.api.repository.ExceptionCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RootCauseAnalyticsService {

    private final ExceptionCaseRepository exceptionCaseRepository;
    private final ExceptionSlaService exceptionSlaService;
    private final RootCauseTaxonomyService taxonomyService;
    private final ExceptionScopeResolver exceptionScopeResolver;
    private final ReconModuleService reconModuleService;

    public RootCauseAnalyticsResponse getAnalytics(String tenantId,
                                                   String reconView,
                                                   List<String> allowedReconViews,
                                                   java.util.Collection<String> accessibleStoreIds,
                                                   String storeId,
                                                   String fromBusinessDate,
                                                   String toBusinessDate) {
        LocalDate fromDate = normalizeDate(fromBusinessDate, LocalDate.now().minusDays(29));
        LocalDate toDate = normalizeDate(toBusinessDate, LocalDate.now());
        LocalDateTime since = fromDate.minusDays(90).atStartOfDay();

        List<ExceptionCase> rawCases = exceptionCaseRepository.findForRootCauseAnalytics(
                tenantId,
                reconView == null || reconView.isBlank() ? null : reconView.toUpperCase(),
                storeId == null || storeId.isBlank() ? null : storeId,
                since
        );

        List<String> scopedViews = resolveReconViews(reconView, allowedReconViews);
        List<ExceptionCase> cases = rawCases.stream()
                .filter(exceptionCase -> scopedViews.stream()
                        .anyMatch(view -> view.equalsIgnoreCase(exceptionCase.getReconView())))
                .filter(exceptionCase -> matchesStoreScope(exceptionCase, accessibleStoreIds))
                .filter(exceptionCase -> {
                    LocalDate effectiveDate = effectiveDate(exceptionCase);
                    return effectiveDate != null
                            && !effectiveDate.isBefore(fromDate)
                            && !effectiveDate.isAfter(toDate);
                })
                .toList();

        List<RootCauseBreakdownDto> topReasons = buildBreakdown(
                cases,
                exceptionCase -> taxonomyService.effectiveReasonCode(exceptionCase),
                taxonomyService::labelForReason,
                toDate,
                8
        );

        List<RootCauseBreakdownDto> paretoReasons = applyPareto(topReasons);

        return RootCauseAnalyticsResponse.builder()
                .summary(buildSummary(cases, topReasons, buildBreakdown(
                        cases,
                        exceptionCase -> taxonomyService.effectiveCategory(exceptionCase),
                        taxonomyService::labelForCategory,
                        toDate,
                        6
                )))
                .topReasons(topReasons)
                .topCategories(buildBreakdown(
                        cases,
                        exceptionCase -> taxonomyService.effectiveCategory(exceptionCase),
                        taxonomyService::labelForCategory,
                        toDate,
                        6
                ))
                .topStores(buildBreakdown(
                        cases,
                        exceptionScopeResolver::resolveStoreId,
                        key -> key == null || key.isBlank() ? "Unclassified" : "Store " + key,
                        toDate,
                        8
                ))
                .topRegisters(buildBreakdown(
                        cases,
                        exceptionScopeResolver::resolveWkstnId,
                        key -> key == null || key.isBlank() ? "Unclassified" : "Register " + key,
                        toDate,
                        8
                ))
                .topModules(buildBreakdown(
                        cases,
                        ExceptionCase::getReconView,
                        this::moduleLabel,
                        toDate,
                        6
                ))
                .topSeverities(buildBreakdown(
                        cases,
                        ExceptionCase::getSeverity,
                        taxonomyService::labelForGenericKey,
                        toDate,
                        6
                ))
                .paretoReasons(paretoReasons)
                .build();
    }

    private RootCauseSummaryDto buildSummary(List<ExceptionCase> cases,
                                             List<RootCauseBreakdownDto> topReasons,
                                             List<RootCauseBreakdownDto> topCategories) {
        long totalCases = cases.size();
        long classifiedCases = cases.stream()
                .filter(exceptionCase -> !"UNCLASSIFIED".equalsIgnoreCase(taxonomyService.effectiveReasonCode(exceptionCase)))
                .count();
        long activeCases = cases.stream()
                .filter(this::isActive)
                .count();
        long breachedCases = cases.stream()
                .filter(this::isBreached)
                .count();

        RootCauseBreakdownDto topReason = topReasons.stream().findFirst().orElse(null);
        RootCauseBreakdownDto topCategory = topCategories.stream().findFirst().orElse(null);

        return RootCauseSummaryDto.builder()
                .totalCases(totalCases)
                .classifiedCases(classifiedCases)
                .unclassifiedCases(Math.max(totalCases - classifiedCases, 0))
                .activeCases(activeCases)
                .breachedCases(breachedCases)
                .classificationRate(ratio(classifiedCases, totalCases))
                .topReasonCode(topReason != null ? topReason.getKey() : null)
                .topReasonLabel(topReason != null ? topReason.getLabel() : null)
                .topCategory(topCategory != null ? topCategory.getKey() : null)
                .topCategoryLabel(topCategory != null ? topCategory.getLabel() : null)
                .build();
    }

    private List<RootCauseBreakdownDto> buildBreakdown(List<ExceptionCase> cases,
                                                       Function<ExceptionCase, String> keyExtractor,
                                                       Function<String, String> labelResolver,
                                                       LocalDate toDate,
                                                       int limit) {
        Map<String, List<ExceptionCase>> grouped = new LinkedHashMap<>();
        for (ExceptionCase exceptionCase : cases) {
            String key = normalizeKey(keyExtractor.apply(exceptionCase));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(exceptionCase);
        }

        List<RootCauseBreakdownDto> rows = grouped.entrySet().stream()
                .map(entry -> toBreakdown(entry.getKey(), labelResolver.apply(entry.getKey()), entry.getValue(), cases.size(), toDate))
                .sorted(Comparator.comparingLong(RootCauseBreakdownDto::getCount).reversed()
                        .thenComparing(RootCauseBreakdownDto::getLabel))
                .limit(limit)
                .collect(Collectors.toList());

        return rows;
    }

    private RootCauseBreakdownDto toBreakdown(String key,
                                              String label,
                                              List<ExceptionCase> groupedCases,
                                              int totalCases,
                                              LocalDate toDate) {
        LocalDate last7From = toDate.minusDays(6);
        LocalDate prev7From = toDate.minusDays(13);
        LocalDate prev7To = toDate.minusDays(7);

        long last7 = groupedCases.stream()
                .filter(exceptionCase -> {
                    LocalDate effectiveDate = effectiveDate(exceptionCase);
                    return effectiveDate != null
                            && !effectiveDate.isBefore(last7From)
                            && !effectiveDate.isAfter(toDate);
                })
                .count();

        long previous7 = groupedCases.stream()
                .filter(exceptionCase -> {
                    LocalDate effectiveDate = effectiveDate(exceptionCase);
                    return effectiveDate != null
                            && !effectiveDate.isBefore(prev7From)
                            && !effectiveDate.isAfter(prev7To);
                })
                .count();

        return RootCauseBreakdownDto.builder()
                .key(key)
                .label(label)
                .count(groupedCases.size())
                .percent(ratio(groupedCases.size(), totalCases))
                .activeCases(groupedCases.stream().filter(this::isActive).count())
                .breachedCases(groupedCases.stream().filter(this::isBreached).count())
                .last7DaysCount(last7)
                .previous7DaysCount(previous7)
                .cumulativePercent(0.0)
                .build();
    }

    private List<RootCauseBreakdownDto> applyPareto(List<RootCauseBreakdownDto> rows) {
        double cumulative = 0.0;
        List<RootCauseBreakdownDto> result = new ArrayList<>();
        for (RootCauseBreakdownDto row : rows) {
            cumulative = Math.min(100.0, cumulative + row.getPercent());
            result.add(RootCauseBreakdownDto.builder()
                    .key(row.getKey())
                    .label(row.getLabel())
                    .count(row.getCount())
                    .percent(row.getPercent())
                    .activeCases(row.getActiveCases())
                    .breachedCases(row.getBreachedCases())
                    .last7DaysCount(row.getLast7DaysCount())
                    .previous7DaysCount(row.getPrevious7DaysCount())
                    .cumulativePercent(Math.round(cumulative * 100.0) / 100.0)
                    .build());
        }
        return result;
    }

    private List<String> resolveReconViews(String reconView, List<String> allowedReconViews) {
        if (reconView != null && !reconView.isBlank()) {
            return List.of(reconView.toUpperCase());
        }
        return allowedReconViews == null ? List.of() : allowedReconViews.stream()
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.toList());
    }

    private String moduleLabel(String reconView) {
        return reconModuleService.resolveModuleLabel(reconView, taxonomyService.labelForGenericKey(reconView));
    }

    private LocalDate effectiveDate(ExceptionCase exceptionCase) {
        LocalDate resolvedBusinessDate = exceptionScopeResolver.resolveBusinessDate(exceptionCase);
        if (resolvedBusinessDate != null) {
            return resolvedBusinessDate;
        }
        return exceptionCase.getCreatedAt() != null ? exceptionCase.getCreatedAt().toLocalDate() : null;
    }

    private boolean isActive(ExceptionCase exceptionCase) {
        String status = Objects.toString(exceptionCase.getCaseStatus(), "").toUpperCase();
        return !"RESOLVED".equals(status) && !"IGNORED".equals(status);
    }

    private boolean isBreached(ExceptionCase exceptionCase) {
        return "BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(exceptionCase));
    }

    private LocalDate normalizeDate(String value, LocalDate fallback) {
        return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
    }

    private String normalizeKey(String key) {
        String normalized = Objects.toString(key, "").trim();
        return normalized.isEmpty() ? "UNCLASSIFIED" : normalized.toUpperCase();
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(((double) numerator / denominator) * 10000.0) / 100.0;
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

    private String normalizeStore(String value) {
        String normalized = normalizeKey(value);
        return "UNCLASSIFIED".equals(normalized) ? null : normalized;
    }
}
