package com.recon.cloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudApiPage;
import com.recon.cloud.domain.CloudApiTransaction;
import com.recon.cloud.domain.CloudIngestionCheckpoint;
import com.recon.cloud.repository.CloudCheckpointRepository;
import com.recon.cloud.repository.CloudErrorRepository;
import com.recon.cloud.repository.CloudRawRepository;
import com.recon.cloud.repository.CloudTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudRestDownloadServiceTest {

    @Mock
    private CloudCheckpointRepository checkpointRepository;
    @Mock
    private CloudRestClient restClient;
    @Mock
    private CloudRawRepository rawRepository;
    @Mock
    private CloudTransactionRepository transactionRepository;
    @Mock
    private CloudErrorRepository errorRepository;
    @Mock
    private CloudRuntimeConfigService runtimeConfigService;

    private CloudConnectorProperties properties;
    private CloudRestDownloadService service;

    @BeforeEach
    void setUp() {
        properties = new CloudConnectorProperties();
        properties.setEnabled(true);
        properties.setConnectorName("cloud-rest-main");
        properties.setSourceName("CLOUD_SIM");
        properties.setTenantId("tenant-india");
        properties.setBatchSize(100);
        properties.setOverlapMinutes(10);
        lenient().when(runtimeConfigService.getBoolean("CLOUD_CONNECTOR_ENABLED", true)).thenReturn(true);
        lenient().when(runtimeConfigService.getInt("CLOUD_OVERLAP_MINUTES", 10)).thenReturn(10);
        lenient().when(runtimeConfigService.getInt("CLOUD_BATCH_SIZE", 100)).thenReturn(100);
        service = new CloudRestDownloadService(
                properties,
                checkpointRepository,
                restClient,
                rawRepository,
                transactionRepository,
                errorRepository,
                new ObjectMapper().findAndRegisterModules(),
                runtimeConfigService);
    }

    @Test
    void runDownloadCyclePersistsAndAdvancesCheckpoint() {
        CloudApiTransaction record = CloudApiTransaction.builder()
                .id(1966L)
                .sourceRecordKey("src-1")
                .externalId("0100700100008620251128")
                .updateDateTime(Instant.parse("2025-11-28T14:18:44Z"))
                .build();

        when(checkpointRepository.findOrCreate("cloud-rest-main", "CLOUD_SIM", "tenant-india"))
                .thenReturn(CloudIngestionCheckpoint.builder()
                        .lastCursorId(0L)
                        .lastSuccessTimestamp(Timestamp.from(Instant.parse("2025-11-25T00:00:00Z")))
                        .build());
        when(restClient.fetchTransactions(any(), any(), eq(100)))
                .thenReturn(CloudApiPage.builder()
                        .records(List.of(record))
                        .hasMore(false)
                        .build());
        when(rawRepository.insert(eq("tenant-india"), eq("CLOUD_SIM"), eq("src-1"),
                eq("0"), any(), any())).thenReturn(1L);

        service.runDownloadCycle();

        verify(checkpointRepository).markStarted("cloud-rest-main");
        verify(rawRepository).insert(eq("tenant-india"), eq("CLOUD_SIM"), eq("src-1"),
                eq("0"), any(), any());
        verify(transactionRepository).upsertTransactionRows(
                eq("tenant-india"), eq("CLOUD_SIM"), eq("0"), eq(1L), any());
        verify(checkpointRepository).advance(
                eq("cloud-rest-main"),
                eq(1966L),
                eq(Timestamp.from(Instant.parse("2025-11-28T14:18:44Z"))));
    }

    @Test
    void persistPageRejectsInvalidRecord() {
        CloudApiTransaction invalid = CloudApiTransaction.builder()
                .sourceRecordKey("")
                .externalId("")
                .build();

        service.persistPage("", List.of(invalid));

        verify(rawRepository, never()).insert(any(), any(), any(), any(), any(), any());
        verify(transactionRepository, never()).upsertTransactionRows(any(), any(), any(), anyLong(), any());
        verify(errorRepository).save(
                eq("tenant-india"),
                eq("CLOUD_SIM"),
                eq(""),
                any(),
                eq("VALIDATION_ERROR"),
                eq("Missing sourceRecordKey")
        );
    }
}
