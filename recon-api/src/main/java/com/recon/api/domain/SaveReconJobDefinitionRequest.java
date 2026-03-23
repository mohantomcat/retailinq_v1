package com.recon.api.domain;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SaveReconJobDefinitionRequest {
    private UUID id;
    private String jobName;
    private String reconView;
    private String cronExpression;
    private String jobTimezone;
    private String windowType;
    private String endOfDayLocalTime;
    private Integer businessDateOffsetDays;
    private Integer maxRetryAttempts;
    private Integer retryDelayMinutes;
    private Boolean allowConcurrentRuns;
    private Boolean enabled;
    private Boolean notifyOnSuccess;
    private Boolean notifyOnFailure;
    private String notificationChannelType;
    private String notificationEndpoint;
    private String notificationEmail;
    private List<String> scopeStoreIds;
    private List<SaveReconJobStepRequest> steps;
}
