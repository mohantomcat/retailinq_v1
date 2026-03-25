package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveIntegrationReplayRequest {
    private String connectorKey;
    private String flowKey;
    private String businessKey;
    private String documentId;
    private String requestedFrom;
    private String requestedTo;
    private String reason;
}
