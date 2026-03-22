package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RootCauseBreakdownDto {
    private String key;
    private String label;
    private long count;
    private double percent;
    private long activeCases;
    private long breachedCases;
    private long last7DaysCount;
    private long previous7DaysCount;
    private double cumulativePercent;
}
