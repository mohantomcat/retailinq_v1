package com.recon.api.service;

import com.recon.api.domain.ConfigurationCatalogResponse;
import com.recon.api.domain.ConfigurationEntryDto;
import com.recon.api.domain.ConfigurationSectionDto;
import com.recon.api.domain.ModuleConfigurationDto;
import com.recon.api.repository.ConfigurationOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigurationCatalogService {

    private static final String APPLY_MODE_RESTART_REQUIRED = "RESTART_REQUIRED";
    private static final String APPLY_MODE_REFERENCE_ONLY = "REFERENCE_ONLY";
    private static final String APPLY_MODE_LIVE_APPLIED = "LIVE_APPLIED";

    private final ConfigurationOverrideRepository configurationOverrideRepository;
    private final AuditLedgerService auditLedgerService;

    public ConfigurationCatalogResponse getCatalog(boolean includeModules,
                                                   boolean includeSystemSections,
                                                   boolean allowEdit) {
        Map<String, String> overrides = configurationOverrideRepository.findAllOverrides();
        List<ModuleConfigurationDto> modules = includeModules
                ? List.of(
                buildXstoreSim(overrides),
                buildXstoreSiocs(overrides),
                buildXstoreXocs(overrides)
        ).stream().map(module -> maskEditable(module, allowEdit)).collect(Collectors.toList())
                : List.of();
        List<ConfigurationSectionDto> systemSections = includeSystemSections
                ? buildSystemSections(overrides).stream()
                .map(section -> maskEditable(section, allowEdit))
                .collect(Collectors.toList())
                : List.of();
        return ConfigurationCatalogResponse.builder()
                .modules(modules)
                .systemSections(systemSections)
                .build();
    }

    private ModuleConfigurationDto maskEditable(ModuleConfigurationDto module, boolean allowEdit) {
        return ModuleConfigurationDto.builder()
                .moduleId(module.getModuleId())
                .moduleLabel(module.getModuleLabel())
                .sections(module.getSections().stream()
                        .map(section -> maskEditable(section, allowEdit))
                        .collect(Collectors.toList()))
                .build();
    }

    private ConfigurationSectionDto maskEditable(ConfigurationSectionDto section, boolean allowEdit) {
        return ConfigurationSectionDto.builder()
                .id(section.getId())
                .label(section.getLabel())
                .description(section.getDescription())
                .entries(section.getEntries().stream()
                        .map(entry -> maskEditable(entry, allowEdit))
                        .collect(Collectors.toList()))
                .build();
    }

    private ConfigurationEntryDto maskEditable(ConfigurationEntryDto entry, boolean allowEdit) {
        return ConfigurationEntryDto.builder()
                .key(entry.getKey())
                .label(entry.getLabel())
                .description(entry.getDescription())
                .envVar(entry.getEnvVar())
                .defaultValue(entry.getDefaultValue())
                .effectiveValue(entry.getEffectiveValue())
                .overrideValue(entry.getOverrideValue())
                .sensitive(entry.isSensitive())
                .editable(allowEdit && entry.isEditable())
                .applyMode(entry.getApplyMode())
                .build();
    }

    @Transactional
    public void saveOverride(String configKey, String value, String changedBy) {
        ConfigDefinition definition = findDefinition(configKey);
        if (!definition.editable() || definition.sensitive()) {
            throw new IllegalArgumentException("Configuration is not editable from the UI: " + configKey);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Override value is required");
        }
        String previousValue = configurationOverrideRepository.findOverride(configKey).orElse(null);
        configurationOverrideRepository.upsertOverride(configKey, value.trim(), changedBy);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(resolveTenantId())
                .sourceType("CONFIGURATION")
                .moduleKey("CONFIGURATIONS")
                .entityType("CONFIGURATION_OVERRIDE")
                .entityKey(configKey)
                .actionType("UPSERT")
                .title("Configuration override updated")
                .summary(configKey)
                .actor(changedBy)
                .status("UPSERT")
                .referenceKey(configKey)
                .controlFamily("ITGC")
                .evidenceTags(List.of("CONFIG", "CHANGE"))
                .beforeState(previousValue)
                .afterState(value.trim())
                .build());
    }

    @Transactional
    public void clearOverride(String configKey, String changedBy) {
        ConfigDefinition definition = findDefinition(configKey);
        if (!definition.editable() || definition.sensitive()) {
            throw new IllegalArgumentException("Configuration is not editable from the UI: " + configKey);
        }
        String previousValue = configurationOverrideRepository.findOverride(configKey).orElse(null);
        configurationOverrideRepository.deleteOverride(configKey, changedBy);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(resolveTenantId())
                .sourceType("CONFIGURATION")
                .moduleKey("CONFIGURATIONS")
                .entityType("CONFIGURATION_OVERRIDE")
                .entityKey(configKey)
                .actionType("DELETE")
                .title("Configuration override cleared")
                .summary(configKey)
                .actor(changedBy)
                .status("DELETE")
                .referenceKey(configKey)
                .controlFamily("ITGC")
                .evidenceTags(List.of("CONFIG", "CHANGE"))
                .beforeState(previousValue)
                .afterState(null)
                .build());
    }

    private String resolveTenantId() {
        return System.getenv("TENANT_ID") != null && !System.getenv("TENANT_ID").isBlank()
                ? System.getenv("TENANT_ID").trim()
                : "tenant-india";
    }

    private ModuleConfigurationDto buildXstoreSim(Map<String, String> overrides) {
        return ModuleConfigurationDto.builder()
                .moduleId("xstore-sim")
                .moduleLabel("Xstore vs SIM")
                .sections(List.of(
                        xstorePublisherSection(overrides),
                        siocsDbPollerSection(overrides)
                ))
                .build();
    }

    private ModuleConfigurationDto buildXstoreSiocs(Map<String, String> overrides) {
        return ModuleConfigurationDto.builder()
                .moduleId("xstore-siocs")
                .moduleLabel("Xstore vs SIOCS")
                .sections(List.of(
                        xstorePublisherSection(overrides),
                        siocsCloudSection(overrides)
                ))
                .build();
    }

    private ModuleConfigurationDto buildXstoreXocs(Map<String, String> overrides) {
        return ModuleConfigurationDto.builder()
                .moduleId("xstore-xocs")
                .moduleLabel("Xstore vs XOCS")
                .sections(List.of(
                        xstorePublisherSection(overrides),
                        xocsCloudSection(overrides)
                ))
                .build();
    }

    private List<ConfigurationSectionDto> buildSystemSections(Map<String, String> overrides) {
        return List.of(
                tenantSection(overrides),
                infrastructureSection(overrides),
                notificationsSection(overrides),
                adminSecuritySection(overrides)
        );
    }

    private ConfigurationSectionDto tenantSection(Map<String, String> overrides) {
        return ConfigurationSectionDto.builder()
                .id("tenant")
                .label("Tenant Defaults")
                .description("Cross-module tenant and organization values.")
                .entries(List.of(
                        entry(overrides, "tenantId", "Tenant ID",
                                "Tenant identifier used across connectors and API.",
                                "TENANT_ID", "tenant-india", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "tenantTimezone", "Tenant Timezone",
                                "Business timezone used for tenant-facing dates.",
                                "TENANT_TIMEZONE", "Asia/Kolkata", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "orgId", "Organization ID",
                                "Organization scope used by source connectors.",
                                "ORG_ID", "1", false, true, APPLY_MODE_RESTART_REQUIRED)
                ))
                .build();
    }

    private ConfigurationSectionDto infrastructureSection(Map<String, String> overrides) {
        return ConfigurationSectionDto.builder()
                .id("infrastructure")
                .label("Infrastructure")
                .description("Shared infrastructure and platform access values.")
                .entries(List.of(
                        entry(overrides, "pgHost", "PostgreSQL Host",
                                "Local reconciliation and staging database host.",
                                "PG_HOST", "localhost", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "pgDatabase", "PostgreSQL Database",
                                "Local reconciliation and staging database name.",
                                "PG_DB", "recondb", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "pgUser", "PostgreSQL User",
                                "Local reconciliation and staging database user.",
                                "PG_USER", "recon", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "pgPassword", "PostgreSQL Password",
                                "Local reconciliation and staging database password.",
                                "PG_PASSWORD", "", true, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "kafkaBrokers", "Kafka Brokers",
                                "Kafka bootstrap brokers shared by connectors and recon engine.",
                                "KAFKA_BROKERS", "localhost:9092", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "elasticHost", "Elasticsearch Host",
                                "Elasticsearch host used by the API.",
                                "ES_HOST", "localhost", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "elasticPort", "Elasticsearch Port",
                                "Elasticsearch port used by the API.",
                                "ES_PORT", "9200", false, false, APPLY_MODE_REFERENCE_ONLY)
                ))
                .build();
    }

    private ConfigurationSectionDto adminSecuritySection(Map<String, String> overrides) {
        return ConfigurationSectionDto.builder()
                .id("admin-security")
                .label("Administrative Security")
                .description("Credentials used to protect connector admin endpoints.")
                .entries(List.of(
                        entry(overrides, "connectorAdminUser", "Admin Username",
                                "Connector admin username for protected operational endpoints.",
                                "CONNECTOR_ADMIN_USER", "connector-admin", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "connectorAdminPassword", "Admin Password",
                                "Connector admin password for protected operational endpoints.",
                                "CONNECTOR_ADMIN_PASSWORD", "", true, false, APPLY_MODE_REFERENCE_ONLY)
                ))
                .build();
    }

    private ConfigurationSectionDto notificationsSection(Map<String, String> overrides) {
        return ConfigurationSectionDto.builder()
                .id("notifications")
                .label("Notifications")
                .description("Alert email and webhook delivery settings, including SMTP and outbound webhook controls.")
                .entries(List.of(
                        entry(overrides, "alertEmailEnabled", "Alert Email Enabled",
                                "Controls whether alert events can send email notifications.",
                                "APP_ALERTING_EMAIL_ENABLED", "false", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertEmailFrom", "Alert From Address",
                                "From email address used for alert notifications.",
                                "APP_ALERTING_EMAIL_FROM", "no-reply@retailinq.local", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertEmailFromName", "Alert From Name",
                                "Display name used in alert notification emails.",
                                "APP_ALERTING_EMAIL_FROM_NAME", "RetailINQ Alerts", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertEmailAppBaseUrl", "Alert App Base URL",
                                "RetailINQ application URL included in alert emails.",
                                "APP_ALERTING_EMAIL_APP_BASE_URL", "http://localhost:5173", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertWebhookEnabled", "Alert Webhook Enabled",
                                "Controls whether alert events can send webhook notifications.",
                                "APP_ALERTING_WEBHOOK_ENABLED", "false", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertWebhookConnectTimeout", "Webhook Connect Timeout (ms)",
                                "HTTP connect timeout for outbound alert webhooks.",
                                "APP_ALERTING_WEBHOOK_CONNECT_TIMEOUT_MS", "5000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertWebhookReadTimeout", "Webhook Read Timeout (ms)",
                                "HTTP read timeout for outbound alert webhooks.",
                                "APP_ALERTING_WEBHOOK_READ_TIMEOUT_MS", "10000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertWebhookAppBaseUrl", "Webhook App Base URL",
                                "RetailINQ application URL included in webhook payloads.",
                                "APP_ALERTING_WEBHOOK_APP_BASE_URL", "http://localhost:5173", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertEscalationEnabled", "Alert Escalation Enabled",
                                "Controls whether unresolved alerts are escalated automatically.",
                                "APP_ALERTING_ESCALATION_ENABLED", "true", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "alertEscalationInterval", "Alert Escalation Interval (ms)",
                                "How often unresolved alert escalation rules are evaluated.",
                                "APP_ALERTING_ESCALATION_INTERVAL_MS", "300000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "mailHost", "SMTP Host",
                                "SMTP server host for outbound alert emails.",
                                "SPRING_MAIL_HOST", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "mailPort", "SMTP Port",
                                "SMTP server port for outbound alert emails.",
                                "SPRING_MAIL_PORT", "587", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "mailUsername", "SMTP Username",
                                "SMTP username for outbound alert emails.",
                                "SPRING_MAIL_USERNAME", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "mailPassword", "SMTP Password",
                                "SMTP password for outbound alert emails.",
                                "SPRING_MAIL_PASSWORD", "", true, false, APPLY_MODE_REFERENCE_ONLY)
                ))
                .build();
    }

    private ConfigurationSectionDto xstorePublisherSection(Map<String, String> overrides) {
        return ConfigurationSectionDto.builder()
                .id("xstore-publisher")
                .label("Xstore Publisher")
                .description("On-prem Xstore database polling and publishing settings.")
                .entries(List.of(
                        entry(overrides, "dbHost", "Database Host",
                                "Xstore Oracle host or service endpoint.",
                                "DB_HOST", "localhost", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "dbSid", "Database SID",
                                "Xstore Oracle SID.",
                                "DB_SID", "JCR", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "dbUser", "Database User",
                                "Xstore Oracle user.",
                                "DB_USER", "recon", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "dbPassword", "Database Password",
                                "Xstore Oracle password.",
                                "DB_PASSWORD", "", true, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "storeId", "Store ID",
                                "Store whose Xstore transactions are published by this connector instance.",
                                "STORE_ID", "1007", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "pollIntervalMs", "Poll Interval (ms)",
                                "How often Xstore is polled for new transactions.",
                                "PUBLISHER_POLL_INTERVAL_MS", "30000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "batchSize", "Batch Size",
                                "Number of transactions claimed per publish batch.",
                                "PUBLISHER_BATCH_SIZE", "100", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "maxRetries", "Max Retries",
                                "Max retries before rows stop normal reprocessing.",
                                "PUBLISHER_MAX_RETRIES", "5", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "lookbackHours", "Lookback Hours",
                                "Initial historical window for Xstore polling.",
                                "PUBLISHER_LOOKBACK_HOURS", "96", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "lockTimeoutMinutes", "Processing Lock Timeout (min)",
                                "Timeout before stale Xstore publish claims are released.",
                                "PUBLISHER_PROCESSING_LOCK_TIMEOUT_MINUTES", "15", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "schedulerEnabled", "Scheduler Enabled",
                                "Controls scheduled publish execution.",
                                "PUBLISHER_SCHEDULER_ENABLED", "true", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "posTopic", "POS Topic",
                                "Kafka topic for Xstore transaction events.",
                                "KAFKA_POS_TOPIC", "pos.transactions.raw", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "dlqTopic", "DLQ Topic",
                                "Kafka dead-letter topic for connector failures.",
                                "KAFKA_DLQ_TOPIC", "recon.dlq", false, true, APPLY_MODE_RESTART_REQUIRED)
                ))
                .build();
    }

    private ConfigurationSectionDto siocsDbPollerSection(Map<String, String> overrides) {
        return ConfigurationSectionDto.builder()
                .id("siocs-db-poller")
                .label("SIM / SIOCS DB Poller")
                .description("Database poller settings for the existing SIM lane.")
                .entries(List.of(
                        entry(overrides, "siocsDbHost", "Database Host",
                                "SIOCS Oracle host or service endpoint.",
                                "SIOCS_DB_HOST", "localhost", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "siocsDbSid", "Database SID",
                                "SIOCS Oracle SID.",
                                "SIOCS_DB_SID", "JCR", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "siocsDbUser", "Database User",
                                "SIOCS Oracle user.",
                                "SIOCS_DB_USER", "siocs", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "siocsDbPassword", "Database Password",
                                "SIOCS Oracle password.",
                                "SIOCS_DB_PASSWORD", "", true, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "pollIntervalMs", "Poll Interval (ms)",
                                "How often SIM/SIOCS DB is polled for new records.",
                                "SIOCS_POLLER_INTERVAL_MS", "300000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "safetyMarginMin", "Safety Margin (min)",
                                "Overlap safety margin for incremental DB polling.",
                                "SIOCS_POLLER_SAFETY_MARGIN_MIN", "10", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "pageSize", "Page Size",
                                "Rows fetched per database poll page.",
                                "SIOCS_POLLER_PAGE_SIZE", "500", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "maxRetries", "Max Retries",
                                "Max retries before poller moves repeated failures aside.",
                                "SIOCS_POLLER_MAX_RETRIES", "5", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "leaseTimeoutSeconds", "Lease Timeout (sec)",
                                "Single-worker DB lease timeout for the poller.",
                                "SIOCS_POLLER_LEASE_TIMEOUT_SECONDS", "900", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "schedulerEnabled", "Scheduler Enabled",
                                "Controls scheduled DB polling.",
                                "SIOCS_POLLER_SCHEDULER_ENABLED", "true", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "simTopic", "SIM Topic",
                                "Kafka topic for SIM transaction events.",
                                "KAFKA_SIM_TOPIC", "sim.transactions.raw", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "dlqTopic", "DLQ Topic",
                                "Kafka dead-letter topic for connector failures.",
                                "KAFKA_DLQ_TOPIC", "recon.dlq", false, true, APPLY_MODE_RESTART_REQUIRED)
                ))
                .build();
    }

    private ConfigurationSectionDto siocsCloudSection(Map<String, String> overrides) {
        return ConfigurationSectionDto.builder()
                .id("siocs-cloud")
                .label("SIOCS Cloud Connector")
                .description("Cloud API pull, staging, publish, and retention settings for SIOCS.")
                .entries(List.of(
                        entry(overrides, "enabled", "Connector Enabled",
                                "Turns the SIOCS cloud connector on or off.",
                                "CLOUD_CONNECTOR_ENABLED", "false", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "connectorName", "Connector Name",
                                "Logical connector identifier stored in checkpoints and logs.",
                                "CLOUD_CONNECTOR_NAME", "siocs-rest-main", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "sourceName", "Source Name",
                                "Source label stored in staged records.",
                                "CLOUD_SOURCE_NAME", "SIOCS", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "downloadIntervalMs", "Download Interval (ms)",
                                "How often the SIOCS API is polled.",
                                "CLOUD_DOWNLOAD_INTERVAL_MS", "300000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "publishIntervalMs", "Publish Interval (ms)",
                                "How often staged SIOCS rows are published to Kafka.",
                                "CLOUD_PUBLISH_INTERVAL_MS", "30000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "batchSize", "Download Batch Size",
                                "Rows requested per SIOCS API page.",
                                "CLOUD_BATCH_SIZE", "500", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "publisherBatchSize", "Publisher Batch Size",
                                "Rows claimed per Kafka publish batch.",
                                "CLOUD_PUBLISHER_BATCH_SIZE", "200", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "overlapMinutes", "Overlap Minutes",
                                "Incremental polling overlap window.",
                                "CLOUD_OVERLAP_MINUTES", "10", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "lockTimeoutMinutes", "Processing Lock Timeout (min)",
                                "Timeout before stale SIOCS publish claims are released.",
                                "CLOUD_PROCESSING_LOCK_TIMEOUT_MINUTES", "15", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "maxRetries", "Max Retries",
                                "Max retries for staged SIOCS rows.",
                                "CLOUD_MAX_RETRIES", "5", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "connectTimeoutMs", "Connect Timeout (ms)",
                                "HTTP connect timeout for the SIOCS API.",
                                "CLOUD_CONNECT_TIMEOUT_MS", "10000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "readTimeoutMs", "Read Timeout (ms)",
                                "HTTP read timeout for the SIOCS API.",
                                "CLOUD_READ_TIMEOUT_MS", "30000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "schedulerEnabled", "Scheduler Enabled",
                                "Controls scheduled SIOCS API download and publish jobs.",
                                "CLOUD_SCHEDULER_ENABLED", "true", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "normalizedRetentionDays", "Normalized Retention (days)",
                                "Retention for staged normalized SIOCS transactions.",
                                "CLOUD_NORMALIZED_RETENTION_DAYS", "35", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "rawRetentionDays", "Raw Retention (days)",
                                "Retention for raw SIOCS API payloads.",
                                "CLOUD_RAW_RETENTION_DAYS", "10", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "errorRetentionDays", "Error Retention (days)",
                                "Retention for staged SIOCS ingestion errors.",
                                "CLOUD_ERROR_RETENTION_DAYS", "35", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "retentionIntervalMs", "Retention Interval (ms)",
                                "How often SIOCS retention cleanup runs.",
                                "CLOUD_RETENTION_INTERVAL_MS", "86400000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "authType", "Auth Type",
                                "Authentication mode for the SIOCS cloud API.",
                                "CLOUD_AUTH_TYPE", "IDCS", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "baseUrl", "API Base URL",
                                "Base URL for the SIOCS cloud API.",
                                "CLOUD_API_BASE_URL", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "transactionsPath", "Transactions Path",
                                "Relative path for the SIOCS transactions endpoint.",
                                "CLOUD_API_TRANSACTIONS_PATH", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "idcsTokenUrl", "IDCS Token URL",
                                "Token URL for SIOCS IDCS authentication.",
                                "CLOUD_IDCS_TOKEN_URL", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "idcsClientId", "IDCS Client ID",
                                "Client ID for SIOCS IDCS authentication.",
                                "CLOUD_IDCS_CLIENT_ID", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "idcsClientSecret", "IDCS Client Secret",
                                "Client secret for SIOCS IDCS authentication.",
                                "CLOUD_IDCS_CLIENT_SECRET", "", true, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "apiKey", "API Key",
                                "Optional API key for SIOCS API access.",
                                "CLOUD_API_KEY", "", true, false, APPLY_MODE_REFERENCE_ONLY)
                ))
                .build();
    }

    private ConfigurationSectionDto xocsCloudSection(Map<String, String> overrides) {
        return ConfigurationSectionDto.builder()
                .id("xocs-cloud")
                .label("XOCS Cloud Connector")
                .description("Cloud API pull, staging, publish, and retention settings for XOCS.")
                .entries(List.of(
                        entry(overrides, "enabled", "Connector Enabled",
                                "Turns the XOCS cloud connector on or off.",
                                "XOCS_CONNECTOR_ENABLED", "false", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "connectorName", "Connector Name",
                                "Logical connector identifier stored in checkpoints and logs.",
                                "XOCS_CONNECTOR_NAME", "xocs-cloud-main", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "sourceName", "Source Name",
                                "Source label stored in staged records.",
                                "XOCS_SOURCE_NAME", "XOCS", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "downloadIntervalMs", "Download Interval (ms)",
                                "How often the XOCS API is polled.",
                                "XOCS_DOWNLOAD_INTERVAL_MS", "300000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "publishIntervalMs", "Publish Interval (ms)",
                                "How often staged XOCS rows are published to Kafka.",
                                "XOCS_PUBLISH_INTERVAL_MS", "30000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "batchSize", "Download Batch Size",
                                "Rows requested per XOCS API page.",
                                "XOCS_BATCH_SIZE", "500", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "publisherBatchSize", "Publisher Batch Size",
                                "Rows claimed per Kafka publish batch.",
                                "XOCS_PUBLISHER_BATCH_SIZE", "200", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "overlapMinutes", "Overlap Minutes",
                                "Incremental polling overlap window.",
                                "XOCS_OVERLAP_MINUTES", "10", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "lockTimeoutMinutes", "Processing Lock Timeout (min)",
                                "Timeout before stale XOCS publish claims are released.",
                                "XOCS_PROCESSING_LOCK_TIMEOUT_MINUTES", "15", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "maxRetries", "Max Retries",
                                "Max retries for staged XOCS rows.",
                                "XOCS_MAX_RETRIES", "5", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "connectTimeoutMs", "Connect Timeout (ms)",
                                "HTTP connect timeout for the XOCS API.",
                                "XOCS_CONNECT_TIMEOUT_MS", "10000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "readTimeoutMs", "Read Timeout (ms)",
                                "HTTP read timeout for the XOCS API.",
                                "XOCS_READ_TIMEOUT_MS", "30000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "schedulerEnabled", "Scheduler Enabled",
                                "Controls scheduled XOCS API download and publish jobs.",
                                "XOCS_SCHEDULER_ENABLED", "true", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "normalizedRetentionDays", "Normalized Retention (days)",
                                "Retention for staged normalized XOCS transactions.",
                                "XOCS_NORMALIZED_RETENTION_DAYS", "35", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "rawRetentionDays", "Raw Retention (days)",
                                "Retention for raw XOCS API payloads.",
                                "XOCS_RAW_RETENTION_DAYS", "10", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "errorRetentionDays", "Error Retention (days)",
                                "Retention for staged XOCS ingestion errors.",
                                "XOCS_ERROR_RETENTION_DAYS", "35", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "retentionIntervalMs", "Retention Interval (ms)",
                                "How often XOCS retention cleanup runs.",
                                "XOCS_RETENTION_INTERVAL_MS", "86400000", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "pollBusinessDays", "Poll Business Days",
                                "How many recent business dates are scanned in each XOCS poll cycle.",
                                "XOCS_POLL_BUSINESS_DAYS", "2", false, true, APPLY_MODE_RESTART_REQUIRED),
                        entry(overrides, "authType", "Auth Type",
                                "Authentication mode for the XOCS cloud API.",
                                "XOCS_AUTH_TYPE", "IDCS", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "baseUrl", "API Base URL",
                                "Base URL for the XOCS cloud API.",
                                "XOCS_API_BASE_URL", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "transactionsPath", "Transactions Path",
                                "Relative path for the XOCS transactions endpoint.",
                                "XOCS_API_TRANSACTIONS_PATH", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "idcsTokenUrl", "IDCS Token URL",
                                "Token URL for XOCS IDCS authentication.",
                                "XOCS_IDCS_TOKEN_URL", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "idcsClientId", "IDCS Client ID",
                                "Client ID for XOCS IDCS authentication.",
                                "XOCS_IDCS_CLIENT_ID", "", false, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "idcsClientSecret", "IDCS Client Secret",
                                "Client secret for XOCS IDCS authentication.",
                                "XOCS_IDCS_CLIENT_SECRET", "", true, false, APPLY_MODE_REFERENCE_ONLY),
                        entry(overrides, "xocsTopic", "XOCS Topic",
                                "Kafka topic for XOCS transaction events.",
                                "KAFKA_XOCS_TOPIC", "xocs.transactions.raw", false, true, APPLY_MODE_RESTART_REQUIRED)
                ))
                .build();
    }

    private ConfigurationEntryDto entry(Map<String, String> overrides,
                                        String key,
                                        String label,
                                        String description,
                                        String envVar,
                                        String defaultValue,
                                        boolean sensitive,
                                        boolean editable,
                                        String applyMode) {
        String envValue = System.getenv(envVar);
        String overrideValue = overrides.get(key);
        String effectiveValue;
        if (sensitive) {
            effectiveValue = envValue == null || envValue.isBlank() ? "Not set" : "Configured";
        } else {
            effectiveValue = envValue == null || envValue.isBlank() ? defaultValue : envValue;
        }
        return ConfigurationEntryDto.builder()
                .key(envVar)
                .label(label)
                .description(description)
                .envVar(envVar)
                .defaultValue(defaultValue)
                .effectiveValue(effectiveValue)
                .overrideValue(overrides.get(envVar))
                .sensitive(sensitive)
                .editable(editable && !sensitive)
                .applyMode(resolvedApplyMode(envVar, sensitive, editable && !sensitive, applyMode))
                .build();
    }

    private ConfigDefinition findDefinition(String configKey) {
        return getCatalog(true, true, true).getModules().stream()
                .flatMap(module -> module.getSections().stream())
                .flatMap(section -> section.getEntries().stream())
                .filter(entry -> entry.getKey().equals(configKey))
                .findFirst()
                .map(entry -> new ConfigDefinition(entry.getKey(), entry.isSensitive(), entry.isEditable()))
                .or(() -> getCatalog(true, true, true).getSystemSections().stream()
                        .flatMap(section -> section.getEntries().stream())
                        .filter(entry -> entry.getKey().equals(configKey))
                        .findFirst()
                        .map(entry -> new ConfigDefinition(entry.getKey(), entry.isSensitive(), entry.isEditable())))
                .orElseThrow(() -> new IllegalArgumentException("Unknown configuration key: " + configKey));
    }

    private String resolvedApplyMode(String envVar, boolean sensitive, boolean editable, String requestedApplyMode) {
        if (sensitive || !editable || APPLY_MODE_REFERENCE_ONLY.equals(requestedApplyMode)) {
            return requestedApplyMode;
        }
        if (isHotReloadable(envVar)) {
            return APPLY_MODE_LIVE_APPLIED;
        }
        return requestedApplyMode;
    }

    private boolean isHotReloadable(String envVar) {
        return switch (envVar) {
            case "CLOUD_CONNECTOR_ENABLED",
                 "CLOUD_SCHEDULER_ENABLED",
                 "CLOUD_BATCH_SIZE",
                 "CLOUD_PUBLISHER_BATCH_SIZE",
                 "CLOUD_OVERLAP_MINUTES",
                 "CLOUD_PROCESSING_LOCK_TIMEOUT_MINUTES",
                 "CLOUD_MAX_RETRIES",
                 "CLOUD_NORMALIZED_RETENTION_DAYS",
                 "CLOUD_RAW_RETENTION_DAYS",
                 "CLOUD_ERROR_RETENTION_DAYS",
                 "XOCS_CONNECTOR_ENABLED",
                 "XOCS_SCHEDULER_ENABLED",
                 "XOCS_BATCH_SIZE",
                 "XOCS_PUBLISHER_BATCH_SIZE",
                 "XOCS_OVERLAP_MINUTES",
                 "XOCS_PROCESSING_LOCK_TIMEOUT_MINUTES",
                 "XOCS_MAX_RETRIES",
                 "XOCS_NORMALIZED_RETENTION_DAYS",
                 "XOCS_RAW_RETENTION_DAYS",
                 "XOCS_ERROR_RETENTION_DAYS",
                 "XOCS_POLL_BUSINESS_DAYS",
                 "SIOCS_POLLER_SCHEDULER_ENABLED",
                 "SIOCS_POLLER_SAFETY_MARGIN_MIN",
                 "SIOCS_POLLER_PAGE_SIZE",
                 "SIOCS_POLLER_LEASE_TIMEOUT_SECONDS",
                 "PUBLISHER_SCHEDULER_ENABLED",
                 "PUBLISHER_BATCH_SIZE",
                 "PUBLISHER_MAX_RETRIES",
                 "PUBLISHER_PROCESSING_LOCK_TIMEOUT_MINUTES" -> true;
            default -> false;
        };
    }

    private record ConfigDefinition(String key, boolean sensitive, boolean editable) {
    }
}
