package com.recon.poller.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiocsPollerStatusResponse {
    private String service;
    private boolean schedulerEnabled;
    private int pageSize;
    private int safetyMarginMin;
    private int leaseTimeoutSeconds;
    private String pollerId;
    private String tenantId;
    private String lastProcessedTimestamp;
    private String lastProcessedExternalId;
    private Long lastProcessedId;
    private String lastPollStartedAt;
    private String lastPollCompletedAt;
    private String lastPollStatus;
    private String lastErrorMessage;
    private long totalRecordsPolled;
    private String lockOwner;
    private String lockExpiresAt;
}
