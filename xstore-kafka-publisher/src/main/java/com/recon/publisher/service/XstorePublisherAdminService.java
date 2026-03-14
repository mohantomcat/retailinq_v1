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

    public XstorePublisherStatusResponse getStatus() {
        return XstorePublisherStatusResponse.builder()
                .service("xstore-kafka-publisher")
                .schedulerEnabled(config.isSchedulerEnabled())
                .batchSize(config.getBatchSize())
                .maxRetries(config.getMaxRetries())
                .processingLockTimeoutMinutes(config.getProcessingLockTimeoutMinutes())
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
                config.getProcessingLockTimeoutMinutes());
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
