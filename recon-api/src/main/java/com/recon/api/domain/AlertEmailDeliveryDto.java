package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEmailDeliveryDto {
    private UUID id;
    private UUID eventId;
    private UUID subscriptionId;
    private String reconView;
    private String recipientEmail;
    private String deliveryStatus;
    private String emailSubject;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime deliveredAt;
}
