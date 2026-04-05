package com.recon.flink.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.flink.domain.FlatSimTransaction;
import com.recon.poller.domain.SimTransactionEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimTransactionDeserializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimTransactionDeserializer deserializer = new SimTransactionDeserializer();

    @Test
    void mapsTenantIdIntoFlatRuntimeTransaction() throws Exception {
        SimTransactionEvent event = SimTransactionEvent.builder()
                .tenantId("tenant-india")
                .externalId("0123412300000120260404")
                .transactionKey("1|0123412300000120260404")
                .source("SIOCS")
                .storeId("1234")
                .businessDate("2026-04-04")
                .transactionType(22)
                .transactionTypeDesc("WarehouseDelivery")
                .lineItemCount(0)
                .totalQuantity(new BigDecimal("4"))
                .build();

        FlatSimTransaction flat = deserializer.deserialize(objectMapper.writeValueAsBytes(event));

        assertEquals("tenant-india", flat.getTenantId());
        assertEquals("123", flat.getWkstnId());
    }
}
