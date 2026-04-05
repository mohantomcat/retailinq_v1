package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationFlowDto {
    private String connectorKey;
    private String flowKey;
    private String flowLabel;
    private String messageType;
    private String sourceSystem;
    private String targetSystem;
    private String businessObject;
    private String endpointMode;
    private boolean sharedAsset;
    private java.util.List<String> affectedReconViews;
    private java.util.List<String> affectedReconLabels;
    private boolean enabled;
    private long messagesLast24Hours;
    private long errorsLast24Hours;
    private String latestRunStatus;
    private String latestRunStartedAt;
    private String latestRunCompletedAt;
    private String lastPublishedAt;
}
