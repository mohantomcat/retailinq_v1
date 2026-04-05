package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudStagedTransaction;
import com.recon.cloud.domain.CloudTransactionEvent;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalEnvelopeMapperTest {

    private final CanonicalEnvelopeMapper mapper = new CanonicalEnvelopeMapper();

    @Test
    void mapsWarehouseDeliveryToFamilyFields() {
        CanonicalIntegrationEnvelope envelope = mapper.map(contract(),
                CloudStagedTransaction.builder()
                        .externalId("EXT-1001")
                        .sourceRecordKey("SRC-1")
                        .storeId("1001")
                        .type(22)
                        .lineItemCount(3)
                        .totalQuantity(new BigDecimal("18"))
                        .build(),
                CloudTransactionEvent.builder()
                        .eventId("evt-1")
                        .eventType("SIOCS_TRANSACTION")
                        .tenantId("tenant-india")
                        .publishedAt("2026-04-04T08:00:00Z")
                        .transactionKey("1|EXT-1001")
                        .transactionType(22)
                        .transactionTypeDesc("WarehouseDelivery")
                        .businessDate("2026-04-04")
                        .build());

        assertEquals("WHD", envelope.getPayload().getTransactionFamily());
        assertEquals("RECEIPT", envelope.getPayload().getTransactionPhase());
        assertEquals("EXT-1001", envelope.getPayload().getBusinessReference());
        assertTrue(Boolean.TRUE.equals(envelope.getPayload().getQuantityMetricsAvailable()));
        assertFalse(Boolean.TRUE.equals(envelope.getPayload().getValueMetricsAvailable()));
    }

    @Test
    void leavesAmbiguousTypeAsUnknownFamily() {
        CanonicalIntegrationEnvelope envelope = mapper.map(contract(),
                CloudStagedTransaction.builder()
                        .sourceRecordKey("SRC-2")
                        .storeId("1001")
                        .type(20)
                        .build(),
                CloudTransactionEvent.builder()
                        .eventId("evt-2")
                        .eventType("SIOCS_TRANSACTION")
                        .tenantId("tenant-india")
                        .publishedAt("2026-04-04T08:00:00Z")
                        .transactionKey("1|SRC-2")
                        .transactionType(20)
                        .transactionTypeDesc("Receiving")
                        .businessDate("2026-04-04")
                        .build());

        assertEquals("UNKNOWN", envelope.getPayload().getTransactionFamily());
        assertEquals("UNKNOWN", envelope.getPayload().getTransactionPhase());
        assertEquals("SRC-2", envelope.getPayload().getBusinessReference());
    }

    private SiocsIntegrationContract contract() {
        CloudConnectorProperties properties = new CloudConnectorProperties();
        properties.setConnectorName("siocs-cloud-main");
        properties.setSourceName("SIOCS");
        return new SiocsIntegrationContract(properties);
    }
}
