package com.recon.rms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rms.poller")
public class PollerConfig {
    private long orgId;
    private String pollerId = "rms-main";
    private int pollIntervalMs = 300000;
    private int safetyMarginMin = 10;
    private int pageSize = 500;
    private int maxRetries = 5;
    private int leaseTimeoutSeconds = 900;
    private boolean schedulerEnabled = true;
    private String transactionView = "RMS_TRANSACTION_V";

    // Multi-tenant fields
    private String tenantId = "tenant-india";
    private String tenantTimezone = "UTC";
}

