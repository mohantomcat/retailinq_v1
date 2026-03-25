package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationHubSummaryDto {
    private long activeConnectors;
    private long activeFlows;
    private long runningRuns;
    private long failedRunsLast24Hours;
    private long openErrors;
    private long pendingReplayRequests;
    private long publishedMessagesLast24Hours;
}
