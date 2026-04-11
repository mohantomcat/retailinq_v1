package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccessGovernanceNotificationActionResultDto {
    private String actionType;
    private String notificationTier;
    private int queuedJobs;
    private String targetSummary;
    private String message;
}
