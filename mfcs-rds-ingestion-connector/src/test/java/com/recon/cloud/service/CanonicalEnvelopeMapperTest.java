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
    void mapsReturnToVendorToFamilyFields() {
        CanonicalIntegrationEnvelope envelope = mapper.map(contract(),
                CloudStagedTransaction.builder()
                        .externalId("RTV-1001")
                        .sourceRecordKey("SRC-9")
                        .storeId("1001")
                        .type(60)
                        .lineItemCount(2)
                        .totalQuantity(new BigDecimal("7"))
                        .build(),
                CloudTransactionEvent.builder()
                        .eventId("evt-9")
                        .eventType("MFCS_TRANSACTION")
                        .tenantId("tenant-india")
                        .publishedAt("2026-04-04T08:00:00Z")
                        .transactionKey("1|RTV-1001")
                        .transactionType(60)
                        .transactionTypeDesc("ReturnToVendor")
                        .businessDate("2026-04-04")
                        .build());

        assertEquals("RTV", envelope.getPayload().getTransactionFamily());
        assertEquals("RETURN", envelope.getPayload().getTransactionPhase());
        assertEquals("RTV-1001", envelope.getPayload().getBusinessReference());
        assertTrue(Boolean.TRUE.equals(envelope.getPayload().getQuantityMetricsAvailable()));
        assertFalse(Boolean.TRUE.equals(envelope.getPayload().getValueMetricsAvailable()));
    }

    private MfcsIntegrationContract contract() {
        CloudConnectorProperties properties = new CloudConnectorProperties();
        properties.setConnectorName("mfcs-rds-main");
        properties.setSourceName("MFCS");
        return new MfcsIntegrationContract(properties);
    }
}
