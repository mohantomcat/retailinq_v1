package com.recon.xocs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.publisher.domain.LineItem;
import com.recon.publisher.domain.PosTransactionEvent;
import com.recon.publisher.util.ChecksumUtil;
import com.recon.xocs.config.XocsConnectorProperties;
import com.recon.xocs.domain.XocsStagedLine;
import com.recon.xocs.domain.XocsStagedTransaction;
import com.recon.xocs.repository.XocsLineRepository;
import com.recon.xocs.repository.XocsTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class XocsIngestionPublisher {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final XocsConnectorProperties properties;
    private final XocsTransactionRepository transactionRepository;
    private final XocsLineRepository lineRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final XocsRuntimeConfigService runtimeConfigService;

    @Value("${kafka.topic.xocs-transactions}")
    private String topic;

    @Scheduled(fixedDelayString = "${xocs.connector.publish-interval-ms:30000}")
    public void publish() {
        if (!runtimeConfigService.getBoolean("XOCS_SCHEDULER_ENABLED", properties.isSchedulerEnabled())) {
            return;
        }
        runPublishCycle();
    }

    public void runPublishCycle() {
        if (!runtimeConfigService.getBoolean("XOCS_CONNECTOR_ENABLED", properties.isEnabled())) {
            return;
        }

        int lockTimeoutMinutes = runtimeConfigService.getInt(
                "XOCS_PROCESSING_LOCK_TIMEOUT_MINUTES",
                properties.getProcessingLockTimeoutMinutes());
        int publisherBatchSize = runtimeConfigService.getInt(
                "XOCS_PUBLISHER_BATCH_SIZE",
                properties.getPublisherBatchSize());
        int maxRetries = runtimeConfigService.getInt(
                "XOCS_MAX_RETRIES",
                properties.getMaxRetries());

        int released = transactionRepository.releaseStaleClaims(lockTimeoutMinutes);
        if (released > 0) {
            log.warn("Released {} stale XOCS staged transactions", released);
        }

        List<XocsStagedTransaction> rows = transactionRepository.claimBatch(
                UUID.randomUUID().toString(),
                publisherBatchSize,
                maxRetries);
        if (rows.isEmpty()) {
            return;
        }

        Map<Long, List<XocsStagedLine>> linesByTransactionId = lineRepository.findByTransactionIds(
                rows.stream().map(XocsStagedTransaction::getId).toList());

        for (XocsStagedTransaction row : rows) {
            try {
                row.setLineItems(linesByTransactionId.getOrDefault(row.getId(), List.of()));
                PosTransactionEvent event = mapToEvent(row);
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(topic, event.getTransactionKey(), payload).get(10, TimeUnit.SECONDS);
                transactionRepository.markPublished(List.of(row.getId()));
            } catch (Exception e) {
                transactionRepository.markFailed(List.of(row.getId()), e.getMessage(), maxRetries);
                log.error("Failed to publish XOCS staged transaction id={}: {}", row.getId(), e.getMessage(), e);
            }
        }
    }

    private PosTransactionEvent mapToEvent(XocsStagedTransaction row) {
        List<LineItem> lineItems = row.getLineItems().stream()
                .map(line -> LineItem.builder()
                        .lineSeq(line.getRtransLineitmSeq() == null ? 0 : line.getRtransLineitmSeq().intValue())
                        .lineType(line.getLineBusinessType())
                        .itemId(line.getItemId())
                        .quantity(line.getNormalizedQuantity())
                        .unitOfMeasure(line.getUnitOfMeasure())
                        .unitPrice(line.getUnitPrice())
                        .extendedAmount(line.getRawExtendedAmt())
                        .build())
                .toList();

        return PosTransactionEvent.builder()
                .eventType("XOCS_TRANSACTION")
                .eventId("xocs-" + row.getTransactionKey())
                .source(properties.getSourceName())
                .publishedAt(TIMESTAMP_FORMAT.format(java.time.OffsetDateTime.now(ZoneOffset.UTC)))
                .tenantId(properties.getTenantId())
                .tenantTimezone(properties.getTenantTimezone())
                .organizationId(row.getOrganizationId())
                .storeId(String.valueOf(row.getRtlLocId()))
                .businessDate(DATE_FORMAT.format(row.getBusinessDate()))
                .wkstnId(row.getWkstnId())
                .transSeq(row.getTransSeq())
                .externalId(row.getExternalId())
                .transactionKey(row.getTransactionKey())
                .transactionType(row.getTransTypcode())
                .beginDatetime(formatTimestamp(row.getBeginDatetime()))
                .endDatetime(formatTimestamp(row.getEndDatetime()))
                .operatorId(row.getOperatorPartyId() == null ? null : String.valueOf(row.getOperatorPartyId()))
                .totalAmount(row.getTransTotal())
                .lineItems(lineItems)
                .checksum(ChecksumUtil.compute(lineItems))
                .compressed(false)
                .clockDriftDetected(false)
                .build();
    }

    private String formatTimestamp(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return TIMESTAMP_FORMAT.format(timestamp.toInstant().atOffset(ZoneOffset.UTC));
    }
}
