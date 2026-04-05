package com.recon.rms.service;

import com.recon.rms.config.PollerConfig;
import com.recon.rms.domain.RmsPollCheckpoint;
import com.recon.rms.domain.RmsPollerActionResponse;
import com.recon.rms.domain.RmsPollerStatusResponse;
import com.recon.rms.domain.RmsResetCheckpointRequest;
import com.recon.rms.repository.CheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RmsPollerAdminService {
    private final PollerConfig config;
    private final CheckpointRepository checkpointRepository;
    private final RmsKafkaPoller poller;

    public RmsPollerStatusResponse getStatus() {
        String pollerId = config.getPollerId();
        RmsPollCheckpoint cp = checkpointRepository.findOrCreate(pollerId, config.getTenantId());
        return RmsPollerStatusResponse.builder()
                .service("rms-db-connector")
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

    public RmsPollerActionResponse triggerPoll() {
        poller.runPollCycle();
        return RmsPollerActionResponse.builder()
                .action("poll")
                .status("OK")
                .message("Triggered one poll cycle")
                .build();
    }

    public RmsPollerActionResponse releaseLease() {
        checkpointRepository.forceReleaseLease(config.getPollerId());
        return RmsPollerActionResponse.builder()
                .action("release-lease")
                .status("OK")
                .message("Released RMS poller lease")
                .build();
    }

    public RmsPollerActionResponse resetCheckpoint(RmsResetCheckpointRequest request) {
        Timestamp ts = request.getLastProcessedTimestamp() == null || request.getLastProcessedTimestamp().isBlank()
                ? Timestamp.from(Instant.EPOCH)
                : Timestamp.from(Instant.parse(request.getLastProcessedTimestamp()));
        String extId = request.getLastProcessedExternalId() == null
                ? ""
                : request.getLastProcessedExternalId();
        long lastProcessedId = request.getLastProcessedId() == null ? 0L : request.getLastProcessedId();
        checkpointRepository.findOrCreate(config.getPollerId(), config.getTenantId());
        checkpointRepository.resetCheckpoint(config.getPollerId(), ts, extId, lastProcessedId);
        return RmsPollerActionResponse.builder()
                .action("reset-checkpoint")
                .status("OK")
                .message("Reset checkpoint for " + config.getPollerId())
                .build();
    }

    private String toIso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}

