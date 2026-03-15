package com.recon.publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.publisher.config.PublisherConfig;
import com.recon.publisher.domain.ParseResult;
import com.recon.publisher.domain.PosTransactionEvent;
import com.recon.publisher.domain.PoslogRecord;
import com.recon.publisher.parser.PoslogStaxParser;
import com.recon.publisher.repository.PublishTrackerRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XstoreKafkaPublisherTest {

    @Mock
    private PublishTrackerRepository trackerRepository;
    @Mock
    private PoslogStaxParser parser;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private KafkaOperations<String, String> kafkaOperations;
    @Mock
    private XstoreRuntimeConfigService runtimeConfigService;

    private XstoreKafkaPublisher publisher;
    private PublisherConfig config;

    @BeforeEach
    void setUp() {
        config = new PublisherConfig();
        config.setOrgId(1L);
        config.setBatchSize(10);
        config.setMaxRetries(5);
        config.setProcessingLockTimeoutMinutes(15);
        config.setSchedulerEnabled(true);

        publisher = new XstoreKafkaPublisher(
                trackerRepository,
                parser,
                kafkaTemplate,
                config,
                new ObjectMapper(),
                runtimeConfigService);
        when(runtimeConfigService.getBoolean("PUBLISHER_SCHEDULER_ENABLED", true)).thenReturn(true);
        when(runtimeConfigService.getInt("PUBLISHER_PROCESSING_LOCK_TIMEOUT_MINUTES", 15)).thenReturn(15);
        when(runtimeConfigService.getInt("PUBLISHER_BATCH_SIZE", 10)).thenReturn(10);
        when(runtimeConfigService.getInt("PUBLISHER_MAX_RETRIES", 5)).thenReturn(5);
        ReflectionTestUtils.setField(publisher, "topic", "pos.transactions.raw");
        ReflectionTestUtils.setField(publisher, "dlqTopic", "recon.dlq");
    }

    @Test
    void publishPendingTransactionsSkipsWhenSchedulerDisabled() {
        config.setSchedulerEnabled(false);

        publisher.publishPendingTransactions();

        verify(trackerRepository, never()).seedPendingRows(anyInt());
    }

    @Test
    void publishPendingTransactionsClaimsAndPublishes() {
        PoslogRecord record = PoslogRecord.builder()
                .organizationId(1L)
                .rtlLocId(1007L)
                .businessDate(LocalDate.parse("2025-11-28"))
                .wkstnId(1L)
                .transSeq(86L)
                .poslogBytes(new byte[]{1, 2, 3})
                .build();
        PosTransactionEvent event = PosTransactionEvent.builder()
                .organizationId(1L)
                .externalId("0100700100008620251128")
                .transactionKey("1|0100700100008620251128")
                .transactionType("RETAIL_SALE")
                .checksum("abc123")
                .build();

        when(trackerRepository.releaseStaleClaims(15)).thenReturn(0);
        when(trackerRepository.claimBatch(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(record));
        when(trackerRepository.find(record)).thenReturn(null);
        when(parser.parse(1L, 1007L, LocalDate.parse("2025-11-28"), 1L, 86L, record.getPoslogBytes()))
                .thenReturn(ParseResult.success(event, false));
        when(kafkaTemplate.executeInTransaction(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var callback = (org.springframework.kafka.core.KafkaOperations.OperationsCallback<String, String, Object>) invocation.getArgument(0);
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("pos.transactions.raw", 0),
                    0L, 10L, System.currentTimeMillis(), 0L, 0, 0);
            CompletableFuture<SendResult<String, String>> future =
                    CompletableFuture.completedFuture(new SendResult<>(null, metadata));
            when(kafkaOperations.send(ArgumentMatchers.eq("pos.transactions.raw"),
                    ArgumentMatchers.eq("1|0100700100008620251128"),
                    anyString())).thenReturn(future);
            return callback.doInOperations(kafkaOperations);
        });

        publisher.publishPendingTransactions();

        verify(trackerRepository).seedPendingRows(10);
        verify(trackerRepository).claimBatch(anyString(), ArgumentMatchers.eq(10), ArgumentMatchers.eq(5), ArgumentMatchers.eq(15));
        verify(trackerRepository).markPublished(
                ArgumentMatchers.eq(record),
                ArgumentMatchers.eq("0100700100008620251128"),
                ArgumentMatchers.eq("1|0100700100008620251128"),
                ArgumentMatchers.eq("RETAIL_SALE"),
                ArgumentMatchers.eq(0),
                ArgumentMatchers.eq(10L),
                ArgumentMatchers.eq("abc123"),
                ArgumentMatchers.eq(false));
    }
}
