package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OperationsJobCenterResponse {
    long enabledJobs;
    long failedRunsLast24Hours;
    long successfulRunsLast24Hours;
    long pendingRetries;
    List<ReconJobActionCatalogDto> actionCatalog;
    List<ReconJobTemplateDto> templates;
    List<ReconJobDefinitionDto> jobDefinitions;
    List<ReconJobRunDto> recentRuns;
}
