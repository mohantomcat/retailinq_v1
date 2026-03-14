package com.recon.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "cloud.connector")
public class CloudConnectorProperties {
    private String connectorName = "cloud-rest-main";
    private String sourceName = "CLOUD_SIM";
    private String tenantId = "tenant-india";
    private String tenantTimezone = "UTC";
    private long orgId = 1L;
    private int downloadIntervalMs = 300000;
    private int publishIntervalMs = 30000;
    private int batchSize = 500;
    private int publisherBatchSize = 200;
    private int overlapMinutes = 10;
    private int processingLockTimeoutMinutes = 15;
    private int maxRetries = 5;
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 30000;
    private int metricsRefreshIntervalMs = 60000;
    private int retryMaxAttempts = 3;
    private long retryInitialIntervalMs = 1000;
    private double retryMultiplier = 2.0d;
    private long retryMaxIntervalMs = 10000;
    private String authType = "NONE";
    private String authHeaderName = "X-API-Key";
    private String bearerToken = "";
    private String basicUsername = "";
    private String basicPassword = "";
    private String idcsTokenUrl = "https://replace-idcs.example.com/oauth2/v1/token";
    private String idcsClientId = "";
    private String idcsClientSecret = "";
    private String idcsScope = "";
    private String idcsGrantType = "client_credentials";
    private long idcsRefreshSkewSeconds = 60;
    private String baseUrl = "http://localhost:8081";
    private String transactionsPath = "/api/transactions";
    private String apiKey = "";
    private boolean enabled = false;
    private boolean schedulerEnabled = true;
    private Map<String, String> additionalHeaders = new LinkedHashMap<>();
}
