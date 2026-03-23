package com.recon.api.service;

import com.recon.api.domain.*;
import com.recon.api.repository.ReconElasticsearchRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconQueryService {

    private final ReconElasticsearchRepository esRepository;

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

        long total = esRepository.count(
                req.getReconStatus(),
                req.getStoreIds(),
                req.getFromBusinessDate(),
                req.getToBusinessDate(),
                req.getWkstnIds(),
                req.getTransactionTypes(),
                req.getReconView());

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
            String transactionKey, TenantConfig tenant) {
        ReconSummary summary =
                esRepository.findByTransactionKey(transactionKey);
        if (summary == null) return null;
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

        Map<String, Long> byStatus =
                esRepository.aggregateByStatus(
                        storeIds, wkstnIds, null, fromBusinessDate, toBusinessDate, reconView);
        Map<String, Long> byStore =
                esRepository.aggregateByStore(
                        storeIds,
                        wkstnIds,
                        null,
                        fromBusinessDate, toBusinessDate, reconView);

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
                .build();
    }

    public DashboardStats getDashboardStats(
            List<String> storeIds,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView,
            TenantConfig tenant) {
        return getDashboardStats(storeIds, null, fromBusinessDate, toBusinessDate, reconView, tenant);
    }

    private long countStatus(Map<String, Long> byStatus,
                             String reconView,
                             String prefix) {
        if (reconView != null && !reconView.isBlank()) {
            return byStatus.getOrDefault(prefix + targetSystem(reconView), 0L);
        }

        return byStatus.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getKey().startsWith(prefix))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private String targetSystem(String reconView) {
        if ("XSTORE_SIOCS".equalsIgnoreCase(reconView)) {
            return "SIOCS";
        }
        if ("SIOCS_MFCS".equalsIgnoreCase(reconView)) {
            return "MFCS";
        }
        if ("XSTORE_XOCS".equalsIgnoreCase(reconView)) {
            return "XOCS";
        }
        return "SIM";
    }

    public List<ReconSummary> getMismatches(
            List<String> storeIds,
            String fromBusinessDate,
            String toBusinessDate,
            int page, int size,
            TenantConfig tenant) {

        ReconSearchRequest req = ReconSearchRequest.builder()
                .storeIds(storeIds)
                .fromBusinessDate(fromBusinessDate)
                .toBusinessDate(toBusinessDate)
                .reconStatus("ITEM_MISSING")
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
        return esRepository.aggregateDistinctWithFilter(
                        "wkstnId.keyword",
                        "storeId.keyword",
                        storeIds,
                        reconView)
                .stream().sorted()
                .collect(Collectors.toList());
    }

    public List<String> getTransactionTypes(List<String> storeIds, String reconView) {
        return esRepository.aggregateDistinctWithFilter(
                        "transactionType.keyword",
                        "storeId.keyword",
                        storeIds,
                        reconView)
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .collect(Collectors.toList());
    }
}
