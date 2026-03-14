package com.recon.poller.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.poller.aggregator.SiocsRowAggregator;
import com.recon.poller.config.PollerConfig;
import com.recon.poller.domain.AggregationResult;
import com.recon.poller.domain.SiocsPollCheckpoint;
import com.recon.poller.domain.SiocsRawRow;
import com.recon.poller.mapper.SiocsTransactionMapper;
import com.recon.poller.repository.CheckpointRepository;
import com.recon.poller.repository.SiocsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.kafka.core.KafkaTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiocsKafkaPollerTest {

    @Mock
    private SiocsRepository siocsRepository;
    @Mock
    private CheckpointRepository checkpointRepository;
    @Mock
    private SiocsRowAggregator aggregator;
    @Mock
    private SiocsTransactionMapper mapper;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private SiocsKafkaPoller poller;
    private PollerConfig config;

    @BeforeEach
    void setUp() {
        config = new PollerConfig();
        config.setTenantId("tenant-india");
        config.setTenantTimezone("UTC");
        config.setOrgId(1L);
        config.setPageSize(2);
        config.setSafetyMarginMin(10);
        config.setLeaseTimeoutSeconds(900);
        config.setSchedulerEnabled(true);

        poller = new SiocsKafkaPoller(
                siocsRepository,
                checkpointRepository,
                aggregator,
                mapper,
                kafkaTemplate,
                config,
                new ObjectMapper());
        ReflectionTestUtils.setField(poller, "topic", "sim.transactions.raw");
        ReflectionTestUtils.setField(poller, "dlqTopic", "recon.dlq");
    }

    @Test
    void runPollCycleSkipsWhenLeaseNotAcquired() {
        SiocsPollCheckpoint cp = SiocsPollCheckpoint.builder()
                .pollerId("siocs-main")
                .tenantId("tenant-india")
                .lastProcessedTimestamp(Timestamp.from(Instant.now()))
                .lastProcessedExternalId("")
                .lastProcessedId(0L)
                .build();
        when(checkpointRepository.findOrCreate("siocs-main", "tenant-india"))
                .thenReturn(cp);
        when(checkpointRepository.tryAcquireLease(
                ArgumentMatchers.eq("siocs-main"),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(900)))
                .thenReturn(false);

        poller.runPollCycle();

        verify(siocsRepository, never()).findRawRows(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt());
        verify(checkpointRepository, never()).markStarted("siocs-main");
    }

    @Test
    void runPollCycleAdvancesCheckpointWithLastProcessedId() throws Exception {
        Timestamp checkpointTs = Timestamp.from(Instant.parse("2026-03-14T00:00:00Z"));
        Timestamp rowTs = Timestamp.from(Instant.parse("2026-03-14T00:05:00Z"));
        SiocsPollCheckpoint cp = SiocsPollCheckpoint.builder()
                .pollerId("siocs-main")
                .tenantId("tenant-india")
                .lastProcessedTimestamp(checkpointTs)
                .lastProcessedExternalId("0000000000000000000000")
                .lastProcessedId(10L)
                .build();
        SiocsRawRow row = SiocsRawRow.builder()
                .id(42L)
                .externalId("0100700100008620251128")
                .updateDateTime(rowTs)
                .build();

        when(checkpointRepository.findOrCreate("siocs-main", "tenant-india"))
                .thenReturn(cp);
        when(checkpointRepository.tryAcquireLease(
                ArgumentMatchers.eq("siocs-main"),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(900)))
                .thenReturn(true);
        when(siocsRepository.findRawRows(
                ArgumentMatchers.any(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.eq(2)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());
        when(aggregator.aggregate(List.of(row), 2))
                .thenReturn(AggregationResult.empty());

        poller.runPollCycle();

        verify(checkpointRepository).updateComposite(
                "siocs-main",
                rowTs,
                "0100700100008620251128",
                42L);
        verify(checkpointRepository).markCompleted("siocs-main", 0);
        verify(checkpointRepository).releaseLease(
                ArgumentMatchers.eq("siocs-main"),
                ArgumentMatchers.anyString());
    }
}
