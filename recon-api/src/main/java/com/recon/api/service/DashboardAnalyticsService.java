package com.recon.api.service;

import com.recon.api.domain.DashboardAnalyticsResponse;
import com.recon.api.domain.ExceptionAgingBucket;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.RankedLocationStat;
import com.recon.api.domain.TrendPoint;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ReconElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardAnalyticsService {

    private final ReconElasticsearchRepository esRepository;
    private final ExceptionCaseRepository exceptionCaseRepository;

    public DashboardAnalyticsResponse getAnalytics(List<String> storeIds,
                                                   List<String> wkstnIds,
                                                   String reconView) {
        String today = LocalDate.now().toString();
        String last7 = LocalDate.now().minusDays(6).toString();
        String last30 = LocalDate.now().minusDays(29).toString();

        return DashboardAnalyticsResponse.builder()
                .last7Days(buildTrend(last7, today, storeIds, wkstnIds, reconView))
                .last30Days(buildTrend(last30, today, storeIds, wkstnIds, reconView))
                .topFailingStores(buildRankedStats(
                        esRepository.aggregateByFieldAndStatus(
                                "storeId", 20, storeIds, wkstnIds, last30, today, reconView)))
                .topFailingRegisters(buildRankedStats(
                        esRepository.aggregateByFieldAndStatus(
                                "wkstnId", 20, storeIds, wkstnIds, last30, today, reconView)))
                .exceptionAging(buildExceptionAging(reconView))
                .build();
    }

    private List<TrendPoint> buildTrend(String fromBusinessDate,
                                        String toBusinessDate,
                                        List<String> storeIds,
                                        List<String> wkstnIds,
                                        String reconView) {
        Map<String, Map<String, Long>> raw = esRepository.aggregateByBusinessDateAndStatus(
                storeIds, wkstnIds, fromBusinessDate, toBusinessDate, reconView);

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

    private List<ExceptionAgingBucket> buildExceptionAging(String reconView) {
        String normalizedReconView = Objects.requireNonNullElse(reconView, "XSTORE_SIM").toUpperCase();
        List<ExceptionCase> cases = exceptionCaseRepository.findActiveCasesForAging(
                "tenant-india",
                normalizedReconView,
                LocalDateTime.now().minusDays(30)
        );

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
        String target = targetSystem(reconView);
        return byStatus.getOrDefault(prefix + target, 0L);
    }

    private long countPrefix(Map<String, Long> byStatus, String prefix) {
        return byStatus.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith(prefix))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private String targetSystem(String reconView) {
        if ("XSTORE_SIOCS".equalsIgnoreCase(reconView)) {
            return "SIOCS";
        }
        if ("XSTORE_XOCS".equalsIgnoreCase(reconView)) {
            return "XOCS";
        }
        return "SIM";
    }
}
