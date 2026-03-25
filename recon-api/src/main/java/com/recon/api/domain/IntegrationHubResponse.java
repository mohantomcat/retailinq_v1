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
public class IntegrationHubResponse {
    private IntegrationHubSummaryDto summary;
    private List<IntegrationConnectorDto> connectors;
    private List<IntegrationFlowDto> flows;
    private List<IntegrationRunDto> recentRuns;
    private List<IntegrationErrorQueueItemDto> openErrors;
    private List<IntegrationReplayRequestDto> replayRequests;
}
