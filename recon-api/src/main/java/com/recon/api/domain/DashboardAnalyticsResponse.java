package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAnalyticsResponse {
    private List<TrendPoint> last7Days;
    private List<TrendPoint> last30Days;
    private List<RankedLocationStat> topFailingStores;
    private List<RankedLocationStat> topFailingRegisters;
    private List<ExceptionAgingBucket> exceptionAging;
}
