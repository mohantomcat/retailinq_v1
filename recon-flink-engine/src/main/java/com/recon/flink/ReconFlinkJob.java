package com.recon.flink;

import com.recon.flink.deserializer.PosTransactionDeserializer;
import com.recon.flink.deserializer.SimTransactionDeserializer;
import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.FlatPosTransaction;
import com.recon.flink.domain.FlatSimTransaction;
import com.recon.flink.domain.ItemDiscrepancy;
import com.recon.flink.domain.ReconEvent;
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

        KafkaSource<FlatPosTransaction> xstoreSource =
                KafkaSource.<FlatPosTransaction>builder()
                        .setBootstrapServers("kafka:29092")
                        .setTopics("pos.transactions.raw")
                        .setGroupId("recon-engine")
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new PosTransactionDeserializer())
                        .build();

        KafkaSource<FlatSimTransaction> siocsSource =
                KafkaSource.<FlatSimTransaction>builder()
                        .setBootstrapServers("kafka:29092")
                        .setTopics("sim.transactions.raw", "siocs.transactions.raw")
                        .setGroupId("recon-engine")
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new SimTransactionDeserializer())
                        .build();

        KafkaSource<FlatPosTransaction> xocsSource =
                KafkaSource.<FlatPosTransaction>builder()
                        .setBootstrapServers("kafka:29092")
                        .setTopics("xocs.transactions.raw")
                        .setGroupId("recon-engine")
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

        DataStream<FlatSimTransaction> siocsStream =
                env.fromSource(siocsSource,
                        WatermarkStrategy
                                .<FlatSimTransaction>forBoundedOutOfOrderness(Duration.ofHours(8))
                                .withTimestampAssigner((event, ts) ->
                                        parseTs(event.getTransactionDateTime()))
                                .withIdleness(Duration.ofMinutes(10)),
                        "SIOCS Source");

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

        DataStream<FlatSimTransaction> simDbStream = siocsStream
                .filter(event -> "SIOCS".equalsIgnoreCase(event.getSource()))
                .name("SIM DB Source");

        DataStream<FlatSimTransaction> siocsCloudStream = siocsStream
                .filter(event -> event.getSource() != null
                        && !"SIOCS".equalsIgnoreCase(event.getSource()))
                .name("SIOCS Cloud Source");

        DataStream<ReconEvent> simReconStream = xstoreStream
                .keyBy(FlatPosTransaction::getTransactionKey)
                .connect(simDbStream.keyBy(FlatSimTransaction::getTransactionKey))
                .process(new ReconciliationProcessor("XSTORE_SIM", "SIOCS"))
                .name("Reconciliation Processor - Xstore vs SIM");

        DataStream<ReconEvent> siocsReconStream = xstoreStream
                .keyBy(FlatPosTransaction::getTransactionKey)
                .connect(siocsCloudStream.keyBy(FlatSimTransaction::getTransactionKey))
                .process(new ReconciliationProcessor("XSTORE_SIOCS", "CLOUD_SIM"))
                .name("Reconciliation Processor - Xstore vs SIOCS");

        DataStream<ReconEvent> xocsReconStream = xstoreStream
                .keyBy(FlatPosTransaction::getTransactionKey)
                .connect(xocsStream.keyBy(FlatPosTransaction::getTransactionKey))
                .process(new PosToPosReconciliationProcessor("XSTORE_XOCS", "XOCS"))
                .name("Reconciliation Processor - Xstore vs XOCS");

        DataStream<ReconEvent> reconStream = simReconStream
                .union(siocsReconStream)
                .union(xocsReconStream);

        env.getConfig().registerPojoType(FlatPosTransaction.class);
        env.getConfig().registerPojoType(FlatSimTransaction.class);
        env.getConfig().registerPojoType(FlatLineItem.class);
        env.getConfig().registerPojoType(ReconEvent.class);
        env.getConfig().registerPojoType(ItemDiscrepancy.class);

        reconStream.sinkTo(KafkaSummarySink.build("kafka:29092"))
                .name("Kafka Summary Sink");

        reconStream.sinkTo(ElasticsearchReconSink.build("elasticsearch"))
                .name("Elasticsearch Sink");

        env.execute("Xstore-SIOCS Reconciliation Engine v1.0");
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
}
