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
public class RootCauseAnalyticsResponse {
    private RootCauseSummaryDto summary;
    private List<RootCauseBreakdownDto> topReasons;
    private List<RootCauseBreakdownDto> topCategories;
    private List<RootCauseBreakdownDto> topStores;
    private List<RootCauseBreakdownDto> topRegisters;
    private List<RootCauseBreakdownDto> topModules;
    private List<RootCauseBreakdownDto> topSeverities;
    private List<RootCauseBreakdownDto> paretoReasons;
}
