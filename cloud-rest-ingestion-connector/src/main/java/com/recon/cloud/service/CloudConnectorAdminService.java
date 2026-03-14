package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudConnectorActionResponse;
import com.recon.cloud.domain.CloudConnectorStatusResponse;
import com.recon.cloud.domain.CloudIngestionCheckpoint;
import com.recon.cloud.repository.CloudCheckpointRepository;
import com.recon.cloud.repository.CloudErrorRepository;
import com.recon.cloud.repository.CloudTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CloudConnectorAdminService {

    private final CloudConnectorProperties properties;
    private final CloudCheckpointRepository checkpointRepository;
    private final CloudTransactionRepository transactionRepository;
    private final CloudErrorRepository errorRepository;
    private final CloudRestDownloadService downloadService;
    private final CloudIngestionPublisher publisher;

    public CloudConnectorStatusResponse getStatus() {
        CloudIngestionCheckpoint checkpoint = checkpointRepository.findOrCreate(
                properties.getConnectorName(),
                properties.getSourceName(),
                properties.getTenantId());

        return CloudConnectorStatusResponse.builder()
                .enabled(properties.isEnabled())
                .connectorName(checkpoint.getConnectorName())
                .sourceName(checkpoint.getSourceName())
                .tenantId(checkpoint.getTenantId())
                .checkpointStatus(checkpoint.getLastStatus())
                .lastCursorId(checkpoint.getLastCursorId())
                .lastSuccessTimestamp(checkpoint.getLastSuccessTimestamp())
                .lastPolledTimestamp(checkpoint.getLastPolledTimestamp())
                .lastErrorMessage(checkpoint.getLastErrorMessage())
                .ingestionCounts(transactionRepository.countByStatus())
                .errorCount(errorRepository.countAll())
                .oldestReadyTimestamp(transactionRepository.oldestTimestampForStatus("READY"))
                .oldestFailedTimestamp(transactionRepository.oldestTimestampForStatus("FAILED"))
                .build();
    }

    public CloudConnectorActionResponse triggerDownload() {
        downloadService.runDownloadCycle();
        return action("download", "OK", "Download cycle completed");
    }

    public CloudConnectorActionResponse triggerPublish() {
        publisher.runPublishCycle();
        return action("publish", "OK", "Publish cycle completed");
    }

    public CloudConnectorActionResponse resetCheckpoint(Instant lastSuccessTimestamp,
                                                        Long lastCursorId) {
        checkpointRepository.reset(
                properties.getConnectorName(),
                lastCursorId == null ? 0L : lastCursorId,
                Timestamp.from(lastSuccessTimestamp));
        return action("resetCheckpoint", "OK", "Checkpoint reset");
    }

    public CloudConnectorActionResponse releaseStaleClaims() {
        int updated = transactionRepository.releaseStaleClaims(
                properties.getProcessingLockTimeoutMinutes());
        return action("releaseStaleClaims", "OK",
                "Released " + updated + " stale rows");
    }

    public CloudConnectorActionResponse requeueFailed() {
        int updated = transactionRepository.requeueStatus("FAILED");
        return action("requeueFailed", "OK",
                "Moved " + updated + " FAILED rows to READY");
    }

    public CloudConnectorActionResponse requeueDlq() {
        int updated = transactionRepository.requeueStatus("DLQ");
        return action("requeueDlq", "OK",
                "Moved " + updated + " DLQ rows to READY");
    }

    private CloudConnectorActionResponse action(String action,
                                                String status,
                                                String message) {
        return CloudConnectorActionResponse.builder()
                .action(action)
                .status(status)
                .message(message)
                .build();
    }
}
