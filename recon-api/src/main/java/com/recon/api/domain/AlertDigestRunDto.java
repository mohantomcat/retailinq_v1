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
public class AlertDigestRunDto {
    private UUID id;
    private UUID subscriptionId;
    private String reconView;
    private String scopeType;
    private String scopeKey;
    private String recipientSummary;
    private String runStatus;
    private Integer itemCount;
    private String digestSubject;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
}
