package com.recon.api.service;

import com.recon.api.domain.DashboardAnalyticsResponse;
import com.recon.api.domain.ExceptionAgingBucket;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.RankedLocationStat;
import com.recon.api.domain.SlaSummaryDto;
import com.recon.api.domain.TrendPoint;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ReconElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardAnalyticsService {

    private final ReconElasticsearchRepository esRepository;
    private final ExceptionCaseRepository exceptionCaseRepository;
    private final ExceptionSlaService exceptionSlaService;
    private final ReconModuleService reconModuleService;

    public DashboardAnalyticsResponse getAnalytics(String tenantId,
                                                   List<String> storeIds,
                                                   List<String> wkstnIds,
                                                   List<String> transactionTypes,
                                                   List<String> transactionFamilies,
                                                   String reconView) {
        return getAnalytics(tenantId, storeIds, wkstnIds, transactionTypes, transactionFamilies, reconView, null);
    }

    public DashboardAnalyticsResponse getAnalytics(String tenantId,
                                                   List<String> storeIds,
                                                   List<String> wkstnIds,
                                                   List<String> transactionTypes,
                                                   List<String> transactionFamilies,
                                                   String reconView,
                                                   Collection<String> allowedReconViews) {
        String today = LocalDate.now().toString();
        String last7 = LocalDate.now().minusDays(6).toString();
        String last30 = LocalDate.now().minusDays(29).toString();
        List<String> scopedReconViews = resolveScopedReconViews(reconView, allowedReconViews);
        SlaSummaryDto slaSummary = exceptionSlaService.getSlaSummary(
                tenantId, reconView, storeIds, wkstnIds, scopedReconViews);
        boolean transactionFamilyScoped = reconModuleService.isTransactionFamilyScoped(reconView);

        return DashboardAnalyticsResponse.builder()
                .last7Days(buildTrend(last7, today, storeIds, wkstnIds, transactionTypes, transactionFamilies, reconView, scopedReconViews))
                .last30Days(buildTrend(last30, today, storeIds, wkstnIds, transactionTypes, transactionFamilies, reconView, scopedReconViews))
                .topFailingStores(buildRankedStats(
                        mergeNestedStatusCounts(scopedReconViews,
                                scopedReconView -> esRepository.aggregateByFieldAndStatus(
                                        "storeId", 20, storeIds, wkstnIds, transactionTypes, transactionFamilies, last30, today, scopedReconView))))
                .topFailingRegisters(buildRankedStats(
                        mergeNestedStatusCounts(scopedReconViews,
                                scopedReconView -> esRepository.aggregateByFieldAndStatus(
                                        transactionFamilyScoped ? "transactionFamily" : "wkstnId",
                                        20,
                                        storeIds,
                                        wkstnIds,
                                        transactionTypes,
                                        transactionFamilies,
                                        last30,
                                        today,
                                        scopedReconView))))
                .exceptionAging(buildExceptionAging(tenantId, reconView, scopedReconViews))
                .slaSummary(slaSummary)
                .build();
    }

    private List<TrendPoint> buildTrend(String fromBusinessDate,
                                        String toBusinessDate,
                                        List<String> storeIds,
                                        List<String> wkstnIds,
                                        List<String> transactionTypes,
                                        List<String> transactionFamilies,
                                        String reconView,
                                        List<String> scopedReconViews) {
        Map<String, Map<String, Long>> raw = mergeNestedStatusCounts(
                scopedReconViews,
                scopedReconView -> esRepository.aggregateByBusinessDateAndStatus(
                        storeIds, wkstnIds, transactionTypes, transactionFamilies, fromBusinessDate, toBusinessDate, scopedReconView));

        List<TrendPoint> points = new ArrayList<>();
        LocalDate start = LocalDate.parse(fromBusinessDate);
        LocalDate end = LocalDate.parse(toBusinessDate);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String businessDate = date.toString();
            points.add(toTrendPoint(
                    businessDate,
                    raw.getOrDefault(businessDate, Map.of()),
                    reconView
            ));
        }

        return points;
    }

    private TrendPoint toTrendPoint(String date,
                                    Map<String, Long> byStatus,
                                    String reconView) {
        long total = sum(byStatus);
        long matched = byStatus.getOrDefault("MATCHED", 0L);
        long missing = countTargetStatus(byStatus, reconView, "MISSING_IN_");
        long duplicates = countTargetStatus(byStatus, reconView, "DUPLICATE_IN_");
        long quantityMismatch = byStatus.getOrDefault("QUANTITY_MISMATCH", 0L);
        long totalMismatch = byStatus.getOrDefault("TOTAL_MISMATCH", 0L);
        long itemMissing = byStatus.getOrDefault("ITEM_MISSING", 0L);

        return TrendPoint.builder()
                .businessDate(date)
                .totalTransactions(total)
                .matched(matched)
                .missing(missing)
                .duplicates(duplicates)
                .quantityMismatch(quantityMismatch)
                .totalMismatch(totalMismatch)
                .itemMissing(itemMissing)
                .matchRate(total > 0
                        ? Math.round((double) matched / total * 10000.0) / 100.0
                        : 0.0)
                .build();
    }

    private List<RankedLocationStat> buildRankedStats(Map<String, Map<String, Long>> raw) {
        return raw.entrySet().stream()
                .map(entry -> {
                    long total = sum(entry.getValue());
                    long matched = entry.getValue().getOrDefault("MATCHED", 0L);
                    long missing = countPrefix(entry.getValue(), "MISSING_IN_");
                    long duplicates = countPrefix(entry.getValue(), "DUPLICATE_IN_");
                    long exceptionCount = total - matched;
                    double matchRate = total > 0
                            ? Math.round((double) matched / total * 10000.0) / 100.0
                            : 0.0;
                    return RankedLocationStat.builder()
                            .key(entry.getKey())
                            .totalTransactions(total)
                            .exceptionCount(exceptionCount)
                            .missing(missing)
                            .duplicates(duplicates)
                            .matchRate(matchRate)
                            .build();
                })
                .filter(stat -> stat.getKey() != null && !stat.getKey().isBlank())
                .sorted(Comparator
                        .comparingLong(RankedLocationStat::getExceptionCount).reversed()
                        .thenComparingLong(RankedLocationStat::getMissing).reversed()
                        .thenComparingDouble(RankedLocationStat::getMatchRate))
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<ExceptionAgingBucket> buildExceptionAging(String tenantId,
                                                           String reconView,
                                                           List<String> scopedReconViews) {
        String normalizedReconView = reconView == null || reconView.isBlank()
                ? null
                : reconView.toUpperCase();
        List<ExceptionCase> cases = exceptionCaseRepository.findActiveCasesForAging(
                tenantId,
                normalizedReconView,
                LocalDateTime.now().minusDays(30)
        ).stream()
                .filter(exceptionCase -> normalizedReconView != null
                        || scopedReconViews.contains(normalizeReconView(exceptionCase.getReconView())))
                .toList();

        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("0-1 day", 0L);
        buckets.put("2-3 days", 0L);
        buckets.put("4-7 days", 0L);
        buckets.put("7+ days", 0L);

        LocalDate today = LocalDate.now();
        for (ExceptionCase exceptionCase : cases) {
            long ageDays = ChronoUnit.DAYS.between(exceptionCase.getUpdatedAt().toLocalDate(), today);
            String bucket = ageDays <= 1 ? "0-1 day"
                    : ageDays <= 3 ? "2-3 days"
                    : ageDays <= 7 ? "4-7 days"
                    : "7+ days";
            buckets.put(bucket, buckets.get(bucket) + 1);
        }

        List<ExceptionAgingBucket> response = new ArrayList<>();
        buckets.forEach((label, count) -> response.add(ExceptionAgingBucket.builder()
                .label(label)
                .count(count)
                .build()));
        return response;
    }

    private long sum(Map<String, Long> byStatus) {
        return byStatus.values().stream().mapToLong(Long::longValue).sum();
    }

    private long countTargetStatus(Map<String, Long> byStatus, String reconView, String prefix) {
        if (reconView == null || reconView.isBlank()) {
            return countPrefix(byStatus, prefix);
        }
        String target = reconModuleService.resolveTargetSystem(reconView, "SIM");
        return byStatus.getOrDefault(prefix + target, 0L);
    }

    private long countPrefix(Map<String, Long> byStatus, String prefix) {
        return byStatus.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith(prefix))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private List<String> resolveScopedReconViews(String reconView, Collection<String> allowedReconViews) {
        if (reconView != null && !reconView.isBlank()) {
            return List.of(normalizeReconView(reconView));
        }
        if (allowedReconViews == null) {
            return List.of();
        }
        return allowedReconViews.stream()
                .map(this::normalizeReconView)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String normalizeReconView(String reconView) {
        if (reconView == null || reconView.isBlank()) {
            return null;
        }
        return reconView.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Map<String, Long>> mergeNestedStatusCounts(
            List<String> scopedReconViews,
            java.util.function.Function<String, Map<String, Map<String, Long>>> loader) {
        if (scopedReconViews.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Long>> merged = new LinkedHashMap<>();
        for (String reconView : scopedReconViews) {
            loader.apply(reconView).forEach((key, statusCounts) -> {
                Map<String, Long> target = merged.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
                statusCounts.forEach((status, count) -> target.merge(status, count, Long::sum));
            });
        }
        return merged;
    }
}
