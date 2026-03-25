package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationReplayRequestDto {
    private UUID id;
    private String connectorKey;
    private String flowKey;
    private String businessKey;
    private String documentId;
    private String requestedBy;
    private String requestedAt;
    private String requestedFrom;
    private String requestedTo;
    private String replayStatus;
    private String reason;
    private String processedAt;
    private String processedBy;
    private String resolutionMessage;
}
