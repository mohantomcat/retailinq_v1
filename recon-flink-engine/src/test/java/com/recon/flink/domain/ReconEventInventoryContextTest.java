package com.recon.flink.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconEventInventoryContextTest {

    @Test
    void awaitingInventoryCarriesBusinessContext() {
        FlatSimTransaction source = transaction(
                "tenant-india",
                "1|EXT-3003",
                "EXT-3003",
                "1007",
                "2026-04-04",
                50,
                "PurchaseOrder",
                new BigDecimal("12"),
                "SIOCS",
                1,
                null
        );

        ReconEvent event = ReconEvent.awaitingInventory(source, "SIOCS_MFCS", "MFCS");

        assertEquals("tenant-india", event.getTenantId());
        assertEquals("PO", event.getTransactionFamily());
        assertEquals("RECEIPT", event.getTransactionPhase());
        assertEquals("SIOCS", event.getOriginSystem());
        assertEquals("MFCS", event.getCounterpartySystem());
        assertFalse(event.isDirectionResolved());
        assertEquals("TENANT-INDIA|PO|EXT-3003|1007", event.getHeaderMatchKey());
        assertTrue(event.isSourcePresent());
        assertFalse(event.isTargetPresent());
        assertEquals(new BigDecimal("12"), event.getSourceTotalQuantity());
    }

    @Test
    void processingPendingInventoryUsesResolvedShipmentDirection() {
        FlatSimTransaction mfcsOrigin = transaction(
                "tenant-india",
                "1|EXT-4004",
                "EXT-4004",
                "1008",
                "2026-04-04",
                22,
                "WarehouseDelivery",
                new BigDecimal("24"),
                "MFCS",
                1,
                "2026-04-04T09:00:00Z"
        );
        FlatSimTransaction siocsCounterparty = transaction(
                "tenant-india",
                "1|EXT-4004",
                "EXT-4004",
                "1008",
                "2026-04-04",
                22,
                "WarehouseDelivery",
                new BigDecimal("24"),
                "SIOCS",
                0,
                "2026-04-04T09:05:00Z"
        );

        ReconEvent event = ReconEvent.processingPendingInventory(mfcsOrigin, siocsCounterparty, "SIOCS_MFCS");

        assertEquals("PROCESSING_PENDING_IN_SIOCS", event.getReconStatus());
        assertEquals("MFCS", event.getOriginSystem());
        assertEquals("SIOCS", event.getCounterpartySystem());
        assertTrue(event.isDirectionResolved());
        assertEquals("SHIPMENT", event.getTransactionPhase());
        assertEquals("MFCS", event.getSimSource());
    }

    private FlatSimTransaction transaction(String tenantId,
                                           String transactionKey,
                                           String externalId,
                                           String storeId,
                                           String businessDate,
                                           int transactionType,
                                           String transactionTypeDesc,
                                           BigDecimal totalQuantity,
                                           String source,
                                           Integer processingStatus,
                                           String updateDateTime) {
        FlatSimTransaction transaction = new FlatSimTransaction();
        transaction.setTenantId(tenantId);
        transaction.setTransactionKey(transactionKey);
        transaction.setExternalId(externalId);
        transaction.setStoreId(storeId);
        transaction.setBusinessDate(businessDate);
        transaction.setTransactionType(transactionType);
        transaction.setTransactionTypeDesc(transactionTypeDesc);
        transaction.setTotalQuantity(totalQuantity);
        transaction.setSource(source);
        transaction.setProcessingStatus(processingStatus);
        transaction.setUpdateDateTime(updateDateTime);
        transaction.setLineItems(new FlatLineItem[0]);
        transaction.setLineItemCount(0);
        return transaction;
    }
}
