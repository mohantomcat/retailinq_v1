package com.recon.poller.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.poller.aggregator.SiocsRowAggregator;
import com.recon.poller.config.PollerConfig;
import com.recon.poller.domain.AggregationResult;
import com.recon.poller.domain.SimTransactionEvent;
import com.recon.poller.domain.SiocsPollCheckpoint;
import com.recon.poller.domain.SiocsTransactionRow;
import com.recon.poller.domain.SiocsRawRow;
import com.recon.poller.mapper.SiocsTransactionMapper;
import com.recon.poller.repository.CheckpointRepository;
import com.recon.poller.repository.IntegrationRunJournalRepository;
import com.recon.poller.repository.SiocsRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private KafkaOperations<String, String> kafkaOperations;
    @Mock
    private PollerRuntimeConfigService runtimeConfigService;
    @Mock
    private IntegrationEnvelopeMapper integrationEnvelopeMapper;
    @Mock
    private SiocsIntegrationContract integrationContract;
    @Mock
    private IntegrationRunJournalRepository integrationRunJournalRepository;

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
                integrationEnvelopeMapper,
                integrationContract,
                integrationRunJournalRepository,
                kafkaTemplate,
                config,
                new ObjectMapper(),
                runtimeConfigService);
        lenient().when(runtimeConfigService.getBoolean("SIOCS_POLLER_SCHEDULER_ENABLED", true)).thenReturn(true);
        lenient().when(runtimeConfigService.getInt("SIOCS_POLLER_LEASE_TIMEOUT_SECONDS", 900)).thenReturn(900);
        lenient().when(runtimeConfigService.getInt("SIOCS_POLLER_SAFETY_MARGIN_MIN", 10)).thenReturn(10);
        lenient().when(runtimeConfigService.getInt("SIOCS_POLLER_PAGE_SIZE", 2)).thenReturn(2);
        lenient().when(integrationRunJournalRepository.startRun(anyString(), any(), anyString())).thenReturn(UUID.randomUUID());
        lenient().when(integrationRunJournalRepository.startStep(any(), anyString(), anyString(), anyInt())).thenReturn(UUID.randomUUID());
        ReflectionTestUtils.setField(poller, "topic", "sim.transactions.raw");
        ReflectionTestUtils.setField(poller, "integrationCanonicalTopic", "integration.canonical.transactions");
        ReflectionTestUtils.setField(poller, "dlqTopic", "recon.dlq");
    }

    @Test
    void runPollCycleSkipsWhenLeaseNotAcquired() {
        SiocsPollCheckpoint cp = SiocsPollCheckpoint.builder()
                .pollerId("sim-main")
                .tenantId("tenant-india")
                .lastProcessedTimestamp(Timestamp.from(Instant.now()))
                .lastProcessedExternalId("")
                .lastProcessedId(0L)
                .build();
        when(checkpointRepository.findOrCreate("sim-main", "tenant-india"))
                .thenReturn(cp);
        when(checkpointRepository.tryAcquireLease(
                ArgumentMatchers.eq("sim-main"),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(900)))
                .thenReturn(false);

        poller.runPollCycle();

        verify(siocsRepository, never()).findRawRows(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt());
        verify(checkpointRepository, never()).markStarted("sim-main");
    }

    @Test
    void runPollCycleAdvancesCheckpointWithLastProcessedId() throws Exception {
        Timestamp checkpointTs = Timestamp.from(Instant.parse("2026-03-14T00:00:00Z"));
        Timestamp rowTs = Timestamp.from(Instant.parse("2026-03-14T00:05:00Z"));
        SiocsPollCheckpoint cp = SiocsPollCheckpoint.builder()
                .pollerId("sim-main")
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

        when(checkpointRepository.findOrCreate("sim-main", "tenant-india"))
                .thenReturn(cp);
        when(checkpointRepository.tryAcquireLease(
                ArgumentMatchers.eq("sim-main"),
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
                "sim-main",
                rowTs,
                "0100700100008620251128",
                42L);
        verify(checkpointRepository).markCompleted("sim-main", 0);
        verify(checkpointRepository).releaseLease(
                ArgumentMatchers.eq("sim-main"),
                ArgumentMatchers.anyString());
    }

    @Test
    void runPollCyclePublishesAndJournalsStatusMessages() throws Exception {
        Timestamp checkpointTs = Timestamp.from(Instant.parse("2026-03-14T00:00:00Z"));
        Timestamp rowTs = Timestamp.from(Instant.parse("2026-03-14T00:05:00Z"));
        SiocsPollCheckpoint cp = SiocsPollCheckpoint.builder()
                .pollerId("sim-main")
                .tenantId("tenant-india")
                .lastProcessedTimestamp(checkpointTs)
                .lastProcessedExternalId("")
                .lastProcessedId(0L)
                .build();
        SiocsRawRow rawRow = SiocsRawRow.builder()
                .id(42L)
                .externalId("0100700100008620251128")
                .updateDateTime(rowTs)
                .build();
        SiocsTransactionRow transactionRow = SiocsTransactionRow.builder()
                .externalId("0100700100008620251128")
                .storeId("1007")
                .requestId(999L)
                .transactionDateTime(rowTs)
                .updateDateTime(rowTs)
                .type(1)
                .processingStatus(1)
                .lineItems(List.of())
                .lineItemCount(0)
                .build();
        SimTransactionEvent event = SimTransactionEvent.builder()
                .eventId("sim-event-1")
                .eventType("SIM_TRANSACTION")
                .source("SIOCS")
                .publishedAt("2026-03-14T00:06:00Z")
                .tenantId("tenant-india")
                .tenantTimezone("UTC")
                .externalId("0100700100008620251128")
                .transactionKey("1|0100700100008620251128")
                .requestId(999L)
                .storeId("1007")
                .businessDate("2026-03-14")
                .transactionDateTime("2026-03-14T00:05:00Z")
                .updateDateTime("2026-03-14T00:05:00Z")
                .transactionType(1)
                .processingStatus(1)
                .checksum("chk-1")
                .build();

        when(checkpointRepository.findOrCreate("sim-main", "tenant-india")).thenReturn(cp);
        when(checkpointRepository.tryAcquireLease(
                ArgumentMatchers.eq("sim-main"),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(900)))
                .thenReturn(true);
        when(siocsRepository.findRawRows(
                ArgumentMatchers.any(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.eq(2)))
                .thenReturn(List.of(rawRow))
                .thenReturn(List.of());
        when(aggregator.aggregate(List.of(rawRow), 2))
                .thenReturn(AggregationResult.builder().transactions(List.of(transactionRow)).build());
        when(mapper.mapToEvent(transactionRow, 1L)).thenReturn(event);
        when(integrationEnvelopeMapper.map(any(), any()))
                .thenReturn(CanonicalIntegrationEnvelope.builder()
                        .messageId("msg-1")
                        .traceId("sim-event-1")
                        .businessKey("1|0100700100008620251128")
                        .documentId("0100700100008620251128")
                        .messageType("TARGET_STATUS_POLL")
                        .sourceSystem("SIOCS")
                        .targetSystem("RECON")
                        .payloadVersion("1.0")
                        .schemaKey("sim-transaction-event")
                        .status("PUBLISHED")
                        .build());

        when(kafkaTemplate.executeInTransaction(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var callback = (org.springframework.kafka.core.KafkaOperations.OperationsCallback<String, String, Object>) invocation.getArgument(0);
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("sim.transactions.raw", 0),
                    0L, 10L, System.currentTimeMillis(), 0L, 0, 0);
            CompletableFuture<SendResult<String, String>> future =
                    CompletableFuture.completedFuture(new SendResult<>(null, metadata));
            when(kafkaOperations.send(
                    ArgumentMatchers.eq("sim.transactions.raw"),
                    ArgumentMatchers.eq("1|0100700100008620251128"),
                    anyString())).thenReturn(future);
            return callback.doInOperations(kafkaOperations);
        });
        RecordMetadata canonicalMetadata = new RecordMetadata(
                new TopicPartition("integration.canonical.transactions", 1),
                0L, 11L, System.currentTimeMillis(), 0L, 0, 0);
        CompletableFuture<SendResult<String, String>> canonicalFuture =
                CompletableFuture.completedFuture(new SendResult<>(null, canonicalMetadata));
        when(kafkaTemplate.send(
                ArgumentMatchers.eq("integration.canonical.transactions"),
                ArgumentMatchers.eq("1|0100700100008620251128"),
                anyString())).thenReturn(canonicalFuture);

        poller.runPollCycle();

        verify(checkpointRepository).markCompleted("sim-main", 1);
        verify(integrationRunJournalRepository).recordPublishedMessage(
                ArgumentMatchers.eq("tenant-india"),
                any(),
                any(),
                any(),
                anyString());
    }
}
