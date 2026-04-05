package com.recon.rms.aggregator;

import com.recon.rms.domain.AggregationResult;
import com.recon.rms.domain.RmsLineItem;
import com.recon.rms.domain.RmsRawRow;
import com.recon.rms.domain.RmsTransactionRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RmsRowAggregator {

    /**
     * Aggregates raw line item rows into transaction-level objects.
     * <p>
     * CRITICAL: If page is full (size == requestedPageSize), the last
     * EXTERNAL_ID group may be incomplete â€” remaining line items are
     * on the next page. Exclude it from this batch.
     * The overlap window guarantees it will be re-fetched complete.
     */
    public AggregationResult aggregate(List<RmsRawRow> rawRows,
                                       int requestedPageSize) {
        if (rawRows == null || rawRows.isEmpty()) {
            return AggregationResult.empty();
        }

        List<RmsRawRow> safeRows = rawRows;
        String excludedExternalId = null;

        // Page boundary protection
        if (rawRows.size() == requestedPageSize) {
            excludedExternalId =
                    rawRows.get(rawRows.size() - 1).getExternalId();
            final String excluded = excludedExternalId;

            safeRows = rawRows.stream()
                    .filter(r -> !r.getExternalId().equals(excluded))
                    .collect(Collectors.toList());

            log.debug("Page boundary: excluded externalId={} from batch",
                    excludedExternalId);

            if (safeRows.isEmpty()) {
                // Entire page consumed by one huge transaction
                log.warn("Single transaction fills entire page. " +
                                "externalId={} pageSize={}. Consider increasing pageSize.",
                        excludedExternalId, requestedPageSize);
                return AggregationResult.singleTransactionPage(
                        excludedExternalId);
            }
        }

        List<RmsTransactionRow> transactions = aggregateRows(safeRows);

        return AggregationResult.builder()
                .transactions(transactions)
                .excludedExternalId(excludedExternalId)
                .pageWasFull(rawRows.size() == requestedPageSize)
                .singleTransactionPage(false)
                .build();
    }

    private List<RmsTransactionRow> aggregateRows(
            List<RmsRawRow> rows) {
        // Group by EXTERNAL_ID preserving insertion order
        Map<String, List<RmsRawRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(
                        RmsRawRow::getExternalId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return grouped.entrySet().stream()
                .map(e -> buildTransaction(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private RmsTransactionRow buildTransaction(String externalId,
                                                 List<RmsRawRow> rows) {
        RmsRawRow first = rows.get(0);

        // Duplicate detection â€” multiple TRANSACTION_EXTENDED_IDs
        long distinctExtendedIds = rows.stream()
                .map(RmsRawRow::getTransactionExtendedId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long duplicateItemKeys = rows.stream()
                .filter(row -> row.getRequestId() != null
                        && row.getExternalId() != null
                        && row.getItemId() != null)
                .collect(Collectors.groupingBy(
                        row -> row.getRequestId() + "|" + row.getExternalId() + "|" + row.getItemId(),
                        LinkedHashMap::new,
                        Collectors.counting()))
                .values().stream()
                .filter(count -> count > 1)
                .count();

        List<RmsLineItem> lineItems = rows.stream()
                .map(this::mapToLineItem)
                .collect(Collectors.toList());

        BigDecimal totalQty = lineItems.stream()
                .map(RmsLineItem::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int postingCount = (int) Math.max(distinctExtendedIds, duplicateItemKeys + 1);

        return RmsTransactionRow.builder()
                .externalId(externalId)
                .requestId(first.getRequestId())
                .storeId(first.getStoreId())
                .transactionDateTime(first.getTransactionDateTime())
                .updateDateTime(first.getUpdateDateTime())
                .type(first.getType())
                .processingStatus(first.getProcessingStatus())
                .lineItems(lineItems)
                .lineItemCount(lineItems.size())
                .totalQuantity(totalQty)
                .postingCount(postingCount)
                .duplicateFlag(distinctExtendedIds > 1 || duplicateItemKeys > 0)
                .build();
    }

    private RmsLineItem mapToLineItem(RmsRawRow row) {
        return RmsLineItem.builder()
                .id(row.getId())
                .itemId(row.getItemId())
                .quantity(row.getQuantity())
                .unitOfMeasure(row.getUnitOfMeasure())
                .type(row.getType())
                .processingStatus(row.getProcessingStatus())
                .transactionExtendedId(row.getTransactionExtendedId())
                .build();
    }
}

