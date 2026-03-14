package com.recon.publisher.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "publisher")
public class PublisherConfig {
    private long orgId;
    private int pollIntervalMs = 30000;
    private int batchSize = 100;
    private int maxRetries = 5;
    private int processingLockTimeoutMinutes = 15;
    private boolean schedulerEnabled = true;

    // Multi-tenant fields
    private String tenantId = "tenant-india";
    private String tenantTimezone = "UTC";
}
