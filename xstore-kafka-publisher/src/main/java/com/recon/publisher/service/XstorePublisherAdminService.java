package com.recon.publisher.service;

import com.recon.publisher.config.PublisherConfig;
import com.recon.publisher.domain.XstorePublisherActionResponse;
import com.recon.publisher.domain.XstorePublisherStatusResponse;
import com.recon.publisher.repository.PublishTrackerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
public class XstorePublisherAdminService {

    private final PublisherConfig config;
    private final PublishTrackerRepository trackerRepository;
    private final XstoreKafkaPublisher publisher;
    private final XstoreRuntimeConfigService runtimeConfigService;

    public XstorePublisherStatusResponse getStatus() {
        boolean schedulerEnabled = runtimeConfigService.getBoolean(
                "PUBLISHER_SCHEDULER_ENABLED",
                config.isSchedulerEnabled());
        int batchSize = runtimeConfigService.getInt(
                "PUBLISHER_BATCH_SIZE",
                config.getBatchSize());
        int maxRetries = runtimeConfigService.getInt(
                "PUBLISHER_MAX_RETRIES",
                config.getMaxRetries());
        int lockTimeoutMinutes = runtimeConfigService.getInt(
                "PUBLISHER_PROCESSING_LOCK_TIMEOUT_MINUTES",
                config.getProcessingLockTimeoutMinutes());
        return XstorePublisherStatusResponse.builder()
                .service("xstore-kafka-publisher")
                .schedulerEnabled(schedulerEnabled)
                .batchSize(batchSize)
                .maxRetries(maxRetries)
                .processingLockTimeoutMinutes(lockTimeoutMinutes)
                .statusCounts(trackerRepository.countByStatus())
                .oldestPendingAt(toIso(trackerRepository.oldestTimestampForStatus("PENDING")))
                .oldestFailedAt(toIso(trackerRepository.oldestTimestampForStatus("FAILED")))
                .oldestProcessingAt(toIso(trackerRepository.oldestTimestampForStatus("PROCESSING")))
                .build();
    }

    public XstorePublisherActionResponse triggerPublish() {
        publisher.publishPendingTransactions();
        return XstorePublisherActionResponse.builder()
                .action("publish")
                .status("OK")
                .message("Triggered one publish cycle")
                .build();
    }

    public XstorePublisherActionResponse releaseStaleClaims() {
        int released = trackerRepository.releaseStaleClaims(
                runtimeConfigService.getInt(
                        "PUBLISHER_PROCESSING_LOCK_TIMEOUT_MINUTES",
                        config.getProcessingLockTimeoutMinutes()));
        return XstorePublisherActionResponse.builder()
                .action("release-stale-claims")
                .status("OK")
                .message("Released " + released + " stale claim rows")
                .build();
    }

    private String toIso(Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }
}
