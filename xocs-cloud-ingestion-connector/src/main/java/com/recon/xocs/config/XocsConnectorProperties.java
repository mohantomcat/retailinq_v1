package com.recon.xocs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xocs.connector")
public class XocsConnectorProperties {
    private boolean enabled = false;
    private String connectorName = "xocs-cloud-main";
    private String sourceName = "XOCS";
    private String tenantId = "tenant-india";
    private String tenantTimezone = "UTC";
    private long orgId = 1L;
    private long downloadIntervalMs = 300000L;
    private long publishIntervalMs = 30000L;
    private int batchSize = 500;
    private int publisherBatchSize = 200;
    private int overlapMinutes = 10;
    private int processingLockTimeoutMinutes = 15;
    private int maxRetries = 5;
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 30000;
    private int retryMaxAttempts = 3;
    private long retryInitialIntervalMs = 1000L;
    private double retryMultiplier = 2.0d;
    private long retryMaxIntervalMs = 10000L;
    private String authType = "IDCS";
    private String authHeaderName = "X-API-Key";
    private String apiKey;
    private String bearerToken;
    private String basicUsername;
    private String basicPassword;
    private String idcsTokenUrl;
    private String idcsClientId;
    private String idcsClientSecret;
    private String idcsScope;
    private String idcsGrantType = "client_credentials";
    private long idcsRefreshSkewSeconds = 60L;
    private String baseUrl;
    private String transactionsPath;
    private boolean schedulerEnabled = true;
    private int normalizedRetentionDays = 35;
    private int rawRetentionDays = 10;
    private int errorRetentionDays = 35;
    private long retentionIntervalMs = 86400000L;
    private int pollBusinessDays = 2;
}
