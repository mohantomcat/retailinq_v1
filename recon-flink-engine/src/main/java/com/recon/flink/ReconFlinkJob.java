package com.recon.flink;

import com.recon.integration.kafka.KafkaTopicCatalog;
import com.recon.integration.recon.TransactionDomain;
import com.recon.flink.deserializer.PosTransactionDeserializer;
import com.recon.flink.deserializer.SimTransactionDeserializer;
import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.FlatPosTransaction;
import com.recon.flink.domain.FlatSimTransaction;
import com.recon.flink.domain.ItemDiscrepancy;
import com.recon.flink.domain.ReconEvent;
import com.recon.flink.processor.InventoryToInventoryReconciliationProcessor;
import com.recon.flink.processor.ReconciliationProcessor;
import com.recon.flink.processor.PosToPosReconciliationProcessor;
import com.recon.flink.sink.ElasticsearchReconSink;
import com.recon.flink.sink.KafkaSummarySink;
import com.recon.poller.domain.SimTransactionEvent;
import com.recon.poller.domain.SiocsLineItem;
import com.recon.publisher.domain.LineItem;
import com.recon.publisher.domain.PosTransactionEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.time.Duration;
import java.util.ArrayList;

public class ReconFlinkJob {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "kafka:29092";
    private static final String DEFAULT_GROUP_ID = "recon-engine";

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment();

        EmbeddedRocksDBStateBackend rocksDB =
                new EmbeddedRocksDBStateBackend(true);
        env.setStateBackend(rocksDB);
        env.getCheckpointConfig().setCheckpointStorage(
                "file:///tmp/flink/checkpoints");

        env.enableCheckpointing(60_000, CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30_000);
        env.getCheckpointConfig().setCheckpointTimeout(120_000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);

        env.getConfig().registerPojoType(PosTransactionEvent.class);
        env.getConfig().registerPojoType(SimTransactionEvent.class);
        env.getConfig().registerPojoType(ReconEvent.class);
        env.getConfig().registerPojoType(LineItem.class);
        env.getConfig().registerPojoType(SiocsLineItem.class);
        env.getConfig().registerPojoType(ItemDiscrepancy.class);
        env.getConfig().registerKryoType(ArrayList.class);
        env.setParallelism(4);

        String bootstrapServers = env("RECON_KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS);
        String consumerGroupId = env("RECON_KAFKA_GROUP_ID", DEFAULT_GROUP_ID);

        KafkaSource<FlatPosTransaction> xstoreSource =
                KafkaSource.<FlatPosTransaction>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics(env(
                                "KAFKA_XSTORE_POS_TOPIC",
                                KafkaTopicCatalog.rawTransactionTopic("XSTORE", TransactionDomain.POS)))
                        .setGroupId(consumerGroupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new PosTransactionDeserializer())
                        .build();

        KafkaSource<FlatSimTransaction> simPosSource =
                KafkaSource.<FlatSimTransaction>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics(env(
                                "KAFKA_SIM_POS_TOPIC",
                                KafkaTopicCatalog.rawTransactionTopic("SIM", TransactionDomain.POS)))
                        .setGroupId(consumerGroupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new SimTransactionDeserializer())
                        .build();

        KafkaSource<FlatSimTransaction> simInventorySource =
                KafkaSource.<FlatSimTransaction>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics(env(
                                "KAFKA_SIM_INVENTORY_TOPIC",
                                KafkaTopicCatalog.rawTransactionTopic("SIM", TransactionDomain.INVENTORY)))
                        .setGroupId(consumerGroupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new SimTransactionDeserializer())
                        .build();

        KafkaSource<FlatSimTransaction> siocsPosSource =
                KafkaSource.<FlatSimTransaction>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics(env(
                                "KAFKA_SIOCS_POS_TOPIC",
                                KafkaTopicCatalog.rawTransactionTopic("SIOCS", TransactionDomain.POS)))
                        .setGroupId(consumerGroupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new SimTransactionDeserializer())
                        .build();

        KafkaSource<FlatSimTransaction> siocsInventorySource =
                KafkaSource.<FlatSimTransaction>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics(env(
                                "KAFKA_SIOCS_INVENTORY_TOPIC",
                                KafkaTopicCatalog.rawTransactionTopic("SIOCS", TransactionDomain.INVENTORY)))
                        .setGroupId(consumerGroupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new SimTransactionDeserializer())
                        .build();

        KafkaSource<FlatSimTransaction> mfcsSource =
                KafkaSource.<FlatSimTransaction>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics(env(
                                "KAFKA_MFCS_INVENTORY_TOPIC",
                                KafkaTopicCatalog.rawTransactionTopic("MFCS", TransactionDomain.INVENTORY)))
                        .setGroupId(consumerGroupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new SimTransactionDeserializer())
                        .build();

        KafkaSource<FlatSimTransaction> rmsSource =
                KafkaSource.<FlatSimTransaction>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics(env(
                                "KAFKA_RMS_INVENTORY_TOPIC",
                                KafkaTopicCatalog.rawTransactionTopic("RMS", TransactionDomain.INVENTORY)))
                        .setGroupId(consumerGroupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new SimTransactionDeserializer())
                        .build();

        KafkaSource<FlatPosTransaction> xocsSource =
                KafkaSource.<FlatPosTransaction>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics(env(
                                "KAFKA_XOCS_POS_TOPIC",
                                KafkaTopicCatalog.rawTransactionTopic("XOCS", TransactionDomain.POS)))
                        .setGroupId(consumerGroupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new PosTransactionDeserializer())
                        .build();

        DataStream<FlatPosTransaction> xstoreStream =
                env.fromSource(xstoreSource,
                        WatermarkStrategy
                                .<FlatPosTransaction>forBoundedOutOfOrderness(Duration.ofHours(2))
                                .withTimestampAssigner((event, ts) -> {
                                    long eventTs = parseTs(event.getBeginDatetime());
                                    long now = System.currentTimeMillis();
                                    return Math.min(eventTs, now + Duration.ofHours(1).toMillis());
                                })
                                .withIdleness(Duration.ofMinutes(10)),
                        "Xstore Source");

        DataStream<FlatSimTransaction> simPosStream =
                env.fromSource(simPosSource,
                        WatermarkStrategy
                                .<FlatSimTransaction>forBoundedOutOfOrderness(Duration.ofHours(8))
                                .withTimestampAssigner((event, ts) ->
                                        parseTs(event.getTransactionDateTime()))
                                .withIdleness(Duration.ofMinutes(10)),
                        "SIM POS Source");

        DataStream<FlatSimTransaction> simInventoryStream =
                env.fromSource(simInventorySource,
                        WatermarkStrategy
                                .<FlatSimTransaction>forBoundedOutOfOrderness(Duration.ofHours(8))
                                .withTimestampAssigner((event, ts) ->
                                        parseTs(event.getTransactionDateTime()))
                                .withIdleness(Duration.ofMinutes(10)),
                        "SIM Inventory Source");

        DataStream<FlatSimTransaction> siocsPosStream =
                env.fromSource(siocsPosSource,
                        WatermarkStrategy
                                .<FlatSimTransaction>forBoundedOutOfOrderness(Duration.ofHours(8))
                                .withTimestampAssigner((event, ts) ->
                                        parseTs(event.getTransactionDateTime()))
                                .withIdleness(Duration.ofMinutes(10)),
                        "SIOCS POS Source");

        DataStream<FlatSimTransaction> siocsInventoryStream =
                env.fromSource(siocsInventorySource,
                        WatermarkStrategy
                                .<FlatSimTransaction>forBoundedOutOfOrderness(Duration.ofHours(8))
                                .withTimestampAssigner((event, ts) ->
                                        parseTs(event.getTransactionDateTime()))
                                .withIdleness(Duration.ofMinutes(10)),
                        "SIOCS Inventory Source");

        DataStream<FlatSimTransaction> mfcsStream =
                env.fromSource(mfcsSource,
                        WatermarkStrategy
                                .<FlatSimTransaction>forBoundedOutOfOrderness(Duration.ofHours(8))
                                .withTimestampAssigner((event, ts) ->
                                        parseTs(event.getTransactionDateTime()))
                                .withIdleness(Duration.ofMinutes(10)),
                        "MFCS Source");

        DataStream<FlatSimTransaction> rmsStream =
                env.fromSource(rmsSource,
                        WatermarkStrategy
                                .<FlatSimTransaction>forBoundedOutOfOrderness(Duration.ofHours(8))
                                .withTimestampAssigner((event, ts) ->
                                        parseTs(event.getTransactionDateTime()))
                                .withIdleness(Duration.ofMinutes(10)),
                        "RMS Source");

        DataStream<FlatPosTransaction> xocsStream =
                env.fromSource(xocsSource,
                        WatermarkStrategy
                                .<FlatPosTransaction>forBoundedOutOfOrderness(Duration.ofHours(2))
                                .withTimestampAssigner((event, ts) -> {
                                    long eventTs = parseTs(event.getBeginDatetime());
                                    long now = System.currentTimeMillis();
                                    return Math.min(eventTs, now + Duration.ofHours(1).toMillis());
                                })
                                .withIdleness(Duration.ofMinutes(10)),
                        "XOCS Source");

        DataStream<ReconEvent> simReconStream = xstoreStream
                .keyBy(FlatPosTransaction::getTransactionKey)
                .connect(simPosStream.keyBy(FlatSimTransaction::getTransactionKey))
                .process(new ReconciliationProcessor("XSTORE_SIM", "SIOCS"))
                .name("Reconciliation Processor - Xstore vs SIM");

        DataStream<ReconEvent> siocsReconStream = xstoreStream
                .keyBy(FlatPosTransaction::getTransactionKey)
                .connect(siocsPosStream.keyBy(FlatSimTransaction::getTransactionKey))
                .process(new ReconciliationProcessor("XSTORE_SIOCS", "SIOCS"))
                .name("Reconciliation Processor - Xstore vs SIOCS");

        DataStream<ReconEvent> xocsReconStream = xstoreStream
                .keyBy(FlatPosTransaction::getTransactionKey)
                .connect(xocsStream.keyBy(FlatPosTransaction::getTransactionKey))
                .process(new PosToPosReconciliationProcessor("XSTORE_XOCS", "XOCS"))
                .name("Reconciliation Processor - Xstore vs XOCS");

        DataStream<ReconEvent> siocsMfcsReconStream = siocsInventoryStream
                .keyBy(FlatSimTransaction::getTransactionKey)
                .connect(mfcsStream.keyBy(FlatSimTransaction::getTransactionKey))
                .process(new InventoryToInventoryReconciliationProcessor("SIOCS_MFCS", "SIOCS", "MFCS"))
                .name("Reconciliation Processor - SIOCS vs MFCS");

        DataStream<ReconEvent> simRmsReconStream = simInventoryStream
                .keyBy(FlatSimTransaction::getTransactionKey)
                .connect(rmsStream.keyBy(FlatSimTransaction::getTransactionKey))
                .process(new InventoryToInventoryReconciliationProcessor("SIM_RMS", "SIM", "RMS"))
                .name("Reconciliation Processor - SIM vs RMS");

        DataStream<ReconEvent> simMfcsReconStream = simInventoryStream
                .keyBy(FlatSimTransaction::getTransactionKey)
                .connect(mfcsStream.keyBy(FlatSimTransaction::getTransactionKey))
                .process(new InventoryToInventoryReconciliationProcessor("SIM_MFCS", "SIM", "MFCS"))
                .name("Reconciliation Processor - SIM vs MFCS");

        DataStream<ReconEvent> siocsRmsReconStream = siocsInventoryStream
                .keyBy(FlatSimTransaction::getTransactionKey)
                .connect(rmsStream.keyBy(FlatSimTransaction::getTransactionKey))
                .process(new InventoryToInventoryReconciliationProcessor("SIOCS_RMS", "SIOCS", "RMS"))
                .name("Reconciliation Processor - SIOCS vs RMS");

        DataStream<ReconEvent> reconStream = simReconStream
                .union(siocsReconStream)
                .union(xocsReconStream)
                .union(siocsMfcsReconStream)
                .union(simRmsReconStream)
                .union(simMfcsReconStream)
                .union(siocsRmsReconStream);

        env.getConfig().registerPojoType(FlatPosTransaction.class);
        env.getConfig().registerPojoType(FlatSimTransaction.class);
        env.getConfig().registerPojoType(FlatLineItem.class);
        env.getConfig().registerPojoType(ReconEvent.class);
        env.getConfig().registerPojoType(ItemDiscrepancy.class);

        reconStream.sinkTo(KafkaSummarySink.build(bootstrapServers))
                .name("Kafka Summary Sink");

        reconStream.sinkTo(ElasticsearchReconSink.build("elasticsearch"))
                .name("Elasticsearch Sink");

        env.execute("Reconciliation Engine v1.0");
    }

    private static long parseTs(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return System.currentTimeMillis();
        }
        try {
            return java.time.Instant.parse(isoTimestamp).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String env(String key,
                              String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
