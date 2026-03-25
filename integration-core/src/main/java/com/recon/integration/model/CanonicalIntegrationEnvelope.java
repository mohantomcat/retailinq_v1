package com.recon.integration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanonicalIntegrationEnvelope {

    @Builder.Default
    private int schemaVersion = 1;

    private String messageId;
    private String flowId;
    private String connectorId;
    private String tenantId;
    private String sourceSystem;
    private String targetSystem;
    private String messageType;
    private String businessKey;
    private String documentId;
    private String traceId;
    private String eventTime;
    private String ingestionTime;
    private String payloadRef;
    private Integer retryCount;
    private String status;
    private String schemaKey;
    private String payloadVersion;
    private Map<String, String> attributes;
    private CanonicalTransaction payload;
}
