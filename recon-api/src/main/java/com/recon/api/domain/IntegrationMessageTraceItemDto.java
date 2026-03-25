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
public class IntegrationMessageTraceItemDto {
    private UUID id;
    private UUID runId;
    private String connectorKey;
    private String flowKey;
    private String messageId;
    private String traceId;
    private String businessKey;
    private String documentId;
    private String messageType;
    private String sourceSystem;
    private String targetSystem;
    private String messageStatus;
    private Integer retryCount;
    private String payloadRef;
    private String createdAt;
    private String updatedAt;
}
