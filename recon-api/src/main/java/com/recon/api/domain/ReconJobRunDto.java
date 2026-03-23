package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ReconJobRunDto {
    UUID id;
    UUID jobDefinitionId;
    UUID parentRunId;
    UUID rootRunId;
    String jobName;
    String reconView;
    String triggerType;
    String runStatus;
    String initiatedBy;
    Integer attemptNumber;
    Integer maxRetryAttempts;
    Integer retryDelayMinutes;
    boolean retryPending;
    String scheduledFor;
    String startedAt;
    String completedAt;
    String businessDate;
    String windowFromBusinessDate;
    String windowToBusinessDate;
    String summary;
    Object resultPayload;
    String nextRetryAt;
    List<ReconJobStepRunDto> stepRuns;
    List<ReconJobNotificationDeliveryDto> notifications;
}
