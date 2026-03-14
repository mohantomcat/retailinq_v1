package com.recon.poller.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.poller.aggregator.SiocsRowAggregator;
import com.recon.poller.config.PollerConfig;
import com.recon.poller.domain.AggregationResult;
import com.recon.poller.domain.SimTransactionEvent;
import com.recon.poller.domain.SiocsPollCheckpoint;
import com.recon.poller.domain.SiocsRawRow;
import com.recon.poller.domain.SiocsTransactionRow;
import com.recon.poller.mapper.SiocsTransactionMapper;
import com.recon.poller.repository.CheckpointRepository;
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

    private static final String POLLER_ID = "siocs-main";

    private final SiocsRepository siocsRepository;
    private final CheckpointRepository checkpointRepository;
    private final SiocsRowAggregator aggregator;
    private final SiocsTransactionMapper mapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PollerConfig config;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.sim-transactions}")
    private String topic;

    @Value("${kafka.topic.dlq}")
    private String dlqTopic;

    @Scheduled(fixedDelayString = "${siocs.poller.poll-interval-ms:300000}")
    public void poll() {
        if (!config.isSchedulerEnabled()) {
            return;
        }
        runPollCycle();
    }

    public void runPollCycle() {
        String leaseOwner = UUID.randomUUID().toString();
        SiocsPollCheckpoint cp =
                checkpointRepository.findOrCreate(POLLER_ID, config.getTenantId());
        boolean acquired = checkpointRepository.tryAcquireLease(
                POLLER_ID, leaseOwner, config.getLeaseTimeoutSeconds());
        if (!acquired) {
            log.info("Skipping poll: lease already held for {}", POLLER_ID);
            return;
        }

        try {
            Timestamp fromTs = Timestamp.from(
                    cp.getLastProcessedTimestamp().toInstant()
                            .minus(config.getSafetyMarginMin(), ChronoUnit.MINUTES));
            String fromExtId = cp.getLastProcessedExternalId();
            long fromId = cp.getLastProcessedId() == null ? 0L : cp.getLastProcessedId();

            checkpointRepository.markStarted(POLLER_ID);
            log.info("Poll started from={} extId={} id={} ({}min overlap)",
                    fromTs, fromExtId, fromId, config.getSafetyMarginMin());

            int total = pollAllPages(fromTs, fromExtId, fromId);
            checkpointRepository.markCompleted(POLLER_ID, total);
            log.info("Poll completed. Total transactions: {}", total);

        } catch (Exception e) {
            checkpointRepository.markFailed(POLLER_ID, e.getMessage());
            log.error("Poll failed: {}", e.getMessage(), e);
        } finally {
            checkpointRepository.releaseLease(POLLER_ID, leaseOwner);
        }
    }

    private int pollAllPages(Timestamp fromTs,
                             String fromExtId,
                             long fromId) throws Exception {
        int total = 0;
        List<SiocsRawRow> page;

        do {
            page = siocsRepository.findRawRows(
                    fromTs, fromExtId, fromId, config.getPageSize());

            if (page.isEmpty()) {
                break;
            }

            AggregationResult result =
                    aggregator.aggregate(page, config.getPageSize());

            if (result.isSingleTransactionPage()) {
                log.warn("Single txn fills page; doubling pageSize");
                page = siocsRepository.findRawRows(
                        fromTs, fromExtId, fromId, config.getPageSize() * 2);
                result = aggregator.aggregate(
                        page, config.getPageSize() * 2);
            }

            if (!result.getTransactions().isEmpty()) {
                publishPage(result.getTransactions());
                total += result.getTransactions().size();
            }

            SiocsRawRow last = page.get(page.size() - 1);
            fromTs = last.getUpdateDateTime();
            fromExtId = last.getExternalId();
            fromId = last.getId();
            checkpointRepository.updateComposite(
                    POLLER_ID, fromTs, fromExtId, fromId);

        } while (page.size() >= config.getPageSize());

        return total;
    }

    private void publishPage(List<SiocsTransactionRow> transactions) {
        kafkaTemplate.executeInTransaction(ops -> {
            for (SiocsTransactionRow row : transactions) {
                try {
                    validateRow(row);
                    SimTransactionEvent event =
                            mapper.mapToEvent(row, config.getOrgId());
                    String payload =
                            objectMapper.writeValueAsString(event);
                    ops.send(topic, event.getTransactionKey(), payload)
                            .get(10, TimeUnit.SECONDS);
                    log.debug("Published simTxn key={}",
                            event.getTransactionKey());

                } catch (Exception e) {
                    log.error("Failed to publish row externalId={}: {}",
                            row.getExternalId(), e.getMessage());
                    sendToDlq(row, e.getMessage());
                }
            }
            return null;
        });
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
}
