package com.recon.rms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.integration.kafka.KafkaTopicCatalog;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.integration.recon.TransactionDomain;
import com.recon.rms.aggregator.RmsRowAggregator;
import com.recon.rms.config.PollerConfig;
import com.recon.rms.domain.AggregationResult;
import com.recon.rms.domain.RmsPollCheckpoint;
import com.recon.rms.domain.RmsRawRow;
import com.recon.rms.domain.RmsTransactionEvent;
import com.recon.rms.domain.RmsTransactionRow;
import com.recon.rms.mapper.RmsTransactionMapper;
import com.recon.rms.repository.CheckpointRepository;
import com.recon.rms.repository.IntegrationRunJournalRepository;
import com.recon.rms.repository.RmsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class RmsKafkaPoller {
    private final RmsRepository rmsRepository;
    private final CheckpointRepository checkpointRepository;
    private final RmsRowAggregator aggregator;
    private final RmsTransactionMapper mapper;
    private final IntegrationEnvelopeMapper integrationEnvelopeMapper;
    private final RmsIntegrationContract integrationContract;
    private final IntegrationRunJournalRepository integrationRunJournalRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PollerConfig config;
    private final ObjectMapper objectMapper;
    private final PollerRuntimeConfigService runtimeConfigService;

    @Value("${kafka.topic.rms-inventory-transactions:}")
    private String inventoryTopic;

    @Value("${kafka.topic.integration-canonical}")
    private String integrationCanonicalTopic;

    @Value("${kafka.topic.dlq}")
    private String dlqTopic;

    @Scheduled(fixedDelayString = "${rms.poller.poll-interval-ms:300000}")
    public void poll() {
        if (!runtimeConfigService.getBoolean("RMS_POLLER_SCHEDULER_ENABLED", config.isSchedulerEnabled())) {
            return;
        }
        runPollCycle();
    }

    public void runPollCycle() {
        String pollerId = config.getPollerId();
        String leaseOwner = UUID.randomUUID().toString();
        int leaseTimeoutSeconds = runtimeConfigService.getInt(
                "RMS_POLLER_LEASE_TIMEOUT_SECONDS",
                config.getLeaseTimeoutSeconds());
        RmsPollCheckpoint cp = checkpointRepository.findOrCreate(pollerId, config.getTenantId());
        boolean acquired = checkpointRepository.tryAcquireLease(
                pollerId, leaseOwner, leaseTimeoutSeconds);
        if (!acquired) {
            log.info("Skipping poll: lease already held for {}", pollerId);
            return;
        }

        UUID runId = integrationRunJournalRepository.startRun(config.getTenantId(), integrationContract, "SCHEDULED");
        UUID stepId = integrationRunJournalRepository.startStep(
                runId,
                "POLL_AND_PUBLISH",
                "Poll RMS inventory transactions and journal Integration Hub status messages",
                1
        );

        try {
            int safetyMarginMinutes = runtimeConfigService.getInt(
                    "RMS_POLLER_SAFETY_MARGIN_MIN",
                    config.getSafetyMarginMin());
            int pageSize = runtimeConfigService.getInt(
                    "RMS_POLLER_PAGE_SIZE",
                    config.getPageSize());
            Timestamp fromTs = Timestamp.from(
                    cp.getLastProcessedTimestamp().toInstant()
                            .minus(safetyMarginMinutes, ChronoUnit.MINUTES));
            String fromExtId = cp.getLastProcessedExternalId();
            long fromId = cp.getLastProcessedId() == null ? 0L : cp.getLastProcessedId();

            checkpointRepository.markStarted(pollerId);
            log.info("Poll started from={} extId={} id={} ({}min overlap)",
                    fromTs, fromExtId, fromId, safetyMarginMinutes);

            PollMetrics metrics = pollAllPages(fromTs, fromExtId, fromId, pageSize, runId);
            checkpointRepository.markCompleted(pollerId, metrics.sourceCount());
            integrationRunJournalRepository.completeStep(
                    stepId,
                    metrics.publishedCount(),
                    metrics.errorCount(),
                    "Processed " + metrics.sourceCount() + " RMS inventory transactions",
                    metrics.errorCount() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED"
            );
            integrationRunJournalRepository.completeRun(
                    runId,
                    metrics.sourceCount(),
                    metrics.publishedCount(),
                    metrics.errorCount(),
                    "RMS inventory raw-topic publish completed with Integration Hub status journaling in parallel",
                    metrics.errorCount() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED"
            );
            log.info("Poll completed. Total transactions: {}", metrics.sourceCount());

        } catch (Exception e) {
            checkpointRepository.markFailed(pollerId, e.getMessage());
            integrationRunJournalRepository.recordFailedMessage(
                    config.getTenantId(),
                    runId,
                    integrationContract,
                    UUID.randomUUID().toString(),
                    null,
                    pollerId,
                    pollerId,
                    null,
                    "POLL_ERROR",
                    "RMS_POLL_CYCLE_FAILED",
                    e.getMessage(),
                    true
            );
            integrationRunJournalRepository.completeStep(stepId, 0, 1, e.getMessage(), "FAILED");
            integrationRunJournalRepository.completeRun(runId, 0, 0, 1, e.getMessage(), "FAILED");
            log.error("Poll failed: {}", e.getMessage(), e);
        } finally {
            checkpointRepository.releaseLease(pollerId, leaseOwner);
        }
    }

    private PollMetrics pollAllPages(Timestamp fromTs,
                                     String fromExtId,
                                     long fromId,
                                     int pageSize,
                                     UUID runId) throws Exception {
        int sourceCount = 0;
        int publishedCount = 0;
        int errorCount = 0;
        List<RmsRawRow> page;

        do {
            page = rmsRepository.findRawRows(fromTs, fromExtId, fromId, pageSize);

            if (page.isEmpty()) {
                break;
            }

            AggregationResult result = aggregator.aggregate(page, pageSize);
            if (result.isSingleTransactionPage()) {
                log.warn("Single txn fills page; doubling pageSize");
                page = rmsRepository.findRawRows(fromTs, fromExtId, fromId, pageSize * 2);
                result = aggregator.aggregate(page, pageSize * 2);
            }

            if (!result.getTransactions().isEmpty()) {
                PublishMetrics pageMetrics = publishPage(result.getTransactions(), runId);
                sourceCount += result.getTransactions().size();
                publishedCount += pageMetrics.publishedCount();
                errorCount += pageMetrics.errorCount();
            }

            RmsRawRow last = page.get(page.size() - 1);
            fromTs = last.getUpdateDateTime();
            fromExtId = last.getExternalId();
            fromId = last.getId();
            checkpointRepository.updateComposite(config.getPollerId(), fromTs, fromExtId, fromId);
        } while (page.size() >= pageSize);

        return new PollMetrics(sourceCount, publishedCount, errorCount);
    }

    private PublishMetrics publishPage(List<RmsTransactionRow> transactions, UUID runId) {
        final int[] publishedCount = {0};
        final int[] errorCount = {0};
        kafkaTemplate.executeInTransaction(ops -> {
            for (RmsTransactionRow row : transactions) {
                try {
                    validateRow(row);
                    RmsTransactionEvent event = mapper.mapToEvent(row, config.getOrgId());
                    String rawTopic = rawTopic();
                    String payload = objectMapper.writeValueAsString(event);
                    ops.send(rawTopic, event.getTransactionKey(), payload)
                            .get(10, TimeUnit.SECONDS);
                    boolean journaled = publishIntegrationEnvelope(runId, event);
                    if (journaled) {
                        publishedCount[0]++;
                    } else {
                        errorCount[0]++;
                    }
                    log.debug("Published rmsTxn key={} topic={}", event.getTransactionKey(), rawTopic);

                } catch (Exception e) {
                    errorCount[0]++;
                    log.error("Failed to publish row externalId={}: {}", row.getExternalId(), e.getMessage());
                    recordStatusFailure(runId, row, null, "LEGACY_PUBLISH_ERROR", "RMS_INVENTORY_TOPIC_PUBLISH_FAILED", e.getMessage(), true);
                    sendToDlq(row);
                }
            }
            return null;
        });
        return new PublishMetrics(publishedCount[0], errorCount[0]);
    }

    private String rawTopic() {
        if (inventoryTopic != null && !inventoryTopic.isBlank()) {
            return inventoryTopic.trim();
        }
        return KafkaTopicCatalog.rawTransactionTopic("RMS", TransactionDomain.INVENTORY);
    }

    private boolean publishIntegrationEnvelope(UUID runId,
                                               RmsTransactionEvent event) {
        CanonicalIntegrationEnvelope envelope;
        String envelopeJson;
        try {
            envelope = integrationEnvelopeMapper.map(integrationContract, event);
            envelopeJson = objectMapper.writeValueAsString(envelope);
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
                    "RMS_STATUS_ENVELOPE_SERIALIZATION_FAILED",
                    ex.getMessage(),
                    false
            );
            log.error("RMS Integration Hub envelope serialization failed for businessKey={}: {}",
                    event.getTransactionKey(), ex.getMessage(), ex);
            return false;
        }

        try {
            kafkaTemplate.send(integrationCanonicalTopic, envelope.getBusinessKey(), envelopeJson)
                    .get(10, TimeUnit.SECONDS);
            integrationRunJournalRepository.recordPublishedMessage(
                    config.getTenantId(),
                    runId,
                    integrationContract,
                    envelope,
                    objectMapper.writeValueAsString(event)
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
                    serializeEvent(event),
                    "PUBLISH_ERROR",
                    "RMS_STATUS_INTEGRATION_PUBLISH_FAILED",
                    ex.getMessage(),
                    true
            );
            log.error("RMS Integration Hub publish failed for businessKey={}: {}",
                    envelope.getBusinessKey(), ex.getMessage(), ex);
            return false;
        }
    }

    private void validateRow(RmsTransactionRow row) {
        if (row.getExternalId() == null || row.getExternalId().isBlank()) {
            throw new IllegalArgumentException("Missing EXTERNAL_ID for RMS transaction row");
        }
    }

    private void recordStatusFailure(UUID runId,
                                     RmsTransactionRow row,
                                     RmsTransactionEvent event,
                                     String errorType,
                                     String errorCode,
                                     String errorMessage,
                                     boolean retryable) {
        String businessKey = event != null ? event.getTransactionKey() : config.getOrgId() + "|" + row.getExternalId();
        String traceId = event != null ? event.getEventId() : null;
        integrationRunJournalRepository.recordFailedMessage(
                config.getTenantId(),
                runId,
                integrationContract,
                UUID.randomUUID().toString(),
                traceId,
                businessKey,
                row.getExternalId(),
                event != null ? serializeEvent(event) : serializeRow(row),
                errorType,
                errorCode,
                errorMessage,
                retryable
        );
    }

    private String serializeEvent(RmsTransactionEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String serializeRow(RmsTransactionRow row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void sendToDlq(RmsTransactionRow row) {
        try {
            String payload = objectMapper.writeValueAsString(row);
            kafkaTemplate.send(dlqTopic, row.getExternalId(), payload);
            log.warn("Sent to DLQ externalId={}", row.getExternalId());
        } catch (Exception e) {
            log.error("DLQ send also failed externalId={}: {}", row.getExternalId(), e.getMessage());
        }
    }

    private record PollMetrics(int sourceCount, int publishedCount, int errorCount) {
    }

    private record PublishMetrics(int publishedCount, int errorCount) {
    }
}
