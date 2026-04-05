package com.recon.poller.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.integration.kafka.KafkaTopicCatalog;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.integration.recon.TransactionDomain;
import com.recon.integration.recon.TransactionDomainResolver;
import com.recon.poller.aggregator.SiocsRowAggregator;
import com.recon.poller.config.PollerConfig;
import com.recon.poller.domain.AggregationResult;
import com.recon.poller.domain.SimTransactionEvent;
import com.recon.poller.domain.SiocsPollCheckpoint;
import com.recon.poller.domain.SiocsRawRow;
import com.recon.poller.domain.SiocsTransactionRow;
import com.recon.poller.mapper.SiocsTransactionMapper;
import com.recon.poller.repository.CheckpointRepository;
import com.recon.poller.repository.IntegrationRunJournalRepository;
import com.recon.poller.repository.SiocsRepository;
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
public class SiocsKafkaPoller {
    private final SiocsRepository siocsRepository;
    private final CheckpointRepository checkpointRepository;
    private final SiocsRowAggregator aggregator;
    private final SiocsTransactionMapper mapper;
    private final IntegrationEnvelopeMapper integrationEnvelopeMapper;
    private final SiocsIntegrationContract integrationContract;
    private final IntegrationRunJournalRepository integrationRunJournalRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PollerConfig config;
    private final ObjectMapper objectMapper;
    private final PollerRuntimeConfigService runtimeConfigService;

    @Value("${kafka.topic.sim-pos-transactions:}")
    private String posTopic;

    @Value("${kafka.topic.sim-inventory-transactions:}")
    private String inventoryTopic;

    @Value("${kafka.topic.sim-unknown-transactions:}")
    private String unknownTopic;

    @Value("${kafka.topic.integration-canonical}")
    private String integrationCanonicalTopic;

    @Value("${kafka.topic.dlq}")
    private String dlqTopic;

    @Scheduled(fixedDelayString = "${siocs.poller.poll-interval-ms:300000}")
    public void poll() {
        if (!runtimeConfigService.getBoolean("SIOCS_POLLER_SCHEDULER_ENABLED", config.isSchedulerEnabled())) {
            return;
        }
        runPollCycle();
    }

    public void runPollCycle() {
        String pollerId = config.getPollerId();
        String leaseOwner = UUID.randomUUID().toString();
        int leaseTimeoutSeconds = runtimeConfigService.getInt(
                "SIOCS_POLLER_LEASE_TIMEOUT_SECONDS",
                config.getLeaseTimeoutSeconds());
        SiocsPollCheckpoint cp =
                checkpointRepository.findOrCreate(pollerId, config.getTenantId());
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
                "Poll SIM transactions and journal Integration Hub status messages",
                1
        );

        try {
            int safetyMarginMinutes = runtimeConfigService.getInt(
                    "SIOCS_POLLER_SAFETY_MARGIN_MIN",
                    config.getSafetyMarginMin());
            int pageSize = runtimeConfigService.getInt(
                    "SIOCS_POLLER_PAGE_SIZE",
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
                    "Processed " + metrics.sourceCount() + " SIM transactions",
                    metrics.errorCount() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED"
            );
            integrationRunJournalRepository.completeRun(
                    runId,
                    metrics.sourceCount(),
                    metrics.publishedCount(),
                    metrics.errorCount(),
                    "Legacy SIM raw-topic publish kept active while Integration Hub status messages were journaled in parallel",
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
                    "SIM_POLL_CYCLE_FAILED",
                    e.getMessage(),
                    true
            );
            integrationRunJournalRepository.completeStep(
                    stepId,
                    0,
                    1,
                    e.getMessage(),
                    "FAILED"
            );
            integrationRunJournalRepository.completeRun(
                    runId,
                    0,
                    0,
                    1,
                    e.getMessage(),
                    "FAILED"
            );
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
        List<SiocsRawRow> page;

        do {
            page = siocsRepository.findRawRows(
                    fromTs, fromExtId, fromId, pageSize);

            if (page.isEmpty()) {
                break;
            }

            AggregationResult result =
                    aggregator.aggregate(page, pageSize);

            if (result.isSingleTransactionPage()) {
                log.warn("Single txn fills page; doubling pageSize");
                page = siocsRepository.findRawRows(
                        fromTs, fromExtId, fromId, pageSize * 2);
                result = aggregator.aggregate(
                        page, pageSize * 2);
            }

            if (!result.getTransactions().isEmpty()) {
                PublishMetrics pageMetrics = publishPage(result.getTransactions(), runId);
                sourceCount += result.getTransactions().size();
                publishedCount += pageMetrics.publishedCount();
                errorCount += pageMetrics.errorCount();
            }

            SiocsRawRow last = page.get(page.size() - 1);
            fromTs = last.getUpdateDateTime();
            fromExtId = last.getExternalId();
            fromId = last.getId();
            checkpointRepository.updateComposite(
                    config.getPollerId(), fromTs, fromExtId, fromId);

        } while (page.size() >= pageSize);

        return new PollMetrics(sourceCount, publishedCount, errorCount);
    }

    private PublishMetrics publishPage(List<SiocsTransactionRow> transactions, UUID runId) {
        final int[] publishedCount = {0};
        final int[] errorCount = {0};
        kafkaTemplate.executeInTransaction(ops -> {
            for (SiocsTransactionRow row : transactions) {
                try {
                    validateRow(row);
                    SimTransactionEvent event =
                            mapper.mapToEvent(row, config.getOrgId());
                    TransactionDomain domain =
                            TransactionDomainResolver.resolve(event.getTransactionType());
                    String rawTopic = rawTopic(domain);
                    String payload =
                            objectMapper.writeValueAsString(event);
                    ops.send(rawTopic, event.getTransactionKey(), payload)
                            .get(10, TimeUnit.SECONDS);
                    boolean journaled = publishIntegrationEnvelope(runId, event);
                    if (journaled) {
                        publishedCount[0]++;
                    } else {
                        errorCount[0]++;
                    }
                    log.debug("Published simTxn key={} domain={} topic={}",
                            event.getTransactionKey(), domain, rawTopic);

                } catch (Exception e) {
                    errorCount[0]++;
                    log.error("Failed to publish row externalId={}: {}",
                            row.getExternalId(), e.getMessage());
                    recordStatusFailure(runId, row, null, "LEGACY_PUBLISH_ERROR", "SIM_DOMAIN_TOPIC_PUBLISH_FAILED", e.getMessage(), true);
                    sendToDlq(row, e.getMessage());
                }
            }
            return null;
        });
        return new PublishMetrics(publishedCount[0], errorCount[0]);
    }

    private String rawTopic(TransactionDomain domain) {
        return switch (domain) {
            case POS -> configuredTopic(posTopic, TransactionDomain.POS);
            case INVENTORY -> configuredTopic(inventoryTopic, TransactionDomain.INVENTORY);
            case UNKNOWN -> configuredTopic(unknownTopic, TransactionDomain.UNKNOWN);
        };
    }

    private String configuredTopic(String configuredTopic,
                                   TransactionDomain domain) {
        if (configuredTopic != null && !configuredTopic.isBlank()) {
            return configuredTopic.trim();
        }
        return KafkaTopicCatalog.rawTransactionTopic("SIM", domain);
    }

    private boolean publishIntegrationEnvelope(UUID runId,
                                               SimTransactionEvent event) {
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
                    "SIM_STATUS_ENVELOPE_SERIALIZATION_FAILED",
                    ex.getMessage(),
                    false
            );
            log.error("SIM Integration Hub envelope serialization failed for businessKey={}: {}",
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
                    "SIM_STATUS_INTEGRATION_PUBLISH_FAILED",
                    ex.getMessage(),
                    true
            );
            log.error("SIM Integration Hub publish failed for businessKey={}: {}",
                    envelope.getBusinessKey(), ex.getMessage(), ex);
            return false;
        }
    }

    private void validateRow(SiocsTransactionRow row) {
        if (row.getExternalId() == null
                || row.getExternalId().length() != 22) {
            throw new IllegalArgumentException(
                    "Invalid EXTERNAL_ID: " + row.getExternalId() +
                            " - must be exactly 22 characters");
        }

        String extId = row.getExternalId();
        log.debug("extId={} store={} wkstn={} seq={} date={}",
                extId,
                trimLeadingZeroes(extId.substring(0, 5)),
                trimLeadingZeroes(extId.substring(5, 8)),
                trimLeadingZeroes(extId.substring(8, 14)),
                extId.substring(14, 22));
    }

    private String trimLeadingZeroes(String value) {
        String normalized = value.replaceFirst("^0+(?!$)", "");
        return normalized.isBlank() ? "0" : normalized;
    }

    private void recordStatusFailure(UUID runId,
                                     SiocsTransactionRow row,
                                     SimTransactionEvent event,
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

    private String serializeEvent(SimTransactionEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String serializeRow(SiocsTransactionRow row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void sendToDlq(SiocsTransactionRow row,
                           String errorMessage) {
        try {
            String payload = objectMapper.writeValueAsString(row);
            kafkaTemplate.send(dlqTopic, row.getExternalId(), payload);
            log.warn("Sent to DLQ externalId={}", row.getExternalId());
        } catch (Exception e) {
            log.error("DLQ send also failed externalId={}: {}",
                    row.getExternalId(), e.getMessage());
        }
    }

    private record PollMetrics(int sourceCount, int publishedCount, int errorCount) {
    }

    private record PublishMetrics(int publishedCount, int errorCount) {
    }
}
