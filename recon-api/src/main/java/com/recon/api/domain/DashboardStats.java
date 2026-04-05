package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private long totalTransactions;
    private long matched;
    private long missingInSiocs;
    private long itemMissing;
    private long quantityMismatch;
    private long transactionTotalMismatch;
    private long processingPending;
    private long processingFailed;
    private long duplicateInSiocs;
    private long duplicateTransactions;
    private long awaitingSim;
    private double matchRate;
    private String asOf;
    private Map<String, Long> byStore;
    private Map<String, Long> byStatus;
    private Map<String, Long> byTransactionFamily;
    private List<TransactionFamilyVolumeDto> transactionFamilyVolumes;
    private BigDecimal sourceQuantityTotal;
    private BigDecimal targetQuantityTotal;
    private BigDecimal quantityVarianceTotal;
    private long quantityMetricsTransactionCount;
}
