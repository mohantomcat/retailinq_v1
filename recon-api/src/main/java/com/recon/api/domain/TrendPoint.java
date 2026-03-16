package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendPoint {
    private String businessDate;
    private long totalTransactions;
    private long matched;
    private long missing;
    private long duplicates;
    private long quantityMismatch;
    private long totalMismatch;
    private long itemMissing;
    private double matchRate;
}
