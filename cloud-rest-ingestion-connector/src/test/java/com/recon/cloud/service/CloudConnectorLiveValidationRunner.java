package com.recon.cloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.cloud.CloudRestIngestionConnectorApplication;
import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.repository.CloudCheckpointRepository;
import com.recon.cloud.repository.CloudTransactionRepository;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

public class CloudConnectorLiveValidationRunner {

    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("cloud.connector.enabled", "true");
        System.setProperty("cloud.connector.scheduler-enabled", "false");
        System.setProperty("spring.task.scheduling.enabled", "false");
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("spring.flyway.enabled", "false");
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(CloudRestIngestionConnectorApplication.class)
                             .run(args)) {

            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            CloudConnectorProperties properties = context.getBean(CloudConnectorProperties.class);
            CloudCheckpointRepository checkpointRepository =
                    context.getBean(CloudCheckpointRepository.class);
            CloudTransactionRepository transactionRepository =
                    context.getBean(CloudTransactionRepository.class);
            CloudRestDownloadService downloadService =
                    context.getBean(CloudRestDownloadService.class);
            CloudIngestionPublisher publisher =
                    context.getBean(CloudIngestionPublisher.class);
            CloudRestClient restClient =
                    context.getBean(CloudRestClient.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            Instant runStartedAt = Instant.now();
            Timestamp oneYearAgo = Timestamp.from(runStartedAt.minus(365, ChronoUnit.DAYS));
            resetStagingTables(jdbcTemplate);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runStartedAt", runStartedAt.toString());

            Map<String, Long> statusBefore = transactionRepository.countByStatus();
            long rawBefore = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_raw");
            long txnBefore = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_transaction");
            int directFetchCount = restClient.fetchTransactions(0L, oneYearAgo, 200)
                    .getRecords().size();
            checkpointRepository.reset(
                    properties.getConnectorName(),
                    0L,
                    oneYearAgo);

            downloadService.runDownloadCycle();

            Map<String, Long> statusAfterDownload = transactionRepository.countByStatus();
            long rawAfterDownload = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_raw");
            long txnAfterDownload = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_transaction");
            long nullExternalIdAfterDownload = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_transaction WHERE external_id IS NULL OR BTRIM(external_id) = ''");
            long literalNullExternalIdAfterDownload = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_transaction WHERE LOWER(BTRIM(COALESCE(external_id, ''))) = 'null'");
            long readyAfterDownload = statusAfterDownload.getOrDefault("READY", 0L);
            long processingAfterDownload = statusAfterDownload.getOrDefault("PROCESSING", 0L);
            long failedAfterDownload = statusAfterDownload.getOrDefault("FAILED", 0L);
            long dlqAfterDownload = statusAfterDownload.getOrDefault("DLQ", 0L);

            publisher.runPublishCycle();

            Map<String, Long> statusAfterPublish = transactionRepository.countByStatus();
            long txnAfterPublish = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_transaction");
            long nullExternalIdAfterPublish = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_transaction WHERE external_id IS NULL OR BTRIM(external_id) = ''");
            long literalNullExternalIdAfterPublish = queryLong(jdbcTemplate,
                    "SELECT COUNT(*) FROM recon.cloud_ingestion_transaction WHERE LOWER(BTRIM(COALESCE(external_id, ''))) = 'null'");
            long publishedAfterPublish = statusAfterPublish.getOrDefault("PUBLISHED", 0L);
            long readyAfterPublish = statusAfterPublish.getOrDefault("READY", 0L);
            long failedAfterPublish = statusAfterPublish.getOrDefault("FAILED", 0L);
            long dlqAfterPublish = statusAfterPublish.getOrDefault("DLQ", 0L);

            result.put("checkpointResetTo", oneYearAgo.toInstant().toString());
            result.put("connectorEnabled", properties.isEnabled());
            result.put("directFetchCount", directFetchCount);
            result.put("rawBefore", rawBefore);
            result.put("txnBefore", txnBefore);
            result.put("rawAfterDownload", rawAfterDownload);
            result.put("rawDelta", rawAfterDownload - rawBefore);
            result.put("txnAfterDownload", txnAfterDownload);
            result.put("txnAfterPublish", txnAfterPublish);
            result.put("nullExternalIdAfterDownload", nullExternalIdAfterDownload);
            result.put("nullExternalIdAfterPublish", nullExternalIdAfterPublish);
            result.put("literalNullExternalIdAfterDownload", literalNullExternalIdAfterDownload);
            result.put("literalNullExternalIdAfterPublish", literalNullExternalIdAfterPublish);
            result.put("statusBefore", statusBefore);
            result.put("statusAfterDownload", statusAfterDownload);
            result.put("statusAfterPublish", statusAfterPublish);
            result.put("readyAfterDownload", readyAfterDownload);
            result.put("processingAfterDownload", processingAfterDownload);
            result.put("failedAfterDownload", failedAfterDownload);
            result.put("dlqAfterDownload", dlqAfterDownload);
            result.put("publishedAfterPublish", publishedAfterPublish);
            result.put("readyAfterPublish", readyAfterPublish);
            result.put("failedAfterPublish", failedAfterPublish);
            result.put("dlqAfterPublish", dlqAfterPublish);

            System.out.println(objectMapper.writeValueAsString(result));
        }
    }

    private static long queryLong(JdbcTemplate jdbcTemplate, String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static void resetStagingTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "TRUNCATE TABLE recon.cloud_ingestion_error, " +
                        "recon.cloud_ingestion_transaction, " +
                        "recon.cloud_ingestion_raw, " +
                        "recon.cloud_ingestion_checkpoint RESTART IDENTITY CASCADE");
    }
}
