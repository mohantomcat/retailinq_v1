package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationConnectorDto {
    private String connectorKey;
    private String connectorLabel;
    private String connectorType;
    private String sourceSystem;
    private String targetSystem;
    private String moduleKey;
    private boolean enabled;
    private String latestRunStatus;
    private String latestRunStartedAt;
    private String latestRunCompletedAt;
    private long openErrorCount;
    private long publishedMessagesLast24Hours;
    private long failedMessagesLast24Hours;
}
