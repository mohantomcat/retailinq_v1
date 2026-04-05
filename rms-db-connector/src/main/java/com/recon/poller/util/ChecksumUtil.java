package com.recon.rms.util;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ChecksumUtil {

    /**
     * Must stay aligned with the checksum strategy used by the paired inventory publisher.
     * Both sides sort by itemId only because line sequence is not part of the normalized payload.
     * Price is excluded because this reconciliation payload is quantity-based.
     */
    public static String compute(List<? extends ReconcilableItem> items) {
        if (items == null || items.isEmpty()) {
            return DigestUtils.sha256Hex("");
        }

        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (ReconcilableItem item : items) {
            if (item == null
                    || item.getItemId() == null
                    || item.getQuantity() == null) {
                continue;
            }

            String lineType = normalizeLineType(item.getLineType());
            String itemId = item.getItemId().trim();
            String uom = normalizeText(item.getUnitOfMeasure());
            String key = lineType + "|" + itemId + "|" + uom;

            aggregates.computeIfAbsent(key, ignored ->
                            new Aggregate(lineType, itemId, uom))
                    .add(item.getQuantity());
        }

        String canonical = aggregates.values().stream()
                .sorted(Comparator
                        .comparing((Aggregate i) -> i.lineType)
                        .thenComparing(i -> i.itemId)
                        .thenComparing(i -> i.uom))
                .map(i ->
                        i.lineType + ":" +
                                i.itemId + ":" +
                                normalizeQuantity(i.quantity) + ":" +
                                i.uom)
                .collect(Collectors.joining("|"));
        return DigestUtils.sha256Hex(canonical);
    }

    private static String normalizeLineType(String lineType) {
        return normalizeText(Objects.toString(lineType, "Unknown"));
    }

    private static String normalizeText(String value) {
        return Objects.toString(value, "").trim().toUpperCase();
    }

    private static String normalizeQuantity(java.math.BigDecimal value) {
        if (value == null) return "0";
        java.math.BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private static final class Aggregate {
        private final String lineType;
        private final String itemId;
        private final String uom;
        private java.math.BigDecimal quantity = java.math.BigDecimal.ZERO;

        private Aggregate(String lineType, String itemId, String uom) {
            this.lineType = lineType;
            this.itemId = itemId;
            this.uom = uom;
        }

        private void add(java.math.BigDecimal value) {
            quantity = quantity.add(value);
        }
    }
}

