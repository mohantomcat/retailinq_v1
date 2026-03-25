package com.recon.cloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudIngestionTransactionRow;
import com.recon.cloud.domain.CloudLineItem;
import com.recon.cloud.domain.CloudStagedTransaction;
import com.recon.cloud.domain.CloudTransactionEvent;
import com.recon.cloud.repository.CloudTransactionRepository;
import com.recon.cloud.repository.IntegrationRunJournalRepository;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudIngestionPublisher {

    private final CloudConnectorProperties properties;
    private final CloudTransactionRepository transactionRepository;
    private final CloudTransactionEventMapper eventMapper;
    private final CanonicalEnvelopeMapper canonicalEnvelopeMapper;
    private final MfcsIntegrationContract integrationContract;
    private final IntegrationRunJournalRepository integrationRunJournalRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CloudRuntimeConfigService runtimeConfigService;

    @Value("${kafka.topic.mfcs-transactions}")
    private String topic;

    @Value("${kafka.topic.integration-canonical}")
    private String integrationCanonicalTopic;

    @Scheduled(fixedDelayString =
            "${mfcs.connector.publish-interval-ms:30000}")
    public void publish() {
        if (!runtimeConfigService.getBoolean("MFCS_SCHEDULER_ENABLED", properties.isSchedulerEnabled())) {
            return;
        }
        runPublishCycle();
    }

    public void runPublishCycle() {
        if (!runtimeConfigService.getBoolean("MFCS_CONNECTOR_ENABLED", properties.isEnabled())) {
            return;
        }

        int lockTimeoutMinutes = runtimeConfigService.getInt(
                "MFCS_PROCESSING_LOCK_TIMEOUT_MINUTES",
                properties.getProcessingLockTimeoutMinutes());
        int publisherBatchSize = runtimeConfigService.getInt(
                "MFCS_PUBLISHER_BATCH_SIZE",
                properties.getPublisherBatchSize());
        int maxRetries = runtimeConfigService.getInt(
                "MFCS_MAX_RETRIES",
                properties.getMaxRetries());

        int released = transactionRepository.releaseStaleClaims(
                lockTimeoutMinutes);
        if (released > 0) {
            log.warn("Released {} stale PROCESSING rows back to READY", released);
        }

        String workerId = UUID.randomUUID().toString();
        List<CloudIngestionTransactionRow> claimedRows = transactionRepository.claimBatch(
                workerId,
                publisherBatchSize,
                maxRetries);
        if (claimedRows.isEmpty()) {
            return;
        }

        Map<String, List<CloudIngestionTransactionRow>> grouped = claimedRows.stream()
                .collect(Collectors.groupingBy(
                        CloudIngestionTransactionRow::getExternalId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        UUID runId = integrationRunJournalRepository.startRun(properties.getTenantId(), integrationContract, "SCHEDULED");
        UUID stepId = integrationRunJournalRepository.startStep(
                runId,
                "MAP_AND_PUBLISH",
                "Map source transactions and publish canonical integration events",
                1
        );
        int successCount = 0;
        int errorCount = 0;

        for (Map.Entry<String, List<CloudIngestionTransactionRow>> entry : grouped.entrySet()) {
            List<CloudIngestionTransactionRow> transactionRows = entry.getValue();
            List<Long> ids = transactionRows.stream()
                    .map(CloudIngestionTransactionRow::getId)
                    .toList();
            try {
                CloudStagedTransaction stagedTransaction = aggregate(transactionRows);
                CloudTransactionEvent event = eventMapper.map(stagedTransaction);
                CanonicalIntegrationEnvelope envelope = canonicalEnvelopeMapper.map(integrationContract, stagedTransaction, event);
                if (stagedTransaction.getExternalId() == null || stagedTransaction.getExternalId().isBlank()) {
                    log.warn("Publishing staged cloud transaction without externalId using fallback key={} sourceRecordKey={} firstRowId={}",
                            event.getTransactionKey(), stagedTransaction.getSourceRecordKey(), stagedTransaction.getFirstRowId());
                }
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(topic, event.getTransactionKey(), payload)
                        .get(10, TimeUnit.SECONDS);
                boolean canonicalPublished = publishCanonicalEnvelope(runId, envelope);
                transactionRepository.markPublished(ids);
                if (canonicalPublished) {
                    successCount++;
                } else {
                    errorCount++;
                }
                log.debug("Published staged cloud transaction key={}", event.getTransactionKey());
            } catch (Exception e) {
                transactionRepository.markFailed(ids, e.getMessage(), maxRetries);
                recordCanonicalFailure(runId, entry.getKey(), transactionRows, e);
                errorCount++;
                log.error("Failed to publish staged transaction externalId={}: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }

        integrationRunJournalRepository.completeStep(
                stepId,
                successCount,
                errorCount,
                "Processed " + grouped.size() + " staged transactions",
                errorCount > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED"
        );
        integrationRunJournalRepository.completeRun(
                runId,
                grouped.size(),
                successCount,
                errorCount,
                "Legacy recon publish kept active while canonical integration events were journaled in parallel",
                errorCount > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED"
        );
    }

    private CloudStagedTransaction aggregate(List<CloudIngestionTransactionRow> rows) {
        CloudIngestionTransactionRow first = rows.get(0);
        List<CloudLineItem> lineItems = new ArrayList<>();
        for (CloudIngestionTransactionRow row : rows) {
            if (row.getItemId() == null && row.getQuantity() == null) {
                continue;
            }
            lineItems.add(CloudLineItem.builder()
                    .id(row.getLineId())
                    .itemId(row.getItemId())
                    .quantity(row.getQuantity())
                    .unitOfMeasure(row.getUnitOfMeasure())
                    .type(row.getType())
                    .processingStatus(row.getProcessingStatus())
                    .transactionExtendedId(row.getTransactionExtendedId())
                    .build());
        }

        int postingCount = (int) rows.stream()
                .map(CloudIngestionTransactionRow::getTransactionExtendedId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long duplicateItemKeys = rows.stream()
                .filter(row -> row.getRequestId() != null
                        && row.getExternalId() != null
                        && row.getItemId() != null)
                .collect(Collectors.groupingBy(
                        row -> row.getRequestId() + "|" + row.getExternalId() + "|" + row.getItemId(),
                        LinkedHashMap::new,
                        Collectors.counting()))
                .values().stream()
                .filter(count -> count > 1)
                .count();
        BigDecimal totalQuantity = lineItems.stream()
                .map(CloudLineItem::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CloudStagedTransaction.builder()
                .externalId(first.getExternalId())
                .sourceRecordKey(first.getSourceRecordKey())
                .firstRowId(first.getId())
                .requestId(first.getRequestId())
                .storeId(first.getStoreId())
                .transactionDateTime(first.getTransactionDateTime())
                .updateDateTime(first.getUpdateDateTime())
                .type(first.getType())
                .processingStatus(first.getProcessingStatus())
                .lineItems(lineItems)
                .lineItemCount(lineItems.size())
                .totalQuantity(totalQuantity)
                .postingCount((int) Math.max(postingCount, duplicateItemKeys + 1))
                .duplicateFlag(postingCount > 1 || duplicateItemKeys > 0)
                .rawPayloadId(first.getRawPayloadId())
                .build();
    }

    private boolean publishCanonicalEnvelope(UUID runId,
                                             CanonicalIntegrationEnvelope envelope) {
        String canonicalPayload;
        try {
            canonicalPayload = objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            integrationRunJournalRepository.recordFailedMessage(
                    properties.getTenantId(),
                    runId,
                    integrationContract,
                    envelope.getMessageId(),
                    envelope.getTraceId(),
                    envelope.getBusinessKey(),
                    envelope.getDocumentId(),
                    null,
                    "MAPPING_ERROR",
                    "CANONICAL_ENVELOPE_SERIALIZATION_FAILED",
                    ex.getMessage(),
                    false
            );
            log.error("Canonical integration event serialization failed for businessKey={}: {}",
                    envelope.getBusinessKey(), ex.getMessage(), ex);
            return false;
        }
        try {
            kafkaTemplate.send(integrationCanonicalTopic, envelope.getBusinessKey(), canonicalPayload)
                    .get(10, TimeUnit.SECONDS);
            integrationRunJournalRepository.recordPublishedMessage(
                    properties.getTenantId(),
                    runId,
                    integrationContract,
                    envelope,
                    canonicalPayload
            );
            return true;
        } catch (Exception ex) {
            integrationRunJournalRepository.recordFailedMessage(
                    properties.getTenantId(),
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
            log.error("Canonical integration event publish failed for businessKey={}: {}",
                    envelope.getBusinessKey(), ex.getMessage(), ex);
            return false;
        }
    }

    private void recordCanonicalFailure(UUID runId,
                                        String externalId,
                                        List<CloudIngestionTransactionRow> transactionRows,
                                        Exception exception) {
        CloudIngestionTransactionRow first = transactionRows.isEmpty() ? null : transactionRows.get(0);
        String payloadSnapshot = null;
        try {
            payloadSnapshot = objectMapper.writeValueAsString(transactionRows);
        } catch (Exception ignored) {
            payloadSnapshot = null;
        }
        integrationRunJournalRepository.recordFailedMessage(
                properties.getTenantId(),
                runId,
                integrationContract,
                UUID.randomUUID().toString(),
                null,
                externalId,
                first != null ? first.getExternalId() : externalId,
                payloadSnapshot,
                "LEGACY_PUBLISH_ERROR",
                "MFCS_LEGACY_TOPIC_PUBLISH_FAILED",
                exception.getMessage(),
                true
        );
    }
}
