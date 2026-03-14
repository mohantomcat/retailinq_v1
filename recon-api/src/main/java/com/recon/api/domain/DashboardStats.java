package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private long processingPending;
    private long processingFailed;
    private long duplicateInSiocs;
    private long awaitingSim;
    private double matchRate;
    private String asOf;
    private Map<String, Long> byStore;
    private Map<String, Long> byStatus;
}