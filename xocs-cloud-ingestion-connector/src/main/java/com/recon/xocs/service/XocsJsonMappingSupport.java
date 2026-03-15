package com.recon.xocs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.xocs.domain.XocsApiLineItem;
import com.recon.xocs.domain.XocsApiPage;
import com.recon.xocs.domain.XocsApiTransaction;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class XocsJsonMappingSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static XocsApiPage mapPage(Map<String, Object> response) {
        JsonNode root = MAPPER.valueToTree(response);
        Map<String, XocsApiTransaction> grouped = new LinkedHashMap<>();
        JsonNode items = root.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                XocsApiTransaction row = MAPPER.convertValue(item, XocsApiTransaction.class);
                String groupKey = transactionGroupKey(row);
                XocsApiTransaction transaction = grouped.computeIfAbsent(groupKey, ignored -> initializeTransaction(row));
                XocsApiLineItem lineItem = MAPPER.convertValue(item, XocsApiLineItem.class);
                if (lineItem.getRtransLineitmSeq() != null || hasItemIdentity(lineItem)) {
                    transaction.getLineItems().add(lineItem);
                }
            }
        }
        return XocsApiPage.builder()
                .records(new ArrayList<>(grouped.values()))
                .hasMore(root.path("hasMore").asBoolean(false))
                .build();
    }

    private static XocsApiTransaction initializeTransaction(XocsApiTransaction row) {
        if (row.getSourceRecordKey() == null || row.getSourceRecordKey().isBlank()) {
            row.setSourceRecordKey(row.getTransactionKey());
        }
        if ((row.getTransactionKey() == null || row.getTransactionKey().isBlank())
                && row.getOrganizationId() != null
                && row.getExternalId() != null) {
            row.setTransactionKey(row.getOrganizationId() + "|" + row.getExternalId());
        }
        if (row.getLineItems() == null) {
            row.setLineItems(new ArrayList<>());
        }
        return row;
    }

    private static boolean hasItemIdentity(XocsApiLineItem lineItem) {
        return lineItem.getItemId() != null && !lineItem.getItemId().isBlank();
    }

    private static String transactionGroupKey(XocsApiTransaction row) {
        if (row.getTransactionKey() != null && !row.getTransactionKey().isBlank()) {
            return row.getTransactionKey();
        }
        return String.join("|",
                safe(row.getOrganizationId()),
                safe(row.getRtlLocId()),
                safe(row.getBusinessDate()),
                safe(row.getWkstnId()),
                safe(row.getTransSeq()));
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
