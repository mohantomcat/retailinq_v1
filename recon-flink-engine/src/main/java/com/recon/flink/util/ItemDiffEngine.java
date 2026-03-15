package com.recon.flink.util;

import com.recon.flink.domain.DiscrepancyType;
import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.ItemDiscrepancy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ItemDiffEngine {

    public static List<ItemDiscrepancy> diff(
            FlatLineItem[] xstoreItems,
            FlatLineItem[] siocsItems) {
        return diff(xstoreItems, siocsItems, "Xstore", "SIOCS");
    }

    public static List<ItemDiscrepancy> diff(
            FlatLineItem[] leftItems,
            FlatLineItem[] rightItems,
            String leftLabel,
            String rightLabel) {

        List<ItemDiscrepancy> discrepancies = new ArrayList<>();

        if (leftItems == null) leftItems = new FlatLineItem[0];
        if (rightItems == null) rightItems = new FlatLineItem[0];

        Map<String, AggregatedItem> xstoreMap = aggregate(leftItems);
        Map<String, AggregatedItem> siocsMap = aggregate(rightItems);

        for (Map.Entry<String, AggregatedItem> entry : xstoreMap.entrySet()) {
            String compositeKey = entry.getKey();
            AggregatedItem xItem = entry.getValue();
            AggregatedItem sItem = siocsMap.get(compositeKey);

            if (sItem == null) {
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(xItem.itemId)
                        .type(DiscrepancyType.ITEM_MISSING)
                        .xstoreQuantity(xItem.quantity)
                        .xstoreUom(xItem.uom)
                        .description(xItem.lineType + " item " + xItem.itemId +
                                " present in " + leftLabel + " but missing in " + rightLabel)
                        .build());
                continue;
            }

            if (xItem.quantity.compareTo(sItem.quantity) != 0) {
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(xItem.itemId)
                        .type(DiscrepancyType.QUANTITY_MISMATCH)
                        .xstoreQuantity(xItem.quantity)
                        .siocsQuantity(sItem.quantity)
                        .xstoreUom(xItem.uom)
                        .siocsUom(sItem.uom)
                        .description(xItem.lineType + " item " + xItem.itemId +
                                " qty mismatch: " + leftLabel + "=" + xItem.quantity +
                                " " + rightLabel + "=" + sItem.quantity)
                        .build());
            }

            if (!xItem.uom.isEmpty()
                    && !sItem.uom.isEmpty()
                    && !xItem.uom.equals(sItem.uom)) {
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(xItem.itemId)
                        .type(DiscrepancyType.UOM_MISMATCH)
                        .xstoreQuantity(xItem.quantity)
                        .siocsQuantity(sItem.quantity)
                        .xstoreUom(xItem.uom)
                        .siocsUom(sItem.uom)
                        .description(xItem.lineType + " item " + xItem.itemId +
                                " UOM mismatch: " + leftLabel + "=" + xItem.uom +
                                " " + rightLabel + "=" + sItem.uom)
                        .build());
            }
        }

        for (Map.Entry<String, AggregatedItem> entry : siocsMap.entrySet()) {
            if (!xstoreMap.containsKey(entry.getKey())) {
                AggregatedItem sItem = entry.getValue();
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(sItem.itemId)
                        .type(DiscrepancyType.ITEM_EXTRA)
                        .siocsQuantity(sItem.quantity)
                        .siocsUom(sItem.uom)
                        .description(sItem.lineType + " item " + sItem.itemId +
                                " present in " + rightLabel + " but missing in " + leftLabel)
                        .build());
            }
        }

        return discrepancies;
    }

    private static Map<String, AggregatedItem> aggregate(
            FlatLineItem[] items) {
        Map<String, AggregatedItem> aggregates = new LinkedHashMap<>();

        for (FlatLineItem item : items) {
            if (item == null
                    || item.getItemId() == null
                    || item.getQuantity() == null) {
                continue;
            }

            String lineType = normalize(item.getLineType(), "Unknown");
            String itemId = item.getItemId().trim();
            String key = lineType + "|" + itemId;

            aggregates.computeIfAbsent(key, ignored ->
                            new AggregatedItem(lineType, itemId))
                    .add(item.getQuantity(), item.getUnitOfMeasure());
        }

        return aggregates;
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static final class AggregatedItem {
        private final String lineType;
        private final String itemId;
        private BigDecimal quantity = BigDecimal.ZERO;
        private String uom = "";

        private AggregatedItem(String lineType, String itemId) {
            this.lineType = lineType;
            this.itemId = itemId;
        }

        private void add(BigDecimal value, String unitOfMeasure) {
            quantity = quantity.add(value);
            if (uom.isEmpty() && unitOfMeasure != null) {
                uom = unitOfMeasure.trim().toUpperCase();
            }
        }
    }
}
