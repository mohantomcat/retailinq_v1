package com.recon.xocs.service;

import com.recon.xocs.config.XocsConnectorProperties;
import com.recon.xocs.domain.XocsConnectorActionResponse;
import com.recon.xocs.domain.XocsConnectorStatusResponse;
import com.recon.xocs.domain.XocsIngestionCheckpoint;
import com.recon.xocs.domain.XocsReplayWindowRequest;
import com.recon.xocs.domain.XocsResetCheckpointRequest;
import com.recon.xocs.repository.XocsCheckpointRepository;
import com.recon.xocs.repository.XocsErrorRepository;
import com.recon.xocs.repository.XocsTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class XocsConnectorAdminService {

    private final XocsConnectorProperties properties;
    private final XocsCheckpointRepository checkpointRepository;
    private final XocsTransactionRepository transactionRepository;
    private final XocsErrorRepository errorRepository;
    private final XocsRestDownloadService downloadService;
    private final XocsIngestionPublisher publisher;

    public XocsConnectorStatusResponse getStatus() {
        XocsIngestionCheckpoint checkpoint = checkpointRepository.findOrCreate(
                properties.getConnectorName(),
                properties.getSourceName(),
                properties.getTenantId());

        return XocsConnectorStatusResponse.builder()
                .enabled(properties.isEnabled())
                .connectorName(checkpoint.getConnectorName())
                .sourceName(checkpoint.getSourceName())
                .tenantId(checkpoint.getTenantId())
                .checkpointStatus(checkpoint.getLastPollStatus() != null
                        ? checkpoint.getLastPollStatus()
                        : "UNKNOWN")
                .lastCursorId(checkpoint.getLastCursorId())
                .lastSuccessTimestamp(checkpoint.getLastSuccessTimestamp())
                .lastErrorMessage(checkpoint.getLastErrorMessage())
                .ingestionCounts(transactionRepository.countByStatus())
                .errorCount(errorRepository.countAll())
                .oldestReadyTimestamp(transactionRepository.oldestTimestampForStatus("READY"))
                .oldestFailedTimestamp(transactionRepository.oldestTimestampForStatus("FAILED"))
                .build();
    }

    public XocsConnectorActionResponse triggerDownload() {
        downloadService.runDownloadCycle();
        return action("download", "OK", "Download cycle completed");
    }

    public XocsConnectorActionResponse triggerPublish() {
        publisher.runPublishCycle();
        return action("publish", "OK", "Publish cycle completed");
    }

    public XocsConnectorActionResponse releaseStaleClaims() {
        int updated = transactionRepository.releaseStaleClaims(properties.getProcessingLockTimeoutMinutes());
        return action("release-stale-claims", "OK", "Released " + updated + " stale rows");
    }

    public XocsConnectorActionResponse requeueFailed() {
        int updated = transactionRepository.requeueStatus("FAILED");
        return action("requeue-failed", "OK", "Moved " + updated + " FAILED rows to READY");
    }

    public XocsConnectorActionResponse resetCheckpoint(XocsResetCheckpointRequest request) {
        checkpointRepository.reset(
                properties.getConnectorName(),
                request.getLastCursorId() == null ? 0L : request.getLastCursorId(),
                Timestamp.from(Instant.parse(request.getLastSuccessTimestamp())));
        return action("reset-checkpoint", "OK", "Checkpoint reset");
    }

    public XocsConnectorActionResponse replayWindow(XocsReplayWindowRequest request) {
        LocalDate fromDate = LocalDate.parse(request.getFromBusinessDate());
        LocalDate toDate = request.getToBusinessDate() == null || request.getToBusinessDate().isBlank()
                ? fromDate
                : LocalDate.parse(request.getToBusinessDate());
        Long rtlLocId = request.getStoreId() == null || request.getStoreId().isBlank()
                ? null
                : Long.parseLong(request.getStoreId());
        Long wkstnId = request.getWkstnId() == null || request.getWkstnId().isBlank()
                ? null
                : Long.parseLong(request.getWkstnId());
        int updated = transactionRepository.replayWindow(fromDate, toDate, rtlLocId, wkstnId);
        return action("replay-window", "OK", "Queued " + updated + " staged XOCS transactions for replay");
    }

    private XocsConnectorActionResponse action(String action, String status, String message) {
        return XocsConnectorActionResponse.builder()
                .action(action)
                .status(status)
                .message(message)
                .build();
    }
}
