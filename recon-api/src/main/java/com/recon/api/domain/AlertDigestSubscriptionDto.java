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
public class AlertDigestSubscriptionDto {
    private UUID id;
    private String digestName;
    private String reconView;
    private String scopeType;
    private String scopeKey;
    private String severityThreshold;
    private String recipientType;
    private String recipientKey;
    private boolean active;
    private String description;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
