package com.recon.api.service;

import com.recon.api.domain.*;
import com.recon.api.repository.ReconElasticsearchRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconQueryService {

    private final ReconElasticsearchRepository esRepository;
    private final ReconModuleService reconModuleService;

    public PagedResult<ReconSummary> search(
            ReconSearchRequest req, TenantConfig tenant) {

        // Validate fromBusinessDate and toBusinessDate
        if (req.getFromBusinessDate() != null
                && !req.getFromBusinessDate().isBlank()
                && !TimezoneConverter.isValidIsoDate(
                req.getFromBusinessDate())) {
            throw new IllegalArgumentException(
                    "fromBusinessDate must be yyyy-MM-dd. Received: "
                            + req.getFromBusinessDate());
        }
        if (req.getToBusinessDate() != null
                && !req.getToBusinessDate().isBlank()
                && !TimezoneConverter.isValidIsoDate(
                req.getToBusinessDate())) {
            throw new IllegalArgumentException(
                    "toBusinessDate must be yyyy-MM-dd. Received: "
                            + req.getToBusinessDate());
        }

        List<ReconSummary> results = esRepository.search(req)
                .stream()
                .map(s -> convertTimestamps(s, tenant))
                .collect(Collectors.toList());

        long total = esRepository.count(req);

        int totalPages = (int) Math.ceil(
                (double) total / req.getSize());

        return PagedResult.<ReconSummary>builder()
                .content(results)
                .page(req.getPage())
                .size(req.getSize())
                .totalElements(total)
                .totalPages(totalPages)
                .last(req.getPage() >= totalPages - 1)
                .build();
    }

    public ReconSummary getByTransactionKey(
            String transactionKey, String reconView, TenantConfig tenant) {
        ReconSummary summary =
                esRepository.findByTransactionKey(transactionKey, reconView);
        if (summary == null) return null;
        return convertTimestamps(summary, tenant);
    }

    public ReconSummary getByTransactionKey(String transactionKey,
                                            String reconView,
                                            Collection<String> allowedReconViews,
                                            TenantConfig tenant) {
        ReconSummary summary = esRepository.findByTransactionKey(
                transactionKey,
                reconView,
                resolveScopedReconViews(reconView, allowedReconViews));
        if (summary == null) {
            return null;
        }
        return convertTimestamps(summary, tenant);
    }

    public Map<String, ReconSummary> getByTransactionKeys(
            List<String> transactionKeys, TenantConfig tenant) {
        if (transactionKeys == null || transactionKeys.isEmpty()) {
            return Map.of();
        }

        Map<String, ReconSummary> result = new LinkedHashMap<>();
        int batchSize = 250;
        for (int start = 0; start < transactionKeys.size(); start += batchSize) {
            int end = Math.min(start + batchSize, transactionKeys.size());
            List<String> batch = transactionKeys.subList(start, end);
            esRepository.findByTransactionKeys(batch).stream()
                    .map(summary -> convertTimestamps(summary, tenant))
                    .forEach(summary -> result.put(summaryKey(summary), summary));
        }
        return result;
    }

    public DashboardStats getDashboardStats(
            List<String> storeIds,
            List<String> wkstnIds,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView,
            TenantConfig tenant) {
        return getDashboardStats(storeIds, wkstnIds, null, fromBusinessDate, toBusinessDate, reconView, null, tenant);
    }

    public DashboardStats getDashboardStats(
            List<String> storeIds,
            List<String> wkstnIds,
            List<String> transactionFamilies,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView,
            TenantConfig tenant) {
        return getDashboardStats(
                storeIds,
                wkstnIds,
                transactionFamilies,
                fromBusinessDate,
                toBusinessDate,
                reconView,
                null,
                tenant);
    }

    public DashboardStats getDashboardStats(
            List<String> storeIds,
            List<String> wkstnIds,
            List<String> transactionFamilies,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView,
            Collection<String> allowedReconViews,
            TenantConfig tenant) {

        List<String> scopedReconViews = resolveScopedReconViews(reconView, allowedReconViews);

        Map<String, Long> byStatus =
                mergeStatusCounts(scopedReconViews, scopedReconView -> esRepository.aggregateByStatus(
                        storeIds, wkstnIds, null, transactionFamilies, fromBusinessDate, toBusinessDate, scopedReconView));
        Map<String, Long> byStore = mergeFlatCounts(scopedReconViews, scopedReconView -> esRepository.aggregateByStore(
                storeIds,
                wkstnIds,
                null,
                transactionFamilies,
                fromBusinessDate,
                toBusinessDate,
                scopedReconView));
        Map<String, Long> byTransactionFamily = mergeFlatCounts(scopedReconViews, scopedReconView -> esRepository.aggregateByTransactionFamily(
                storeIds,
                wkstnIds,
                null,
                transactionFamilies,
                fromBusinessDate,
                toBusinessDate,
                scopedReconView));
        List<TransactionFamilyVolumeDto> transactionFamilyVolumes = mergeTransactionFamilyVolumes(
                scopedReconViews,
                scopedReconView -> esRepository.aggregateTransactionFamilyVolumes(
                        storeIds,
                        wkstnIds,
                        null,
                        transactionFamilies,
                        fromBusinessDate,
                        toBusinessDate,
                        scopedReconView));
        QuantityTotalsDto quantityTotals = mergeQuantityTotals(
                scopedReconViews,
                scopedReconView -> esRepository.aggregateQuantityTotals(
                        storeIds,
                        wkstnIds,
                        null,
                        transactionFamilies,
                        fromBusinessDate,
                        toBusinessDate,
                        scopedReconView));

        long matched =
                byStatus.getOrDefault("MATCHED", 0L);
        long missing = countStatus(byStatus, reconView, "MISSING_IN_");
        long itemMissing =
                byStatus.getOrDefault("ITEM_MISSING", 0L);
        long qtyMismatch =
                byStatus.getOrDefault("QUANTITY_MISMATCH", 0L);
        long totalMismatch =
                byStatus.getOrDefault("TOTAL_MISMATCH", 0L);
        long pending = countStatus(byStatus, reconView, "PROCESSING_PENDING_IN_");
        long failed = countStatus(byStatus, reconView, "PROCESSING_FAILED_IN_");
        long duplicate = countStatus(byStatus, reconView, "DUPLICATE_IN_");
        long awaiting = countStatus(byStatus, reconView, "AWAITING_");

        long total = byStatus.values().stream()
                .mapToLong(Long::longValue).sum();

        double matchRate = total > 0
                ? Math.round(
                (double) matched / total * 10000.0) / 100.0
                : 0.0;

        return DashboardStats.builder()
                .totalTransactions(total)
                .matched(matched)
                .missingInSiocs(missing)
                .itemMissing(itemMissing)
                .quantityMismatch(qtyMismatch)
                .transactionTotalMismatch(totalMismatch)
                .processingPending(pending)
                .processingFailed(failed)
                .duplicateInSiocs(duplicate)
                .duplicateTransactions(duplicate)
                .awaitingSim(awaiting)
                .matchRate(matchRate)
                .asOf(TimezoneConverter.toDisplay(
                        Instant.now().toString(), tenant))
                .byStore(byStore)
                .byStatus(byStatus)
                .byTransactionFamily(byTransactionFamily)
                .transactionFamilyVolumes(transactionFamilyVolumes)
                .sourceQuantityTotal(quantityTotals.getSourceQuantityTotal())
                .targetQuantityTotal(quantityTotals.getTargetQuantityTotal())
                .quantityVarianceTotal(quantityTotals.getQuantityVarianceTotal())
                .quantityMetricsTransactionCount(quantityTotals.getQuantityMetricsTransactionCount())
                .build();
    }

    public DashboardStats getDashboardStats(
            List<String> storeIds,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView,
            TenantConfig tenant) {
        return getDashboardStats(storeIds, null, null, fromBusinessDate, toBusinessDate, reconView, null, tenant);
    }

    private long countStatus(Map<String, Long> byStatus,
                             String reconView,
                             String prefix) {
        if (reconView != null && !reconView.isBlank()) {
            return byStatus.getOrDefault(prefix + reconModuleService.resolveTargetSystem(reconView, "SIM"), 0L);
        }

        return byStatus.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getKey().startsWith(prefix))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    public List<ReconSummary> getMismatches(
            List<String> storeIds,
            String fromBusinessDate,
            String toBusinessDate,
            int page, int size,
            TenantConfig tenant) {
        return getMismatches(storeIds, fromBusinessDate, toBusinessDate, page, size, null, tenant);
    }

    public List<ReconSummary> getMismatches(
            List<String> storeIds,
            String fromBusinessDate,
            String toBusinessDate,
            int page, int size,
            Collection<String> allowedReconViews,
            TenantConfig tenant) {

        List<String> scopedReconViews = resolveScopedReconViews(null, allowedReconViews);

        ReconSearchRequest req = ReconSearchRequest.builder()
                .storeIds(storeIds)
                .fromBusinessDate(fromBusinessDate)
                .toBusinessDate(toBusinessDate)
                .reconStatus("ITEM_MISSING")
                .reconViews(scopedReconViews)
                .page(page)
                .size(size)
                .build();

        List<ReconSummary> itemMissing =
                esRepository.search(req).stream()
                        .map(s -> convertTimestamps(s, tenant))
                        .collect(Collectors.toList());

        req.setReconStatus("QUANTITY_MISMATCH");
        List<ReconSummary> qtyMismatch =
                esRepository.search(req).stream()
                        .map(s -> convertTimestamps(s, tenant))
                        .collect(Collectors.toList());

        itemMissing.addAll(qtyMismatch);
        return itemMissing;
    }

    private ReconSummary convertTimestamps(
            ReconSummary summary, TenantConfig tenant) {
        if (tenant == null) return summary;

        summary.setReconciledAt(
                TimezoneConverter.toDisplay(
                        summary.getReconciledAt(), tenant));
        summary.setUpdatedAt(
                TimezoneConverter.toDisplay(
                        summary.getUpdatedAt(), tenant));

        // Set display field — NEVER mutate businessDate
        summary.setBusinessDateDisplay(
                TimezoneConverter.dateToDisplay(
                        summary.getBusinessDate(), tenant));

        return summary;
    }

    private String summaryKey(ReconSummary summary) {
        return Objects.toString(summary.getReconView(), "").toUpperCase()
                + "::"
                + Objects.toString(summary.getTransactionKey(), "");
    }

    public List<String> getStores() {
        return getStores(null);
    }

    public List<String> getStores(String reconView) {
        return esRepository.aggregateDistinct("storeId.keyword", reconView)
                .stream().sorted()
                .collect(Collectors.toList());
    }

    public List<String> getRegisters(List<String> storeIds, String reconView) {
        return getRegisters(storeIds, reconView, null);
    }

    public List<String> getRegisters(List<String> storeIds,
                                     String reconView,
                                     Collection<String> allowedReconViews) {
        return esRepository.aggregateDistinctWithFilter(
                        "wkstnId.keyword",
                        "storeId.keyword",
                        storeIds,
                        reconView,
                        resolveScopedReconViews(reconView, allowedReconViews))
                .stream().sorted()
                .collect(Collectors.toList());
    }

    public List<String> getTransactionTypes(List<String> storeIds, String reconView) {
        return getTransactionTypes(storeIds, reconView, null);
    }

    public List<String> getTransactionTypes(List<String> storeIds,
                                            String reconView,
                                            Collection<String> allowedReconViews) {
        return esRepository.aggregateDistinctWithFilter(
                        "transactionType.keyword",
                        "storeId.keyword",
                        storeIds,
                        reconView,
                        resolveScopedReconViews(reconView, allowedReconViews))
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getTransactionFamilies(List<String> storeIds, String reconView) {
        return getTransactionFamilies(storeIds, reconView, null);
    }

    public List<String> getTransactionFamilies(List<String> storeIds,
                                               String reconView,
                                               Collection<String> allowedReconViews) {
        return esRepository.aggregateDistinctWithFilter(
                        "transactionFamily.keyword",
                        "storeId.keyword",
                        storeIds,
                        reconView,
                        resolveScopedReconViews(reconView, allowedReconViews))
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> resolveScopedReconViews(String reconView, Collection<String> allowedReconViews) {
        if (reconView != null && !reconView.isBlank()) {
            return List.of(reconView.trim().toUpperCase(Locale.ROOT));
        }
        if (allowedReconViews == null) {
            return List.of();
        }
        return allowedReconViews.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private Map<String, Long> mergeStatusCounts(List<String> scopedReconViews,
                                                java.util.function.Function<String, Map<String, Long>> loader) {
        if (scopedReconViews.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> merged = new LinkedHashMap<>();
        for (String reconView : scopedReconViews) {
            loader.apply(reconView).forEach((key, value) -> merged.merge(key, value, Long::sum));
        }
        return merged;
    }

    private Map<String, Long> mergeFlatCounts(List<String> scopedReconViews,
                                              java.util.function.Function<String, Map<String, Long>> loader) {
        return mergeStatusCounts(scopedReconViews, loader);
    }

    private List<TransactionFamilyVolumeDto> mergeTransactionFamilyVolumes(
            List<String> scopedReconViews,
            java.util.function.Function<String, List<TransactionFamilyVolumeDto>> loader) {
        if (scopedReconViews.isEmpty()) {
            return List.of();
        }
        Map<String, TransactionFamilyVolumeDto> merged = new LinkedHashMap<>();
        for (String reconView : scopedReconViews) {
            for (TransactionFamilyVolumeDto item : loader.apply(reconView)) {
                String family = item.getTransactionFamily();
                TransactionFamilyVolumeDto current = merged.get(family);
                if (current == null) {
                    merged.put(family, TransactionFamilyVolumeDto.builder()
                            .transactionFamily(item.getTransactionFamily())
                            .transactionCount(item.getTransactionCount())
                            .quantityMetricsTransactionCount(item.getQuantityMetricsTransactionCount())
                            .sourceQuantityTotal(defaultBigDecimal(item.getSourceQuantityTotal()))
                            .targetQuantityTotal(defaultBigDecimal(item.getTargetQuantityTotal()))
                            .quantityVarianceTotal(defaultBigDecimal(item.getQuantityVarianceTotal()))
                            .valueMetricsAvailable(item.isValueMetricsAvailable())
                            .build());
                    continue;
                }
                merged.put(family, TransactionFamilyVolumeDto.builder()
                        .transactionFamily(current.getTransactionFamily())
                        .transactionCount(current.getTransactionCount() + item.getTransactionCount())
                        .quantityMetricsTransactionCount(current.getQuantityMetricsTransactionCount() + item.getQuantityMetricsTransactionCount())
                        .sourceQuantityTotal(defaultBigDecimal(current.getSourceQuantityTotal()).add(defaultBigDecimal(item.getSourceQuantityTotal())))
                        .targetQuantityTotal(defaultBigDecimal(current.getTargetQuantityTotal()).add(defaultBigDecimal(item.getTargetQuantityTotal())))
                        .quantityVarianceTotal(defaultBigDecimal(current.getQuantityVarianceTotal()).add(defaultBigDecimal(item.getQuantityVarianceTotal())))
                        .valueMetricsAvailable(current.isValueMetricsAvailable() || item.isValueMetricsAvailable())
                        .build());
            }
        }
        return merged.values().stream().toList();
    }

    private QuantityTotalsDto mergeQuantityTotals(List<String> scopedReconViews,
                                                  java.util.function.Function<String, QuantityTotalsDto> loader) {
        if (scopedReconViews.isEmpty()) {
            return QuantityTotalsDto.builder()
                    .sourceQuantityTotal(java.math.BigDecimal.ZERO)
                    .targetQuantityTotal(java.math.BigDecimal.ZERO)
                    .quantityVarianceTotal(java.math.BigDecimal.ZERO)
                    .quantityMetricsTransactionCount(0L)
                    .build();
        }
        QuantityTotalsDto merged = QuantityTotalsDto.builder().build();
        java.math.BigDecimal sourceTotal = java.math.BigDecimal.ZERO;
        java.math.BigDecimal targetTotal = java.math.BigDecimal.ZERO;
        java.math.BigDecimal varianceTotal = java.math.BigDecimal.ZERO;
        long quantityCount = 0L;
        for (String reconView : scopedReconViews) {
            QuantityTotalsDto totals = loader.apply(reconView);
            sourceTotal = sourceTotal.add(defaultBigDecimal(totals.getSourceQuantityTotal()));
            targetTotal = targetTotal.add(defaultBigDecimal(totals.getTargetQuantityTotal()));
            varianceTotal = varianceTotal.add(defaultBigDecimal(totals.getQuantityVarianceTotal()));
            quantityCount += totals.getQuantityMetricsTransactionCount();
        }
        return QuantityTotalsDto.builder()
                .sourceQuantityTotal(sourceTotal)
                .targetQuantityTotal(targetTotal)
                .quantityVarianceTotal(varianceTotal)
                .quantityMetricsTransactionCount(quantityCount)
                .build();
    }

    private java.math.BigDecimal defaultBigDecimal(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }
}
