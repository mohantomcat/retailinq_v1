package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.LocationScorecardDto;
import com.recon.api.domain.ScorecardSummaryDto;
import com.recon.api.domain.ScorecardsResponse;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ReconElasticsearchRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScorecardService {

    private final ReconElasticsearchRepository esRepository;
    private final ExceptionCaseRepository exceptionCaseRepository;
    private final ExceptionSlaService exceptionSlaService;

    public ScorecardsResponse getScorecards(String tenantId,
                                            List<String> storeIds,
                                            String fromBusinessDate,
                                            String toBusinessDate,
                                            String reconView,
                                            List<String> allowedReconViews) {
        String from = normalizeDate(fromBusinessDate, LocalDate.now().minusDays(29).toString());
        String to = normalizeDate(toBusinessDate, LocalDate.now().toString());
        List<String> scopedViews = resolveReconViews(reconView, allowedReconViews);

        Map<String, Long> overallStatuses = new LinkedHashMap<>();
        for (String view : scopedViews) {
            mergeStatusCounts(overallStatuses, esRepository.aggregateByStatus(
                    storeIds,
                    List.of(),
                    null,
                    from,
                    to,
                    view
            ));
        }
        List<ExceptionCase> activeCases = getFilteredCases(tenantId, scopedViews, storeIds, from, to);

        return ScorecardsResponse.builder()
                .executiveSummary(buildSummary("Executive Health", overallStatuses, activeCases))
                .moduleScorecards(buildModuleScorecards(from, to, storeIds, activeCases, scopedViews))
                .storeScorecards(buildStoreScorecards(from, to, storeIds, activeCases, scopedViews))
                .build();
    }

    private List<LocationScorecardDto> buildModuleScorecards(String from,
                                                             String to,
                                                             List<String> storeIds,
                                                             List<ExceptionCase> activeCases,
                                                             List<String> reconViews) {
        Map<String, Map<String, Long>> raw = new LinkedHashMap<>();
        for (String reconView : reconViews) {
            mergeNestedStatusCounts(raw, esRepository.aggregateByFieldAndStatus(
                    "reconView",
                    10,
                    storeIds,
                    List.of(),
                    null,
                    from,
                    to,
                    reconView
            ));
        }
        return raw.entrySet().stream()
                .map(entry -> buildLocationScorecard(
                        entry.getKey(),
                        moduleLabel(entry.getKey()),
                        entry.getValue(),
                        activeCases.stream()
                                .filter(exceptionCase -> equalsIgnoreCase(exceptionCase.getReconView(), entry.getKey()))
                                .toList()))
                .sorted(Comparator.comparingInt(LocationScorecardDto::getHealthScore)
                        .thenComparing(LocationScorecardDto::getExceptionCount, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private List<LocationScorecardDto> buildStoreScorecards(String from,
                                                            String to,
                                                            List<String> storeIds,
                                                            List<ExceptionCase> activeCases,
                                                            List<String> reconViews) {
        Map<String, Map<String, Long>> raw = new LinkedHashMap<>();
        for (String reconView : reconViews) {
            mergeNestedStatusCounts(raw, esRepository.aggregateByFieldAndStatus(
                    "storeId",
                    500,
                    storeIds,
                    List.of(),
                    null,
                    from,
                    to,
                    reconView
            ));
        }
        return raw.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .map(entry -> buildLocationScorecard(
                        entry.getKey(),
                        "Store " + entry.getKey(),
                        entry.getValue(),
                        activeCases.stream()
                                .filter(exceptionCase -> equalsIgnoreCase(exceptionCase.getStoreId(), entry.getKey()))
                                .toList()))
                .sorted(Comparator.comparingInt(LocationScorecardDto::getHealthScore)
                        .thenComparing(LocationScorecardDto::getExceptionCount, Comparator.reverseOrder()))
                .limit(20)
                .collect(Collectors.toList());
    }

    private ScorecardSummaryDto buildSummary(String label,
                                             Map<String, Long> statuses,
                                             List<ExceptionCase> activeCases) {
        long total = sum(statuses);
        long matched = statuses.getOrDefault("MATCHED", 0L);
        long exceptions = Math.max(total - matched, 0);
        long missing = countPrefix(statuses, "MISSING_IN_");
        long duplicates = countPrefix(statuses, "DUPLICATE_IN_");
        long breached = activeCases.stream()
                .filter(exceptionCase -> "BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(exceptionCase)))
                .count();

        double matchRate = ratio(matched, total);
        double exceptionRate = ratio(exceptions, total);
        double duplicateRate = ratio(duplicates, total);
        double slaBreachRate = ratio(breached, activeCases.size());
        int healthScore = healthScore(matchRate, exceptionRate, duplicateRate, ratio(missing, total), slaBreachRate);

        return ScorecardSummaryDto.builder()
                .label(label)
                .totalTransactions(total)
                .matchedTransactions(matched)
                .exceptionCount(exceptions)
                .missingCount(missing)
                .duplicateCount(duplicates)
                .activeExceptions(activeCases.size())
                .breachedExceptions(breached)
                .matchRate(matchRate)
                .exceptionRate(exceptionRate)
                .duplicateRate(duplicateRate)
                .slaBreachRate(slaBreachRate)
                .healthScore(healthScore)
                .healthBand(healthBand(healthScore))
                .build();
    }

    private LocationScorecardDto buildLocationScorecard(String key,
                                                        String label,
                                                        Map<String, Long> statuses,
                                                        List<ExceptionCase> cases) {
        long total = sum(statuses);
        long matched = statuses.getOrDefault("MATCHED", 0L);
        long exceptionCount = Math.max(total - matched, 0);
        long missing = countPrefix(statuses, "MISSING_IN_");
        long duplicates = countPrefix(statuses, "DUPLICATE_IN_");
        long itemMissing = statuses.getOrDefault("ITEM_MISSING", 0L);
        long quantityMismatch = statuses.getOrDefault("QUANTITY_MISMATCH", 0L);
        long totalMismatch = statuses.getOrDefault("TOTAL_MISMATCH", 0L);
        long breached = cases.stream()
                .filter(exceptionCase -> "BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(exceptionCase)))
                .count();

        double matchRate = ratio(matched, total);
        double exceptionRate = ratio(exceptionCount, total);
        double duplicateRate = ratio(duplicates, total);
        double slaBreachRate = ratio(breached, cases.size());
        int healthScore = healthScore(matchRate, exceptionRate, duplicateRate, ratio(missing, total), slaBreachRate);

        return LocationScorecardDto.builder()
                .key(key)
                .label(label)
                .totalTransactions(total)
                .matchedTransactions(matched)
                .exceptionCount(exceptionCount)
                .missingCount(missing)
                .duplicateCount(duplicates)
                .itemMissingCount(itemMissing)
                .quantityMismatchCount(quantityMismatch)
                .totalMismatchCount(totalMismatch)
                .activeExceptions(cases.size())
                .breachedExceptions(breached)
                .matchRate(matchRate)
                .exceptionRate(exceptionRate)
                .duplicateRate(duplicateRate)
                .slaBreachRate(slaBreachRate)
                .healthScore(healthScore)
                .healthBand(healthBand(healthScore))
                .build();
    }

    private List<ExceptionCase> getFilteredCases(String tenantId,
                                                 List<String> reconViews,
                                                 List<String> storeIds,
                                                 String fromBusinessDate,
                                                 String toBusinessDate) {
        LocalDate from = LocalDate.parse(fromBusinessDate);
        LocalDate to = LocalDate.parse(toBusinessDate);
        return exceptionCaseRepository.findActiveCasesForAging(
                        tenantId,
                        null,
                        LocalDateTime.now().minusDays(35))
                .stream()
                .filter(exceptionCase -> reconViews.stream()
                        .anyMatch(view -> equalsIgnoreCase(view, exceptionCase.getReconView())))
                .filter(exceptionCase -> storeIds == null || storeIds.isEmpty()
                        || storeIds.contains(exceptionCase.getStoreId()))
                .filter(exceptionCase -> exceptionCase.getBusinessDate() == null
                        || (!exceptionCase.getBusinessDate().isBefore(from) && !exceptionCase.getBusinessDate().isAfter(to)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String normalizeDate(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

    private long sum(Map<String, Long> byStatus) {
        return byStatus.values().stream().mapToLong(Long::longValue).sum();
    }

    private long countPrefix(Map<String, Long> byStatus, String prefix) {
        return byStatus.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith(prefix))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(((double) numerator / denominator) * 10000.0) / 100.0;
    }

    private int healthScore(double matchRate,
                            double exceptionRate,
                            double duplicateRate,
                            double missingRate,
                            double slaBreachRate) {
        double score = (matchRate * 0.55)
                + ((100.0 - exceptionRate) * 0.20)
                + ((100.0 - duplicateRate) * 0.10)
                + ((100.0 - missingRate) * 0.10)
                + ((100.0 - slaBreachRate) * 0.05);
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private String healthBand(int score) {
        if (score >= 95) {
            return "EXCELLENT";
        }
        if (score >= 85) {
            return "HEALTHY";
        }
        if (score >= 70) {
            return "WATCH";
        }
        return "CRITICAL";
    }

    private String moduleLabel(String reconView) {
        return switch (Objects.toString(reconView, "").toUpperCase()) {
            case "XSTORE_SIOCS" -> "Xstore vs SIOCS";
            case "SIOCS_MFCS" -> "SIOCS vs MFCS";
            case "XSTORE_XOCS" -> "Xstore vs XOCS";
            case "XSTORE_SIM" -> "Xstore vs SIM";
            default -> Objects.toString(reconView, "Unknown Module");
        };
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return Objects.toString(left, "").equalsIgnoreCase(Objects.toString(right, ""));
    }

    private void mergeStatusCounts(Map<String, Long> target, Map<String, Long> source) {
        source.forEach((key, value) -> target.merge(key, value, Long::sum));
    }

    private void mergeNestedStatusCounts(Map<String, Map<String, Long>> target,
                                         Map<String, Map<String, Long>> source) {
        source.forEach((primaryKey, statuses) -> {
            Map<String, Long> targetStatuses = target.computeIfAbsent(primaryKey, ignored -> new LinkedHashMap<>());
            statuses.forEach((statusKey, value) -> targetStatuses.merge(statusKey, value, Long::sum));
        });
    }
}
