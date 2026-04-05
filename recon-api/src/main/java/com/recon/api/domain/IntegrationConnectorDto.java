package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationConnectorDto {
    private String connectorKey;
    private String connectorLabel;
    private String connectorType;
    private String sourceSystem;
    private String targetSystem;
    private String moduleKey;
    private String endpointMode;
    private boolean sharedAsset;
    private java.util.List<String> affectedReconViews;
    private java.util.List<String> affectedReconLabels;
    private boolean enabled;
    private String latestRunStatus;
    private String latestRunStartedAt;
    private String latestRunCompletedAt;
    private String lastPublishedAt;
    private long openErrorCount;
    private long publishedMessagesLast24Hours;
    private long failedMessagesLast24Hours;
}
