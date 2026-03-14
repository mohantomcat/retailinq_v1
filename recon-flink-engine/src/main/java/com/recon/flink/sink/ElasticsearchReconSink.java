package com.recon.flink.sink;

import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.flink.domain.ReconEvent;
import org.apache.flink.connector.elasticsearch.sink.Elasticsearch8AsyncSink;
import org.apache.flink.connector.elasticsearch.sink.Elasticsearch8AsyncSinkBuilder;
import org.apache.http.HttpHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ElasticsearchReconSink {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchReconSink.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Elasticsearch8AsyncSink<ReconEvent> build(String esHost) {

        return new Elasticsearch8AsyncSinkBuilder<ReconEvent>()
                .setHosts(new HttpHost(esHost, 9200, "http"))
                .setElementConverter((event, ctx) -> {
                    try {
                        String docId = event.getReconView() + "|" + event.getTransactionKey();

                        Map<String, Object> doc = MAPPER.convertValue(event, Map.class);

                        return new IndexOperation.Builder<Map<String, Object>>()
                                .index("recon-transactions")
                                .id(docId)
                                .document(doc)
                                .build();
                    } catch (Exception e) {
                        LOG.error("ES serialization failed for key: {}",
                                event.getTransactionKey(), e);
                        throw new RuntimeException("ES serialization failed", e);
                    }
                })
                .setMaxBatchSize(1000)
                .setMaxTimeInBufferMS(5000)
                // setMaxBufferSizeInBytes removed — not available in 3.1.0-1.19
                .build();
    }
}
