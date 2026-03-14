package com.recon.poller.aggregator;

import com.recon.poller.domain.AggregationResult;
import com.recon.poller.domain.SiocsLineItem;
import com.recon.poller.domain.SiocsRawRow;
import com.recon.poller.domain.SiocsTransactionRow;
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
public class SiocsRowAggregator {

    /**
     * Aggregates raw line item rows into transaction-level objects.
     * <p>
     * CRITICAL: If page is full (size == requestedPageSize), the last
     * EXTERNAL_ID group may be incomplete — remaining line items are
     * on the next page. Exclude it from this batch.
     * The overlap window guarantees it will be re-fetched complete.
     */
    public AggregationResult aggregate(List<SiocsRawRow> rawRows,
                                       int requestedPageSize) {
        if (rawRows == null || rawRows.isEmpty()) {
            return AggregationResult.empty();
        }

        List<SiocsRawRow> safeRows = rawRows;
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

        List<SiocsTransactionRow> transactions = aggregateRows(safeRows);

        return AggregationResult.builder()
                .transactions(transactions)
                .excludedExternalId(excludedExternalId)
                .pageWasFull(rawRows.size() == requestedPageSize)
                .singleTransactionPage(false)
                .build();
    }

    private List<SiocsTransactionRow> aggregateRows(
            List<SiocsRawRow> rows) {
        // Group by EXTERNAL_ID preserving insertion order
        Map<String, List<SiocsRawRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(
                        SiocsRawRow::getExternalId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return grouped.entrySet().stream()
                .map(e -> buildTransaction(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private SiocsTransactionRow buildTransaction(String externalId,
                                                 List<SiocsRawRow> rows) {
        SiocsRawRow first = rows.get(0);

        // Duplicate detection — multiple TRANSACTION_EXTENDED_IDs
        long distinctExtendedIds = rows.stream()
                .map(SiocsRawRow::getTransactionExtendedId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        List<SiocsLineItem> lineItems = rows.stream()
                .map(this::mapToLineItem)
                .collect(Collectors.toList());

        BigDecimal totalQty = lineItems.stream()
                .map(SiocsLineItem::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int postingCount = (int) distinctExtendedIds;

        return SiocsTransactionRow.builder()
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
                .duplicateFlag(postingCount > 1)
                .build();
    }

    private SiocsLineItem mapToLineItem(SiocsRawRow row) {
        return SiocsLineItem.builder()
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