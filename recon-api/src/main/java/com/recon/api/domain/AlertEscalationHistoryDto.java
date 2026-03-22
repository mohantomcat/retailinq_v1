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
public class AlertEscalationHistoryDto {
    private UUID id;
    private UUID eventId;
    private UUID policyId;
    private String reconView;
    private String ruleName;
    private String severity;
    private String destinationType;
    private String destinationKey;
    private String escalationStatus;
    private String errorMessage;
    private LocalDateTime escalatedAt;
}
