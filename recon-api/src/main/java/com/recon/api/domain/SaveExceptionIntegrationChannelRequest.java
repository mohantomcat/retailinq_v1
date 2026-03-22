package com.recon.api.domain;

import lombok.Data;

@Data
public class SaveExceptionIntegrationChannelRequest {
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
    private Boolean active;
    private Boolean inboundSyncEnabled;
    private String inboundSharedSecret;
    private Boolean autoCreateOnCaseOpen;
    private Boolean autoCreateOnEscalation;
}
