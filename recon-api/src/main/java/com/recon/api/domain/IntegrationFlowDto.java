package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationFlowDto {
    private String flowKey;
    private String flowLabel;
    private String messageType;
    private String sourceSystem;
    private String targetSystem;
    private String businessObject;
    private boolean enabled;
    private long messagesLast24Hours;
    private long errorsLast24Hours;
    private String latestRunStatus;
}
