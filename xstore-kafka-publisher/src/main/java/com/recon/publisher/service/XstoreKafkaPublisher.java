package com.recon.publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.integration.kafka.KafkaTopicCatalog;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.integration.recon.TransactionDomain;
import com.recon.publisher.config.PublisherConfig;
import com.recon.publisher.domain.ParseResult;
import com.recon.publisher.domain.PosTransactionEvent;
import com.recon.publisher.domain.PoslogRecord;
import com.recon.publisher.parser.PoslogStaxParser;
import com.recon.publisher.repository.IntegrationRunJournalRepository;
import com.recon.publisher.repository.PublishTrackerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class XstoreKafkaPublisher {

    private final PublishTrackerRepository trackerRepository;
    private final PoslogStaxParser parser;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PublisherConfig config;
    private final CanonicalEnvelopeMapper canonicalEnvelopeMapper;
    private final XstoreIntegrationContract integrationContract;
    private final IntegrationRunJournalRepository integrationRunJournalRepository;
    private final ObjectMapper objectMapper;
    private final XstoreRuntimeConfigService runtimeConfigService;

    @Value("${kafka.topic.xstore-pos-transactions:}")
    private String topic;

    @Value("${kafka.topic.integration-canonical}")
    private String integrationCanonicalTopic;

    @Value("${kafka.topic.dlq}")
    private String dlqTopic;

    private int consecutiveFailures = 0;
    private Instant circuitOpenedAt = null;
    private static final int FAILURE_THRESHOLD = 5;
    private static final int CIRCUIT_BREAK_MIN = 10;

    @Scheduled(fixedDelayString = "${publisher.poll-interval-ms:30000}")
    public void publishPendingTransactions() {
        if (!runtimeConfigService.getBoolean("PUBLISHER_SCHEDULER_ENABLED", config.isSchedulerEnabled())) {
            return;
        }
        if (isCircuitOpen()) {
            log.warn("Circuit OPEN since {} - skipping poll", circuitOpenedAt);
            return;
        }

        try {
            int lockTimeoutMinutes = runtimeConfigService.getInt(
                    "PUBLISHER_PROCESSING_LOCK_TIMEOUT_MINUTES",
                    config.getProcessingLockTimeoutMinutes());
            int batchSize = runtimeConfigService.getInt(
                    "PUBLISHER_BATCH_SIZE",
                    config.getBatchSize());
            int maxRetries = runtimeConfigService.getInt(
                    "PUBLISHER_MAX_RETRIES",
                    config.getMaxRetries());

            int released = trackerRepository.releaseStaleClaims(lockTimeoutMinutes);
            if (released > 0) {
                log.warn("Released {} stale PROCESSING rows back to PENDING", released);
            }

            trackerRepository.seedPendingRows(batchSize);
            String workerId = UUID.randomUUID().toString();
            List<PoslogRecord> records = trackerRepository.claimBatch(
                    workerId,
                    batchSize,
                    maxRetries,
                    lockTimeoutMinutes);

            log.info("Poll complete: {} records claimed", records.size());
            if (!records.isEmpty()) {
                UUID runId = integrationRunJournalRepository.startRun(config.getTenantId(), integrationContract, "SCHEDULED");
                UUID stepId = integrationRunJournalRepository.startStep(
                        runId,
                        "MAP_AND_PUBLISH",
                        "Parse Xstore POS logs and journal canonical integration events",
                        1
                );
                int successCount = 0;
                int errorCount = 0;

                for (PoslogRecord record : records) {
                    PublishOutcome outcome = processRecord(record, runId);
                    if (outcome == PublishOutcome.PUBLISHED) {
                        successCount++;
                    } else if (outcome == PublishOutcome.FAILED) {
                        errorCount++;
                    }
                }

                integrationRunJournalRepository.completeStep(
                        stepId,
                        successCount,
                        errorCount,
                        "Processed " + records.size() + " Xstore tracker rows",
                        errorCount > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED"
                );
                integrationRunJournalRepository.completeRun(
                        runId,
                        records.size(),
                        successCount,
                        errorCount,
                        "Legacy raw-topic publish kept active while canonical integration events were journaled in parallel",
                        errorCount > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED"
                );
            }

            consecutiveFailures = 0;
            circuitOpenedAt = null;

        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.error("Root cause: {}", root.getMessage(), e);
            consecutiveFailures++;
            log.error("Poll failed ({}/{}): {}",
                    consecutiveFailures, FAILURE_THRESHOLD, e.getMessage());
            if (consecutiveFailures >= FAILURE_THRESHOLD) {
                circuitOpenedAt = Instant.now();
                log.error("CIRCUIT BREAKER OPEN - pausing for {}min", CIRCUIT_BREAK_MIN);
            }
        }
    }

    private PublishOutcome processRecord(PoslogRecord record, UUID runId) {
        try {
            var existing = trackerRepository.find(record);
            if (existing != null
                    && "PUBLISHED".equals(existing.getStatus())
                    && existing.getPoslogUpdateDate() != null
                    && existing.getPoslogUpdateDate().equals(record.getUpdateDate())) {
                log.debug("Unchanged poslog - skipping seq={}", record.getTransSeq());
                return PublishOutcome.SKIPPED;
            }

            ParseResult result = parser.parse(
                    config.getOrgId(),
                    record.getRtlLocId(),
                    record.getBusinessDate(),
                    record.getWkstnId(),
                    record.getTransSeq(),
                    record.getPoslogBytes());

            if (result.isSkipped()) {
                trackerRepository.markSkipped(record, result.getTransactionType());
                return PublishOutcome.SKIPPED;
            }
            if (result.isFailed()) {
                trackerRepository.markFailed(record, result.getErrorMessage());
                checkAndMoveToDlq(record);
                recordCanonicalFailure(
                        runId,
                        record,
                        null,
                        "PARSE_ERROR",
                        "XSTORE_PARSE_FAILED",
                        result.getErrorMessage(),
                        false
                );
                return PublishOutcome.FAILED;
            }

            return publishToKafka(record, result.getEvent(), result.isCompressed(), runId);

        } catch (Exception e) {
            log.error("Error processing record seq={}: {}", record.getTransSeq(), e.getMessage());
            trackerRepository.markFailed(record, e.getMessage());
            checkAndMoveToDlq(record);
            recordCanonicalFailure(
                    runId,
                    record,
                    null,
                    "PROCESSING_ERROR",
                    "XSTORE_PROCESSING_FAILED",
                    e.getMessage(),
                    true
            );
            return PublishOutcome.FAILED;
        }
    }

    private PublishOutcome publishToKafka(PoslogRecord record,
                                          PosTransactionEvent event,
                                          boolean compressed,
                                          UUID runId) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String rawTopic = configuredTopic();

            LegacyPublishResult publishResult = kafkaTemplate.executeInTransaction(ops -> {
                try {
                    var result = ops.send(rawTopic, event.getTransactionKey(), payload)
                            .get(10, TimeUnit.SECONDS);

                    var meta = result.getRecordMetadata();
                    trackerRepository.markPublished(
                            record,
                            event.getExternalId(),
                            event.getTransactionKey(),
                            event.getTransactionType(),
                            meta.partition(),
                            meta.offset(),
                            event.getChecksum(),
                            compressed);

                    return new LegacyPublishResult(meta.partition(), meta.offset());

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            boolean canonicalPublished = publishCanonicalEnvelope(runId, record, event, compressed);
            log.info("Published key={} topic={} partition={} offset={}",
                    event.getTransactionKey(),
                    rawTopic,
                    publishResult.partition(), publishResult.offset());
            return canonicalPublished ? PublishOutcome.PUBLISHED : PublishOutcome.FAILED;

        } catch (Exception e) {
            log.error("Kafka publish failed key={}: {}", event.getTransactionKey(), e.getMessage());
            trackerRepository.markFailed(record, e.getMessage());
            checkAndMoveToDlq(record);
            recordCanonicalFailure(
                    runId,
                    record,
                    event,
                    "LEGACY_PUBLISH_ERROR",
                    "XSTORE_LEGACY_TOPIC_PUBLISH_FAILED",
                    e.getMessage(),
                    true
            );
            return PublishOutcome.FAILED;
        }
    }

    private String configuredTopic() {
        if (topic != null && !topic.isBlank()) {
            return topic.trim();
        }
        return KafkaTopicCatalog.rawTransactionTopic("XSTORE", TransactionDomain.POS);
    }

    private boolean publishCanonicalEnvelope(UUID runId,
                                             PoslogRecord record,
                                             PosTransactionEvent event,
                                             boolean compressed) {
        CanonicalIntegrationEnvelope envelope;
        String canonicalPayload;
        try {
            envelope = canonicalEnvelopeMapper.map(integrationContract, record, event, compressed);
            canonicalPayload = objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            integrationRunJournalRepository.recordFailedMessage(
                    config.getTenantId(),
                    runId,
                    integrationContract,
                    event.getEventId(),
                    event.getEventId(),
                    event.getTransactionKey(),
                    event.getExternalId(),
                    null,
                    "MAPPING_ERROR",
                    "CANONICAL_ENVELOPE_SERIALIZATION_FAILED",
                    ex.getMessage(),
                    false
            );
            log.error("Xstore canonical integration event serialization failed for businessKey={}: {}",
                    event.getTransactionKey(), ex.getMessage(), ex);
            return false;
        }
        try {
            kafkaTemplate.send(integrationCanonicalTopic, envelope.getBusinessKey(), canonicalPayload)
                    .get(10, TimeUnit.SECONDS);
            integrationRunJournalRepository.recordPublishedMessage(
                    config.getTenantId(),
                    runId,
                    integrationContract,
                    envelope,
                    canonicalPayload
            );
            return true;
        } catch (Exception ex) {
            integrationRunJournalRepository.recordFailedMessage(
                    config.getTenantId(),
                    runId,
                    integrationContract,
                    envelope.getMessageId(),
                    envelope.getTraceId(),
                    envelope.getBusinessKey(),
                    envelope.getDocumentId(),
                    canonicalPayload,
                    "PUBLISH_ERROR",
                    "CANONICAL_TOPIC_PUBLISH_FAILED",
                    ex.getMessage(),
                    true
            );
            log.error("Xstore canonical integration event publish failed for businessKey={}: {}",
                    envelope.getBusinessKey(), ex.getMessage(), ex);
            return false;
        }
    }

    private void recordCanonicalFailure(UUID runId,
                                        PoslogRecord record,
                                        PosTransactionEvent event,
                                        String errorType,
                                        String errorCode,
                                        String errorMessage,
                                        boolean retryable) {
        String businessKey = event != null ? event.getTransactionKey() : fallbackBusinessKey(record);
        String documentId = event != null ? event.getExternalId() : fallbackDocumentId(record);
        String traceId = event != null ? event.getEventId() : null;
        String payloadSnapshot = buildFailurePayload(record, event);
        integrationRunJournalRepository.recordFailedMessage(
                config.getTenantId(),
                runId,
                integrationContract,
                UUID.randomUUID().toString(),
                traceId,
                businessKey,
                documentId,
                payloadSnapshot,
                errorType,
                errorCode,
                errorMessage,
                retryable
        );
    }

    private String buildFailurePayload(PoslogRecord record, PosTransactionEvent event) {
        try {
            if (event != null) {
                return objectMapper.writeValueAsString(event);
            }
            return objectMapper.writeValueAsString(new FailureSnapshot(
                    record.getOrganizationId(),
                    record.getRtlLocId(),
                    record.getBusinessDate() == null ? null : record.getBusinessDate().toString(),
                    record.getWkstnId(),
                    record.getTransSeq(),
                    record.getUpdateDate() == null ? null : record.getUpdateDate().toInstant().toString()
            ));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fallbackBusinessKey(PoslogRecord record) {
        return record.getOrganizationId() + "|" + fallbackDocumentId(record);
    }

    private String fallbackDocumentId(PoslogRecord record) {
        return String.format("%05d%03d%06d%s",
                record.getRtlLocId(),
                record.getWkstnId(),
                record.getTransSeq(),
                record.getBusinessDate() == null
                        ? "00000000"
                        : record.getBusinessDate().format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    private void checkAndMoveToDlq(PoslogRecord record) {
        int maxRetries = runtimeConfigService.getInt("PUBLISHER_MAX_RETRIES", config.getMaxRetries());
        if (trackerRepository.getRetryCount(record) >= maxRetries) {
            trackerRepository.markDlq(record);
            log.error("Record moved to DLQ store={} seq={}",
                    record.getRtlLocId(), record.getTransSeq());
        }
    }

    private boolean isCircuitOpen() {
        if (circuitOpenedAt == null) {
            return false;
        }
        if (Instant.now().isAfter(circuitOpenedAt.plus(CIRCUIT_BREAK_MIN, ChronoUnit.MINUTES))) {
            log.info("Circuit breaker reset - resuming polling");
            consecutiveFailures = 0;
            circuitOpenedAt = null;
            return false;
        }
        return true;
    }

    private enum PublishOutcome {
        PUBLISHED,
        SKIPPED,
        FAILED
    }

    private record LegacyPublishResult(int partition, long offset) {
    }

    private record FailureSnapshot(long organizationId,
                                   long rtlLocId,
                                   String businessDate,
                                   long wkstnId,
                                   long transSeq,
                                   String updateDate) {
    }
}
