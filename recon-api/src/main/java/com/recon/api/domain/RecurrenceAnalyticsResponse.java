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
public class RecurrenceAnalyticsResponse {
    private RecurrenceAnalyticsSummaryDto summary;
    private List<RecurrenceTrendPointDto> trend;
    private List<RecurringIncidentPatternDto> recurringIncidents;
    private List<PreventionOpportunityDto> preventionOpportunities;
    private List<RecurrenceBreakdownDto> topStores;
    private List<RecurrenceBreakdownDto> topModules;
    private List<RecurrenceBreakdownDto> topReasons;
    private List<RecurrenceBreakdownDto> topKnownIssues;
    private List<RecurrenceBreakdownDto> topOwnerTeams;
}
