package com.recon.flink.util;

import java.math.BigDecimal;
import java.util.List;

public record InventoryBusinessContext(
        String tenantId,
        String transactionFamily,
        String transactionPhase,
        String businessReference,
        String headerMatchKey,
        String aggregateKey,
        String sourceDocumentRef,
        String targetDocumentRef,
        List<String> sourceLineMatchKeys,
        List<String> targetLineMatchKeys,
        Integer sourceItemCount,
        Integer targetItemCount,
        BigDecimal sourceTotalQuantity,
        BigDecimal targetTotalQuantity,
        BigDecimal quantityVariance,
        boolean quantityMetricsAvailable,
        boolean valueMetricsAvailable
) {
}
