package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AccessGovernanceNotificationHistoryDto {
    private UUID id;
    private String notificationType;
    private String notificationTier;
    private String channelType;
    private String targetLabel;
    private String notificationStatus;
    private int attemptCount;
    private int maxAttempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private String lastError;
    private List<String> referenceUsers;
}
