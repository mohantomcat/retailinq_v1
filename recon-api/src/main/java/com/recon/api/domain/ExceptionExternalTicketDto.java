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
public class ExceptionExternalTicketDto {
    private UUID id;
    private String channelName;
    private String channelType;
    private String ticketSummary;
    private String ticketDescription;
    private String externalReference;
    private String externalUrl;
    private String deliveryStatus;
    private String externalStatus;
    private Integer responseStatusCode;
    private String errorMessage;
    private String createdBy;
    private String createdAt;
    private String lastSyncedAt;
    private String lastExternalUpdateAt;
    private String lastExternalUpdatedBy;
    private String lastExternalComment;
}
