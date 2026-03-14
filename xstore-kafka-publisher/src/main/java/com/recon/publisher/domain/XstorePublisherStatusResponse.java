package com.recon.publisher.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XstorePublisherStatusResponse {
    private String service;
    private boolean schedulerEnabled;
    private int batchSize;
    private int maxRetries;
    private int processingLockTimeoutMinutes;
    private Map<String, Long> statusCounts;
    private String oldestPendingAt;
    private String oldestFailedAt;
    private String oldestProcessingAt;
}
