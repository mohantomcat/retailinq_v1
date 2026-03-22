package com.recon.api.domain;

import lombok.Data;

import java.util.UUID;

@Data
public class SendExceptionCommunicationRequest {
    private UUID channelId;
    private String incidentKey;
    private String incidentTitle;
    private String storeId;
    private String subject;
    private String messageBody;
}
