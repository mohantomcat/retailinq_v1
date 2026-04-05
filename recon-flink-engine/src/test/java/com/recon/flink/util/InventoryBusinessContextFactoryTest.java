package com.recon.flink.util;

import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.FlatSimTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryBusinessContextFactoryTest {

    @Test
    void derivesTenantAwareBusinessKeysAndMetrics() {
        FlatSimTransaction source = transaction(
                "tenant-india",
                "ORG1|EXT-1001",
                "EXT-1001",
                "1001",
                "2026-04-04",
                22,
                "WarehouseDelivery",
                new BigDecimal("10"),
                "SKU-1",
                "SKU-2"
        );
        FlatSimTransaction target = transaction(
                "tenant-india",
                "ORG1|EXT-1001",
                "EXT-1001",
                "1001",
                "2026-04-04",
                22,
                "WarehouseDelivery",
                new BigDecimal("8"),
                "SKU-1"
        );

        InventoryBusinessContext context = InventoryBusinessContextFactory.from(source, target);

        assertEquals("tenant-india", context.tenantId());
        assertEquals("WHD", context.transactionFamily());
        assertEquals("RECEIPT", context.transactionPhase());
        assertEquals("EXT-1001", context.businessReference());
        assertEquals("TENANT-INDIA|WHD|EXT-1001|1001", context.headerMatchKey());
        assertEquals("TENANT-INDIA|2026-04-04|1001|WHD", context.aggregateKey());
        assertEquals(new BigDecimal("10"), context.sourceTotalQuantity());
        assertEquals(new BigDecimal("8"), context.targetTotalQuantity());
        assertEquals(new BigDecimal("2"), context.quantityVariance());
        assertEquals(2, context.sourceItemCount());
        assertEquals(1, context.targetItemCount());
        assertTrue(context.quantityMetricsAvailable());
        assertFalse(context.valueMetricsAvailable());
        assertArrayEquals(
                new String[]{
                        "TENANT-INDIA|WHD|EXT-1001|1001|SKU-1",
                        "TENANT-INDIA|WHD|EXT-1001|1001|SKU-2"
                },
                context.sourceLineMatchKeys().toArray(new String[0])
        );
    }

    @Test
    void fallsBackToUnknownFamilyWhenMappingIsNotConfirmed() {
        FlatSimTransaction source = transaction(
                "tenant-india",
                "ORG1|EXT-2002",
                "EXT-2002",
                "1002",
                "2026-04-04",
                20,
                "Receiving",
                new BigDecimal("5"),
                "SKU-9"
        );

        InventoryBusinessContext context = InventoryBusinessContextFactory.from(source, null);

        assertEquals("UNKNOWN", context.transactionFamily());
        assertEquals("UNKNOWN", context.transactionPhase());
        assertFalse(context.quantityMetricsAvailable());
        assertFalse(context.valueMetricsAvailable());
    }

    private FlatSimTransaction transaction(String tenantId,
                                           String transactionKey,
                                           String externalId,
                                           String storeId,
                                           String businessDate,
                                           int transactionType,
                                           String transactionTypeDesc,
                                           BigDecimal totalQuantity,
                                           String... itemIds) {
        FlatSimTransaction transaction = new FlatSimTransaction();
        transaction.setTenantId(tenantId);
        transaction.setTransactionKey(transactionKey);
        transaction.setExternalId(externalId);
        transaction.setStoreId(storeId);
        transaction.setBusinessDate(businessDate);
        transaction.setTransactionType(transactionType);
        transaction.setTransactionTypeDesc(transactionTypeDesc);
        transaction.setTotalQuantity(totalQuantity);
        FlatLineItem[] lineItems = new FlatLineItem[itemIds.length];
        for (int i = 0; i < itemIds.length; i++) {
            FlatLineItem lineItem = new FlatLineItem();
            lineItem.setItemId(itemIds[i]);
            lineItems[i] = lineItem;
        }
        transaction.setLineItems(lineItems);
        transaction.setLineItemCount(lineItems.length);
        return transaction;
    }
}
