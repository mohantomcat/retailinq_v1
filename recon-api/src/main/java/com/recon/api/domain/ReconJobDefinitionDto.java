package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ReconJobDefinitionDto {
    UUID id;
    String jobName;
    String reconView;
    String cronExpression;
    String jobTimezone;
    String windowType;
    String endOfDayLocalTime;
    Integer businessDateOffsetDays;
    Integer maxRetryAttempts;
    Integer retryDelayMinutes;
    boolean allowConcurrentRuns;
    boolean enabled;
    boolean notifyOnSuccess;
    boolean notifyOnFailure;
    String notificationChannelType;
    String notificationEndpoint;
    String notificationEmail;
    List<String> scopeStoreIds;
    String createdBy;
    String updatedBy;
    String createdAt;
    String updatedAt;
    String lastScheduledAt;
    String nextScheduledAt;
    String lastRunStartedAt;
    String lastRunCompletedAt;
    String lastRunStatus;
    String lastRunMessage;
    List<ReconJobStepDto> steps;
}
