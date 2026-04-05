package com.recon.flink.util;

import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.FlatSimTransaction;
import com.recon.integration.recon.TransactionFamilyConfig;
import com.recon.integration.recon.TransactionFamilyRegistry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class InventoryBusinessContextFactory {

    private InventoryBusinessContextFactory() {
    }

    public static InventoryBusinessContext from(FlatSimTransaction source,
                                                FlatSimTransaction target) {
        FlatSimTransaction primary = source != null ? source : target;
        if (primary == null) {
            return new InventoryBusinessContext(
                    null,
                    "UNKNOWN",
                    "UNKNOWN",
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false
            );
        }

        TransactionFamilyConfig familyConfig = resolveFamily(primary);
        String tenantId = normalizeForKey(primary.getTenantId(), "UNKNOWN_TENANT");
        String transactionFamily = familyConfig.transactionFamily().name();
        String businessReference = resolveBusinessReference(primary);
        String normalizedBusinessReference = normalizeForKey(businessReference, "UNKNOWN_REFERENCE");
        String normalizedStoreId = normalizeForKey(primary.getStoreId(), "UNKNOWN_STORE");
        String normalizedBusinessDate = normalizeForKey(primary.getBusinessDate(), "UNKNOWN_DATE");
        String headerMatchKey = String.join("|",
                tenantId,
                transactionFamily,
                normalizedBusinessReference,
                normalizedStoreId
        );
        String aggregateKey = String.join("|",
                tenantId,
                normalizedBusinessDate,
                normalizedStoreId,
                transactionFamily
        );

        BigDecimal sourceQuantity = totalQuantity(source);
        BigDecimal targetQuantity = totalQuantity(target);

        return new InventoryBusinessContext(
                primary.getTenantId(),
                transactionFamily,
                familyConfig.transactionPhase().name(),
                businessReference,
                headerMatchKey,
                aggregateKey,
                source != null ? source.getExternalId() : null,
                target != null ? target.getExternalId() : null,
                lineMatchKeys(source, headerMatchKey),
                lineMatchKeys(target, headerMatchKey),
                lineItemCount(source),
                lineItemCount(target),
                sourceQuantity,
                targetQuantity,
                variance(sourceQuantity, targetQuantity),
                familyConfig.quantityMetricsAvailable(),
                familyConfig.valueMetricsAvailable()
        );
    }

    public static TransactionFamilyConfig resolveFamily(FlatSimTransaction transaction) {
        if (transaction == null) {
            return TransactionFamilyConfig.unknown(null, "UNKNOWN");
        }
        return TransactionFamilyRegistry.resolveInventoryFamily(
                transaction.getTransactionType(),
                transaction.getTransactionTypeDesc()
        );
    }

    private static String resolveBusinessReference(FlatSimTransaction transaction) {
        if (transaction == null) {
            return null;
        }
        String externalId = Objects.toString(transaction.getExternalId(), "").trim();
        if (!externalId.isEmpty()) {
            return externalId;
        }
        String transactionKey = Objects.toString(transaction.getTransactionKey(), "").trim();
        return transactionKey.isEmpty() ? null : transactionKey;
    }

    private static List<String> lineMatchKeys(FlatSimTransaction transaction,
                                              String headerMatchKey) {
        if (transaction == null || headerMatchKey == null || transaction.getLineItems() == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (FlatLineItem item : transaction.getLineItems()) {
            if (item == null || item.getItemId() == null || item.getItemId().isBlank()) {
                continue;
            }
            keys.add(headerMatchKey + "|" + normalizeForKey(item.getItemId(), "UNKNOWN_ITEM"));
        }
        return keys;
    }

    private static Integer lineItemCount(FlatSimTransaction transaction) {
        if (transaction == null) {
            return null;
        }
        return transaction.getLineItems() != null
                ? transaction.getLineItems().length
                : transaction.getLineItemCount();
    }

    private static BigDecimal totalQuantity(FlatSimTransaction transaction) {
        return transaction == null ? null : transaction.getTotalQuantity();
    }

    private static BigDecimal variance(BigDecimal left,
                                       BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.subtract(right).abs();
    }

    private static String normalizeForKey(String value,
                                          String fallback) {
        String normalized = Objects.toString(value, "").trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        return normalized
                .replace('|', '/')
                .replaceAll("\\s+", "_")
                .toUpperCase(Locale.ROOT);
    }
}
