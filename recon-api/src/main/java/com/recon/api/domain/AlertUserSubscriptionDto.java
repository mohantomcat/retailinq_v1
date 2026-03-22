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
public class AlertUserSubscriptionDto {
    private UUID id;
    private String username;
    private String reconView;
    private String metricKey;
    private String severityThreshold;
    private String channelType;
    private String endpointUrl;
    private String storeId;
    private String wkstnId;
    private boolean active;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
