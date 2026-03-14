package com.recon.flink.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.flink.domain.ReconEvent;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;

public class KafkaSummarySink {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static KafkaSink<ReconEvent> build(String bootstrapServers) {

        return KafkaSink.<ReconEvent>builder()
                .setBootstrapServers(bootstrapServers)
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)  // ADD: aligns with Flink checkpointing
                .setTransactionalIdPrefix("recon-summary-")             // ADD: required for exactly-once
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<ReconEvent>builder()
                                .setTopic("recon.summary")
                                .setKeySerializationSchema(event -> {
                                    if (event.getTransactionKey() == null)
                                        return null;
                                    return event.getTransactionKey().getBytes();
                                })
                                .setValueSerializationSchema(event -> {
                                    try {
                                        return MAPPER.writeValueAsBytes(event);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                                .build())
                .setKafkaProducerConfig(producerProps())
                .build();
    }

    private static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 10);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        // Required for exactly-once — must be > Flink checkpoint interval
        props.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "900000"); // 15 min
        return props;
    }
}