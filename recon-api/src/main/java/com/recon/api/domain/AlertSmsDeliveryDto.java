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
public class AlertSmsDeliveryDto {
    private UUID id;
    private UUID eventId;
    private UUID subscriptionId;
    private String reconView;
    private String phoneNumber;
    private String providerName;
    private String deliveryStatus;
    private Integer responseStatusCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime deliveredAt;
}
