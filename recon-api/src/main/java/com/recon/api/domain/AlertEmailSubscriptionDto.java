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
public class AlertEmailSubscriptionDto {
    private UUID id;
    private String subscriptionName;
    private String reconView;
    private String metricKey;
    private String severityThreshold;
    private String recipientType;
    private String recipientKey;
    private String storeId;
    private String wkstnId;
    private boolean active;
    private String description;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
