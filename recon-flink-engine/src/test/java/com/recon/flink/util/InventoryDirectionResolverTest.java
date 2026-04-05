package com.recon.flink.util;

import com.recon.flink.domain.FlatSimTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryDirectionResolverTest {

    @Test
    void resolvesWhdShipmentFromCounterpartyProcessingState() {
        FlatSimTransaction siocs = transaction("SIOCS", 22, "WarehouseDelivery", 0, "2026-04-04T09:05:00Z");
        FlatSimTransaction mfcs = transaction("MFCS", 22, "WarehouseDelivery", 1, "2026-04-04T09:00:00Z");

        InventoryDirectionProfile profile = InventoryDirectionResolver.resolve("SIOCS_MFCS", siocs, mfcs);

        assertEquals("MFCS", profile.originSystem());
        assertEquals("SIOCS", profile.counterpartySystem());
        assertEquals("SHIPMENT", profile.transactionPhase().name());
        assertTrue(profile.resolved());
    }

    @Test
    void resolvesWhdReceiptFromTimestampOrdering() {
        FlatSimTransaction siocs = transaction("SIOCS", 22, "WarehouseDelivery", 1, "2026-04-04T09:00:00Z");
        FlatSimTransaction mfcs = transaction("MFCS", 22, "WarehouseDelivery", 1, "2026-04-04T09:07:00Z");

        InventoryDirectionProfile profile = InventoryDirectionResolver.resolve("SIOCS_MFCS", siocs, mfcs);

        assertEquals("SIOCS", profile.originSystem());
        assertEquals("MFCS", profile.counterpartySystem());
        assertEquals("RECEIPT", profile.transactionPhase().name());
        assertTrue(profile.resolved());
    }

    @Test
    void keepsConservativeFallbackForAmbiguousSingleSiocsWhdEvent() {
        FlatSimTransaction siocs = transaction("SIOCS", 22, "WarehouseDelivery", 1, "2026-04-04T09:00:00Z");

        InventoryDirectionProfile profile = InventoryDirectionResolver.resolve("SIOCS_MFCS", siocs, null);

        assertEquals("SIOCS", profile.originSystem());
        assertEquals("MFCS", profile.counterpartySystem());
        assertEquals("RECEIPT", profile.transactionPhase().name());
        assertFalse(profile.resolved());
    }

    private FlatSimTransaction transaction(String source,
                                           int transactionType,
                                           String transactionTypeDesc,
                                           Integer processingStatus,
                                           String updateDateTime) {
        FlatSimTransaction transaction = new FlatSimTransaction();
        transaction.setTenantId("tenant-india");
        transaction.setTransactionKey("ORG1|EXT-1001");
        transaction.setExternalId("EXT-1001");
        transaction.setStoreId("1001");
        transaction.setBusinessDate("2026-04-04");
        transaction.setSource(source);
        transaction.setTransactionType(transactionType);
        transaction.setTransactionTypeDesc(transactionTypeDesc);
        transaction.setProcessingStatus(processingStatus);
        transaction.setUpdateDateTime(updateDateTime);
        transaction.setTotalQuantity(new BigDecimal("10"));
        return transaction;
    }
}
