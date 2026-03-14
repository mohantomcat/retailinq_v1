package com.recon.poller.service;

import com.recon.poller.config.PollerConfig;
import com.recon.poller.domain.SiocsPollCheckpoint;
import com.recon.poller.domain.SiocsPollerActionResponse;
import com.recon.poller.domain.SiocsPollerStatusResponse;
import com.recon.poller.domain.SiocsResetCheckpointRequest;
import com.recon.poller.repository.CheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SiocsPollerAdminService {

    private static final String POLLER_ID = "siocs-main";

    private final PollerConfig config;
    private final CheckpointRepository checkpointRepository;
    private final SiocsKafkaPoller poller;

    public SiocsPollerStatusResponse getStatus() {
        SiocsPollCheckpoint cp = checkpointRepository.findOrCreate(POLLER_ID, config.getTenantId());
        return SiocsPollerStatusResponse.builder()
                .service("siocs-kafka-poller")
                .schedulerEnabled(config.isSchedulerEnabled())
                .pageSize(config.getPageSize())
                .safetyMarginMin(config.getSafetyMarginMin())
                .leaseTimeoutSeconds(config.getLeaseTimeoutSeconds())
                .pollerId(cp.getPollerId())
                .tenantId(cp.getTenantId())
                .lastProcessedTimestamp(toIso(cp.getLastProcessedTimestamp()))
                .lastProcessedExternalId(cp.getLastProcessedExternalId())
                .lastProcessedId(cp.getLastProcessedId())
                .lastPollStartedAt(toIso(cp.getLastPollStartedAt()))
                .lastPollCompletedAt(toIso(cp.getLastPollCompletedAt()))
                .lastPollStatus(cp.getLastPollStatus())
                .lastErrorMessage(cp.getLastErrorMessage())
                .totalRecordsPolled(cp.getTotalRecordsPolled())
                .lockOwner(cp.getLockOwner())
                .lockExpiresAt(toIso(cp.getLockExpiresAt()))
                .build();
    }

    public SiocsPollerActionResponse triggerPoll() {
        poller.runPollCycle();
        return SiocsPollerActionResponse.builder()
                .action("poll")
                .status("OK")
                .message("Triggered one poll cycle")
                .build();
    }

    public SiocsPollerActionResponse releaseLease() {
        checkpointRepository.forceReleaseLease(POLLER_ID);
        return SiocsPollerActionResponse.builder()
                .action("release-lease")
                .status("OK")
                .message("Released SIOCS poller lease")
                .build();
    }

    public SiocsPollerActionResponse resetCheckpoint(SiocsResetCheckpointRequest request) {
        Timestamp ts = request.getLastProcessedTimestamp() == null || request.getLastProcessedTimestamp().isBlank()
                ? Timestamp.from(Instant.EPOCH)
                : Timestamp.from(Instant.parse(request.getLastProcessedTimestamp()));
        String extId = request.getLastProcessedExternalId() == null
                ? ""
                : request.getLastProcessedExternalId();
        long lastProcessedId = request.getLastProcessedId() == null ? 0L : request.getLastProcessedId();
        checkpointRepository.findOrCreate(POLLER_ID, config.getTenantId());
        checkpointRepository.resetCheckpoint(POLLER_ID, ts, extId, lastProcessedId);
        return SiocsPollerActionResponse.builder()
                .action("reset-checkpoint")
                .status("OK")
                .message("Reset checkpoint for " + POLLER_ID)
                .build();
    }

    private String toIso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}
