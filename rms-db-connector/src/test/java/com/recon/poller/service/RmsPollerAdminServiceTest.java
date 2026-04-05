package com.recon.rms.service;

import com.recon.rms.config.PollerConfig;
import com.recon.rms.domain.RmsPollCheckpoint;
import com.recon.rms.domain.RmsResetCheckpointRequest;
import com.recon.rms.repository.CheckpointRepository;
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
class RmsPollerAdminServiceTest {

    @Mock
    private CheckpointRepository checkpointRepository;
    @Mock
    private RmsKafkaPoller poller;

    private RmsPollerAdminService adminService;

    @BeforeEach
    void setUp() {
        PollerConfig config = new PollerConfig();
        config.setTenantId("tenant-india");
        config.setPageSize(500);
        config.setSafetyMarginMin(10);
        config.setLeaseTimeoutSeconds(900);
        config.setSchedulerEnabled(true);
        adminService = new RmsPollerAdminService(config, checkpointRepository, poller);
    }

    @Test
    void getStatusReturnsCheckpointState() {
        RmsPollCheckpoint cp = RmsPollCheckpoint.builder()
                .pollerId("rms-main")
                .tenantId("tenant-india")
                .lastProcessedTimestamp(Timestamp.from(Instant.parse("2026-03-14T00:00:00Z")))
                .lastProcessedExternalId("0100700100008620251128")
                .lastProcessedId(42L)
                .lastPollStatus("SUCCESS")
                .totalRecordsPolled(100L)
                .build();
        when(checkpointRepository.findOrCreate("rms-main", "tenant-india")).thenReturn(cp);

        var status = adminService.getStatus();

        assertEquals("rms-db-connector", status.getService());
        assertEquals(42L, status.getLastProcessedId());
        assertEquals("SUCCESS", status.getLastPollStatus());
    }

    @Test
    void resetCheckpointResetsCompositeCursor() {
        when(checkpointRepository.findOrCreate("rms-main", "tenant-india"))
                .thenReturn(RmsPollCheckpoint.builder().pollerId("rms-main").tenantId("tenant-india").build());

        adminService.resetCheckpoint(RmsResetCheckpointRequest.builder()
                .lastProcessedTimestamp("2025-11-25T00:00:00Z")
                .lastProcessedExternalId("")
                .lastProcessedId(0L)
                .build());

        verify(checkpointRepository).resetCheckpoint(
                eq("rms-main"),
                any(Timestamp.class),
                eq(""),
                eq(0L));
    }
}

