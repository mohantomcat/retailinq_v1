package com.recon.cloud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudApiPage;
import com.recon.cloud.domain.CloudApiTransaction;
import com.recon.cloud.domain.CloudIngestionCheckpoint;
import com.recon.cloud.repository.CloudCheckpointRepository;
import com.recon.cloud.repository.CloudErrorRepository;
import com.recon.cloud.repository.CloudRawRepository;
import com.recon.cloud.repository.CloudTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudRestDownloadService {

    private final CloudConnectorProperties properties;
    private final CloudCheckpointRepository checkpointRepository;
    private final CloudRestClient restClient;
    private final CloudRawRepository rawRepository;
    private final CloudTransactionRepository transactionRepository;
    private final CloudErrorRepository errorRepository;
    private final ObjectMapper objectMapper;
    private final CloudRuntimeConfigService runtimeConfigService;

    @Scheduled(fixedDelayString =
            "${cloud.connector.download-interval-ms:300000}")
    public void download() {
        if (!runtimeConfigService.getBoolean("CLOUD_SCHEDULER_ENABLED", properties.isSchedulerEnabled())) {
            return;
        }
        runDownloadCycle();
    }

    public void runDownloadCycle() {
        if (!runtimeConfigService.getBoolean("CLOUD_CONNECTOR_ENABLED", properties.isEnabled())) {
            return;
        }

        CloudIngestionCheckpoint checkpoint = checkpointRepository.findOrCreate(
                properties.getConnectorName(),
                properties.getSourceName(),
                properties.getTenantId());

        Instant checkpointInstant = checkpoint.getLastSuccessTimestamp() == null
                ? Instant.EPOCH
                : checkpoint.getLastSuccessTimestamp().toInstant();
        int overlapMinutes = runtimeConfigService.getInt("CLOUD_OVERLAP_MINUTES", properties.getOverlapMinutes());
        int batchSize = runtimeConfigService.getInt("CLOUD_BATCH_SIZE", properties.getBatchSize());
        Timestamp fromTimestamp = Timestamp.from(
                checkpointInstant.minus(overlapMinutes, ChronoUnit.MINUTES));
        Long lastCursorId = checkpoint.getLastCursorId();
        checkpointRepository.markStarted(properties.getConnectorName());

        try {
            while (true) {
                CloudApiPage page = restClient.fetchTransactions(
                        lastCursorId,
                        fromTimestamp,
                        batchSize);
                if (page.getRecords() == null || page.getRecords().isEmpty()) {
                    checkpointRepository.advance(
                            properties.getConnectorName(),
                            lastCursorId,
                            Timestamp.from(Instant.now()));
                    break;
                }

                String requestCursor = buildRequestCursor(lastCursorId);
                persistPage(requestCursor, page.getRecords());
                CloudApiTransaction lastRecord = page.getRecords()
                        .get(page.getRecords().size() - 1);
                lastCursorId = lastRecord.getId();
                checkpointRepository.advance(
                        properties.getConnectorName(),
                        lastCursorId,
                        toTimestamp(lastRecord.getUpdateDateTime()));

                if (!page.isHasMore()) {
                    break;
                }
            }
        } catch (Exception e) {
            checkpointRepository.markFailed(
                    properties.getConnectorName(), e.getMessage());
            log.error("Cloud REST download failed: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void persistPage(String cursor, List<CloudApiTransaction> records) {
        String requestId = UUID.randomUUID().toString();
        for (CloudApiTransaction record : records) {
            try {
                validateRecord(record);
                String payload = objectMapper.writeValueAsString(record);
                long rawPayloadId = rawRepository.insert(
                        properties.getTenantId(),
                        properties.getSourceName(),
                        record.getSourceRecordKey(),
                        cursor,
                        payload,
                        requestId);
                transactionRepository.upsertTransactionRows(
                        properties.getTenantId(),
                        properties.getSourceName(),
                        cursor,
                        rawPayloadId,
                        List.of(record));
            } catch (IllegalArgumentException e) {
                writeError(record, "VALIDATION_ERROR", e.getMessage());
            } catch (JsonProcessingException e) {
                writeError(record, "SERIALIZATION_ERROR", e.getMessage());
            } catch (Exception e) {
                writeError(record, "INGESTION_ERROR", e.getMessage());
                throw e;
            }
        }
    }

    private void writeError(CloudApiTransaction record,
                            String errorType,
                            String errorMessage) {
        try {
            errorRepository.save(
                    properties.getTenantId(),
                    properties.getSourceName(),
                    record.getSourceRecordKey(),
                    objectMapper.writeValueAsString(record),
                    errorType,
                    errorMessage);
        } catch (JsonProcessingException ignored) {
            errorRepository.save(
                    properties.getTenantId(),
                    properties.getSourceName(),
                    record.getSourceRecordKey(),
                    "{}",
                    errorType,
                    errorMessage);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? Timestamp.from(Instant.now()) : Timestamp.from(instant);
    }

    private String buildRequestCursor(Long lastCursorId) {
        return String.valueOf(lastCursorId == null ? 0L : lastCursorId);
    }

    private void validateRecord(CloudApiTransaction record) {
        if (record == null) {
            throw new IllegalArgumentException("Record is null");
        }
        if (record.getSourceRecordKey() == null || record.getSourceRecordKey().isBlank()) {
            throw new IllegalArgumentException("Missing sourceRecordKey");
        }
        if (!hasUsableText(record.getExternalId())) {
            throw new IllegalArgumentException("Missing externalId");
        }
    }

    private boolean hasUsableText(String value) {
        return value != null
                && !value.isBlank()
                && !"null".equalsIgnoreCase(value.trim());
    }
}
