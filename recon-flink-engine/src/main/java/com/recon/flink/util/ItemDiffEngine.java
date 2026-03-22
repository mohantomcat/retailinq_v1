package com.recon.flink.util;

import com.recon.flink.domain.DiscrepancyType;
import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.ItemDiscrepancy;
import com.recon.flink.domain.MatchToleranceProfile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ItemDiffEngine {

    private static final MatchToleranceProfile LEGACY_ZERO_TOLERANCE = new MatchToleranceProfile(
            "LEGACY_EXACT",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
    );

    public static List<ItemDiscrepancy> diff(
            FlatLineItem[] xstoreItems,
            FlatLineItem[] siocsItems) {
        return diff(xstoreItems, siocsItems, "Xstore", "SIOCS", LEGACY_ZERO_TOLERANCE);
    }

    public static List<ItemDiscrepancy> diff(
            FlatLineItem[] leftItems,
            FlatLineItem[] rightItems,
            String leftLabel,
            String rightLabel) {
        return diff(leftItems, rightItems, leftLabel, rightLabel, LEGACY_ZERO_TOLERANCE);
    }

    public static List<ItemDiscrepancy> diff(
            FlatLineItem[] leftItems,
            FlatLineItem[] rightItems,
            String leftLabel,
            String rightLabel,
            MatchToleranceProfile toleranceProfile) {

        List<ItemDiscrepancy> discrepancies = new ArrayList<>();

        if (leftItems == null) leftItems = new FlatLineItem[0];
        if (rightItems == null) rightItems = new FlatLineItem[0];

        MatchToleranceProfile effectiveProfile = toleranceProfile != null
                ? toleranceProfile
                : LEGACY_ZERO_TOLERANCE;

        Map<String, AggregatedItem> leftMap = aggregate(leftItems);
        Map<String, AggregatedItem> rightMap = aggregate(rightItems);

        for (Map.Entry<String, AggregatedItem> entry : leftMap.entrySet()) {
            String compositeKey = entry.getKey();
            AggregatedItem leftItem = entry.getValue();
            AggregatedItem rightItem = rightMap.get(compositeKey);

            if (rightItem == null) {
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(leftItem.itemId)
                        .lineType(leftItem.lineType)
                        .type(DiscrepancyType.ITEM_MISSING)
                        .xstoreQuantity(leftItem.quantity)
                        .xstoreAmount(leftItem.amount)
                        .xstoreUom(leftItem.uom)
                        .withinTolerance(false)
                        .severityBand("HIGH")
                        .description(leftItem.lineType + " item " + leftItem.itemId +
                                " present in " + leftLabel + " but missing in " + rightLabel)
                        .build());
                continue;
            }

            MatchScoringEngine.ToleranceAssessment quantityAssessment = MatchScoringEngine.assessVariance(
                    leftItem.quantity,
                    rightItem.quantity,
                    effectiveProfile.quantityAbsoluteTolerance(),
                    effectiveProfile.quantityPercentTolerance()
            );
            if (quantityAssessment.variance() != null
                    && quantityAssessment.variance().compareTo(BigDecimal.ZERO) > 0) {
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(leftItem.itemId)
                        .lineType(leftItem.lineType)
                        .type(DiscrepancyType.QUANTITY_MISMATCH)
                        .xstoreQuantity(leftItem.quantity)
                        .siocsQuantity(rightItem.quantity)
                        .varianceQuantity(quantityAssessment.variance())
                        .variancePercent(quantityAssessment.variancePercent())
                        .xstoreAmount(leftItem.amount)
                        .siocsAmount(rightItem.amount)
                        .xstoreUom(leftItem.uom)
                        .siocsUom(rightItem.uom)
                        .withinTolerance(quantityAssessment.withinTolerance())
                        .toleranceType(quantityAssessment.toleranceType())
                        .toleranceValue(quantityAssessment.toleranceValue())
                        .severityBand(quantityAssessment.withinTolerance()
                                ? "LOW"
                                : severityBand(quantityAssessment.variancePercent()))
                        .description(leftItem.lineType + " item " + leftItem.itemId +
                                " qty mismatch: " + leftLabel + "=" + leftItem.quantity +
                                " " + rightLabel + "=" + rightItem.quantity)
                        .build());
            }

            MatchScoringEngine.ToleranceAssessment amountAssessment = MatchScoringEngine.assessVariance(
                    leftItem.amount,
                    rightItem.amount,
                    effectiveProfile.amountAbsoluteTolerance(),
                    effectiveProfile.amountPercentTolerance()
            );
            if (amountAssessment.variance() != null
                    && amountAssessment.variance().compareTo(BigDecimal.ZERO) > 0) {
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(leftItem.itemId)
                        .lineType(leftItem.lineType)
                        .type(DiscrepancyType.AMOUNT_MISMATCH)
                        .xstoreQuantity(leftItem.quantity)
                        .siocsQuantity(rightItem.quantity)
                        .xstoreAmount(leftItem.amount)
                        .siocsAmount(rightItem.amount)
                        .varianceAmount(amountAssessment.variance())
                        .varianceAmountPercent(amountAssessment.variancePercent())
                        .xstoreUom(leftItem.uom)
                        .siocsUom(rightItem.uom)
                        .withinTolerance(amountAssessment.withinTolerance())
                        .toleranceType(amountAssessment.toleranceType())
                        .toleranceValue(amountAssessment.toleranceValue())
                        .severityBand(amountAssessment.withinTolerance()
                                ? "LOW"
                                : severityBand(amountAssessment.variancePercent()))
                        .description(leftItem.lineType + " item " + leftItem.itemId +
                                " amount mismatch: " + leftLabel + "=" + safeAmount(leftItem.amount) +
                                " " + rightLabel + "=" + safeAmount(rightItem.amount))
                        .build());
            }

            if (!leftItem.uom.isEmpty()
                    && !rightItem.uom.isEmpty()
                    && !leftItem.uom.equals(rightItem.uom)) {
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(leftItem.itemId)
                        .lineType(leftItem.lineType)
                        .type(DiscrepancyType.UOM_MISMATCH)
                        .xstoreQuantity(leftItem.quantity)
                        .siocsQuantity(rightItem.quantity)
                        .xstoreAmount(leftItem.amount)
                        .siocsAmount(rightItem.amount)
                        .xstoreUom(leftItem.uom)
                        .siocsUom(rightItem.uom)
                        .withinTolerance(false)
                        .severityBand("MEDIUM")
                        .description(leftItem.lineType + " item " + leftItem.itemId +
                                " UOM mismatch: " + leftLabel + "=" + leftItem.uom +
                                " " + rightLabel + "=" + rightItem.uom)
                        .build());
            }
        }

        for (Map.Entry<String, AggregatedItem> entry : rightMap.entrySet()) {
            if (!leftMap.containsKey(entry.getKey())) {
                AggregatedItem rightItem = entry.getValue();
                discrepancies.add(ItemDiscrepancy.builder()
                        .itemId(rightItem.itemId)
                        .lineType(rightItem.lineType)
                        .type(DiscrepancyType.ITEM_EXTRA)
                        .siocsQuantity(rightItem.quantity)
                        .siocsAmount(rightItem.amount)
                        .siocsUom(rightItem.uom)
                        .withinTolerance(false)
                        .severityBand("HIGH")
                        .description(rightItem.lineType + " item " + rightItem.itemId +
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
                    .add(item.getQuantity(), item.getExtendedAmount(), item.getUnitOfMeasure());
        }

        return aggregates;
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String severityBand(BigDecimal percent) {
        if (percent == null) {
            return "MEDIUM";
        }
        if (percent.compareTo(new BigDecimal("10")) >= 0) {
            return "HIGH";
        }
        if (percent.compareTo(new BigDecimal("3")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static String safeAmount(BigDecimal value) {
        return value == null ? "n/a" : value.stripTrailingZeros().toPlainString();
    }

    private static final class AggregatedItem {
        private final String lineType;
        private final String itemId;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal amount = null;
        private String uom = "";

        private AggregatedItem(String lineType, String itemId) {
            this.lineType = lineType;
            this.itemId = itemId;
        }

        private void add(BigDecimal quantityValue, BigDecimal amountValue, String unitOfMeasure) {
            quantity = quantity.add(quantityValue);
            if (amountValue != null) {
                amount = amount == null ? amountValue : amount.add(amountValue);
            }
            if (uom.isEmpty() && unitOfMeasure != null) {
                uom = unitOfMeasure.trim().toUpperCase();
            }
        }
    }
}
