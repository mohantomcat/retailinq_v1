package com.recon.poller.service;

import com.recon.poller.config.PollerConfig;
import com.recon.poller.domain.SiocsPollCheckpoint;
import com.recon.poller.domain.SiocsResetCheckpointRequest;
import com.recon.poller.repository.CheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiocsPollerAdminServiceTest {

    @Mock
    private CheckpointRepository checkpointRepository;
    @Mock
    private SiocsKafkaPoller poller;

    private SiocsPollerAdminService adminService;

    @BeforeEach
    void setUp() {
        PollerConfig config = new PollerConfig();
        config.setTenantId("tenant-india");
        config.setPageSize(500);
        config.setSafetyMarginMin(10);
        config.setLeaseTimeoutSeconds(900);
        config.setSchedulerEnabled(true);
        adminService = new SiocsPollerAdminService(config, checkpointRepository, poller);
    }

    @Test
    void getStatusReturnsCheckpointState() {
        SiocsPollCheckpoint cp = SiocsPollCheckpoint.builder()
                .pollerId("sim-main")
                .tenantId("tenant-india")
                .lastProcessedTimestamp(Timestamp.from(Instant.parse("2026-03-14T00:00:00Z")))
                .lastProcessedExternalId("0100700100008620251128")
                .lastProcessedId(42L)
                .lastPollStatus("SUCCESS")
                .totalRecordsPolled(100L)
                .build();
        when(checkpointRepository.findOrCreate("sim-main", "tenant-india")).thenReturn(cp);

        var status = adminService.getStatus();

        assertEquals("sim-kafka-poller", status.getService());
        assertEquals(42L, status.getLastProcessedId());
        assertEquals("SUCCESS", status.getLastPollStatus());
    }

    @Test
    void resetCheckpointResetsCompositeCursor() {
        when(checkpointRepository.findOrCreate("sim-main", "tenant-india"))
                .thenReturn(SiocsPollCheckpoint.builder().pollerId("sim-main").tenantId("tenant-india").build());

        adminService.resetCheckpoint(SiocsResetCheckpointRequest.builder()
                .lastProcessedTimestamp("2025-11-25T00:00:00Z")
                .lastProcessedExternalId("")
                .lastProcessedId(0L)
                .build());

        verify(checkpointRepository).resetCheckpoint(
                eq("sim-main"),
                any(Timestamp.class),
                eq(""),
                eq(0L));
    }
}
