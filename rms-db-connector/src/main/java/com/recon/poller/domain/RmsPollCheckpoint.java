package com.recon.rms.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RmsPollCheckpoint {
    private String pollerId;
    private String tenantId;
    private Timestamp lastProcessedTimestamp;
    private String lastProcessedExternalId;
    private Long lastProcessedId;
    private Timestamp lastPollStartedAt;
    private Timestamp lastPollCompletedAt;
    private String lastPollStatus;
    private long totalRecordsPolled;
    private String lastErrorMessage;
    private String lockOwner;
    private Timestamp lockExpiresAt;
}

