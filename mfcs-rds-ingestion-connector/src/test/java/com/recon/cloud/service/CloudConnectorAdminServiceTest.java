package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudConnectorStatusResponse;
import com.recon.cloud.domain.CloudIngestionCheckpoint;
import com.recon.cloud.repository.CloudCheckpointRepository;
import com.recon.cloud.repository.CloudErrorRepository;
import com.recon.cloud.repository.CloudTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudConnectorAdminServiceTest {

    @Mock
    private CloudCheckpointRepository checkpointRepository;
    @Mock
    private CloudTransactionRepository transactionRepository;
    @Mock
    private CloudErrorRepository errorRepository;
    @Mock
    private CloudRestDownloadService downloadService;
    @Mock
    private CloudIngestionPublisher publisher;

    private CloudConnectorProperties properties;

    @InjectMocks
    private CloudConnectorAdminService service;

    @BeforeEach
    void setUp() {
        properties = new CloudConnectorProperties();
        properties.setEnabled(true);
        properties.setConnectorName("cloud-rest-main");
        properties.setSourceName("MFCS_SIM");
        properties.setTenantId("tenant-india");
        properties.setProcessingLockTimeoutMinutes(15);
        service = new CloudConnectorAdminService(
                properties,
                checkpointRepository,
                transactionRepository,
                errorRepository,
                downloadService,
                publisher);
    }

    @Test
    void getStatusBuildsOperatorView() {
        Timestamp now = Timestamp.from(Instant.parse("2026-03-13T12:00:00Z"));
        when(checkpointRepository.findOrCreate("cloud-rest-main", "MFCS_SIM", "tenant-india"))
                .thenReturn(CloudIngestionCheckpoint.builder()
                        .connectorName("cloud-rest-main")
                        .sourceName("MFCS_SIM")
                        .tenantId("tenant-india")
                        .lastCursorId(1966L)
                        .lastStatus("SUCCESS")
                        .lastSuccessTimestamp(now)
                        .lastPolledTimestamp(now)
                        .build());
        when(transactionRepository.countByStatus()).thenReturn(
                Map.of("READY", 5L, "FAILED", 2L));
        when(transactionRepository.oldestTimestampForStatus("READY")).thenReturn(now);
        when(transactionRepository.oldestTimestampForStatus("FAILED")).thenReturn(now);
        when(errorRepository.countAll()).thenReturn(3L);

        CloudConnectorStatusResponse status = service.getStatus();

        assertTrue(status.isEnabled());
        assertEquals("SUCCESS", status.getCheckpointStatus());
        assertEquals(5L, status.getIngestionCounts().get("READY"));
        assertEquals(3L, status.getErrorCount());
    }

    @Test
    void resetCheckpointDelegatesToRepository() {
        Instant checkpointTime = Instant.parse("2025-11-25T00:00:00Z");

        service.resetCheckpoint(checkpointTime, 1966L);

        verify(checkpointRepository).reset(
                "cloud-rest-main",
                1966L,
                Timestamp.from(checkpointTime));
    }

    @Test
    void manualActionsDelegateToServices() {
        service.triggerDownload();
        service.triggerPublish();
        service.releaseStaleClaims();
        service.requeueFailed();
        service.requeueDlq();

        verify(downloadService).runDownloadCycle();
        verify(publisher).runPublishCycle();
        verify(transactionRepository).releaseStaleClaims(15);
        verify(transactionRepository).requeueStatus("FAILED");
        verify(transactionRepository).requeueStatus("DLQ");
    }
}
