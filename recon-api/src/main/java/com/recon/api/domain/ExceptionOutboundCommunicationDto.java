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
public class ExceptionOutboundCommunicationDto {
    private UUID id;
    private String channelName;
    private String channelType;
    private String recipient;
    private String subject;
    private String messageBody;
    private String deliveryStatus;
    private Integer responseStatusCode;
    private String errorMessage;
    private String createdBy;
    private String createdAt;
    private String deliveredAt;
}
