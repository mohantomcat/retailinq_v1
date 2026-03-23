package com.recon.cloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudIngestionTransactionRow;
import com.recon.cloud.domain.CloudLineItem;
import com.recon.cloud.domain.CloudStagedTransaction;
import com.recon.cloud.domain.CloudTransactionEvent;
import com.recon.cloud.repository.CloudTransactionRepository;
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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CloudRuntimeConfigService runtimeConfigService;

    @Value("${kafka.topic.mfcs-transactions}")
    private String topic;

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

        for (Map.Entry<String, List<CloudIngestionTransactionRow>> entry : grouped.entrySet()) {
            List<CloudIngestionTransactionRow> transactionRows = entry.getValue();
            List<Long> ids = transactionRows.stream()
                    .map(CloudIngestionTransactionRow::getId)
                    .toList();
            try {
                CloudStagedTransaction stagedTransaction = aggregate(transactionRows);
                CloudTransactionEvent event = eventMapper.map(stagedTransaction);
                if (stagedTransaction.getExternalId() == null || stagedTransaction.getExternalId().isBlank()) {
                    log.warn("Publishing staged cloud transaction without externalId using fallback key={} sourceRecordKey={} firstRowId={}",
                            event.getTransactionKey(), stagedTransaction.getSourceRecordKey(), stagedTransaction.getFirstRowId());
                }
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(topic, event.getTransactionKey(), payload)
                        .get(10, TimeUnit.SECONDS);
                transactionRepository.markPublished(ids);
                log.debug("Published staged cloud transaction key={}", event.getTransactionKey());
            } catch (Exception e) {
                transactionRepository.markFailed(ids, e.getMessage(), maxRetries);
                log.error("Failed to publish staged transaction externalId={}: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }
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
                .build();
    }
}
