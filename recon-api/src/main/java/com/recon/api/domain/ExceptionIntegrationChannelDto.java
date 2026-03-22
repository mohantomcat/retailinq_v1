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
public class ExceptionIntegrationChannelDto {
    private UUID id;
    private String channelName;
    private String channelType;
    private String channelGroup;
    private String reconView;
    private String endpointUrl;
    private String recipientEmail;
    private String headersJson;
    private String defaultProjectKey;
    private String defaultIssueType;
    private String description;
    private boolean active;
    private boolean inboundSyncEnabled;
    private boolean inboundSharedSecretConfigured;
    private boolean autoCreateOnCaseOpen;
    private boolean autoCreateOnEscalation;
    private String callbackUrl;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
}
