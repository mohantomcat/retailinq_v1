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
public class IntegrationErrorQueueItemDto {
    private UUID id;
    private UUID integrationMessageId;
    private String connectorKey;
    private String flowKey;
    private String businessKey;
    private String documentId;
    private String errorType;
    private String errorCode;
    private String errorMessage;
    private boolean retryable;
    private String errorStatus;
    private String createdAt;
    private String resolvedAt;
    private String resolutionNotes;
}
