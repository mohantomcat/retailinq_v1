package com.recon.publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.publisher.config.PublisherConfig;
import com.recon.publisher.domain.ParseResult;
import com.recon.publisher.domain.PosTransactionEvent;
import com.recon.publisher.domain.PoslogRecord;
import com.recon.publisher.parser.PoslogStaxParser;
import com.recon.publisher.repository.PublishTrackerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final ObjectMapper objectMapper;
    private final XstoreRuntimeConfigService runtimeConfigService;

    @Value("${kafka.topic.pos-transactions}")
    private String topic;

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

            int released = trackerRepository.releaseStaleClaims(
                    lockTimeoutMinutes);
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
            records.forEach(this::processRecord);

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

    private void processRecord(PoslogRecord record) {
        try {
            var existing = trackerRepository.find(record);
            if (existing != null
                    && "PUBLISHED".equals(existing.getStatus())
                    && existing.getPoslogUpdateDate() != null
                    && existing.getPoslogUpdateDate().equals(record.getUpdateDate())) {
                log.debug("Unchanged poslog - skipping seq={}", record.getTransSeq());
                return;
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
                return;
            }
            if (result.isFailed()) {
                trackerRepository.markFailed(record, result.getErrorMessage());
                checkAndMoveToDlq(record);
                return;
            }

            publishToKafka(record, result.getEvent(), result.isCompressed());

        } catch (Exception e) {
            log.error("Error processing record seq={}: {}", record.getTransSeq(), e.getMessage());
            trackerRepository.markFailed(record, e.getMessage());
            checkAndMoveToDlq(record);
        }
    }

    private void publishToKafka(PoslogRecord record,
                                PosTransactionEvent event,
                                boolean compressed) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.executeInTransaction(ops -> {
                try {
                    var result = ops.send(topic, event.getTransactionKey(), payload)
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

                    log.info("Published key={} partition={} offset={}",
                            event.getTransactionKey(),
                            meta.partition(), meta.offset());
                    return null;

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (Exception e) {
            log.error("Kafka publish failed key={}: {}", event.getTransactionKey(), e.getMessage());
            trackerRepository.markFailed(record, e.getMessage());
            checkAndMoveToDlq(record);
        }
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
}
