package com.recon.cloud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.cloud.domain.CloudApiLineItem;
import com.recon.cloud.domain.CloudApiPage;
import com.recon.cloud.domain.CloudApiTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;

public final class JsonMappingSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonMappingSupport() {
    }

    @SuppressWarnings("unchecked")
    public static CloudApiPage mapPage(Map<String, Object> value) {
        if (value != null && value.containsKey("items")) {
            List<Map<String, Object>> items = OBJECT_MAPPER.convertValue(
                    value.get("items"), List.class);
            List<CloudApiTransaction> records = new ArrayList<>();
            if (items != null) {
                for (Map<String, Object> item : items) {
                    CloudApiTransaction transaction = OBJECT_MAPPER.convertValue(
                            item, CloudApiTransaction.class);
                    CloudApiLineItem lineItem = OBJECT_MAPPER.convertValue(
                            item, CloudApiLineItem.class);
                    if (transaction.getSourceRecordKey() == null
                            || transaction.getSourceRecordKey().isBlank()) {
                        transaction.setSourceRecordKey(buildSourceRecordKey(transaction, lineItem));
                    }
                    lineItem.setId(transaction.getId());
                    transaction.setLineItems(List.of(lineItem));
                    records.add(transaction);
                }
            }

            Boolean hasMore = OBJECT_MAPPER.convertValue(value.get("hasMore"), Boolean.class);
            return CloudApiPage.builder()
                    .records(records)
                    .hasMore(Boolean.TRUE.equals(hasMore))
                    .build();
        }
        return OBJECT_MAPPER.convertValue(value, CloudApiPage.class);
    }

    private static String buildSourceRecordKey(CloudApiTransaction transaction,
                                               CloudApiLineItem lineItem) {
        if (transaction.getId() != null) {
            return String.valueOf(transaction.getId());
        }
        if (lineItem.getTransactionExtendedId() != null
                && !lineItem.getTransactionExtendedId().isBlank()) {
            return lineItem.getTransactionExtendedId();
        }
        return Objects.toString(transaction.getExternalId(), "UNKNOWN");
    }
}
