package com.recon.poller.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "siocs.poller")
public class PollerConfig {
    private long orgId;
    private int pollIntervalMs = 300000;
    private int safetyMarginMin = 10;
    private int pageSize = 500;
    private int maxRetries = 5;
    private int leaseTimeoutSeconds = 900;
    private boolean schedulerEnabled = true;

    // Multi-tenant fields
    private String tenantId = "tenant-india";
    private String tenantTimezone = "UTC";
}
