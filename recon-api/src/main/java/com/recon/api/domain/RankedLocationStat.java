package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankedLocationStat {
    private String key;
    private long totalTransactions;
    private long exceptionCount;
    private long missing;
    private long duplicates;
    private double matchRate;
}
