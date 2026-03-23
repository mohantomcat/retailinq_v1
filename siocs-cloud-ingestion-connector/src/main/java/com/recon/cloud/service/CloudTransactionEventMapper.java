package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudStagedTransaction;
import com.recon.cloud.domain.CloudTransactionEvent;
import com.recon.cloud.util.ChecksumUtil;
import com.recon.cloud.util.TenantTimeZoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CloudTransactionEventMapper {

    private final CloudConnectorProperties properties;

    private static final Map<Integer, String> TYPE_DESC = Map.ofEntries(
            Map.entry(1, "Sale"),
            Map.entry(2, "Return"),
            Map.entry(3, "VoidSale"),
            Map.entry(4, "VoidReturn"),
            Map.entry(10, "Transfer"),
            Map.entry(11, "StoreToStoreTransfer"),
            Map.entry(12, "StoreToWarehouseTransfer"),
            Map.entry(20, "Receiving"),
            Map.entry(21, "StoreToStoreReceiving"),
            Map.entry(22, "WarehouseDelivery"),
            Map.entry(30, "DirectStoreDelivery"),
            Map.entry(40, "InventoryAdjustment"),
            Map.entry(50, "PurchaseOrder"),
            Map.entry(60, "ReturnToVendor"),
            Map.entry(70, "WarehouseDelivery"),
            Map.entry(80, "StoreTransfer")
    );

    private static final Map<Integer, String> STATUS_DESC = Map.of(
            0, "NEW",
            1, "PROCESSED",
            2, "FAILED",
            3, "RETRY",
            4, "REVERTED"
    );

    public CloudTransactionEvent map(CloudStagedTransaction transaction) {
        String transactionKey = buildTransactionKey(transaction);
        String businessDate = null;
        if (transaction.getExternalId() != null && transaction.getExternalId().length() == 22) {
            String rawDate = transaction.getExternalId().substring(14, 22);
            businessDate = rawDate.substring(0, 4) + "-" +
                    rawDate.substring(4, 6) + "-" +
                    rawDate.substring(6, 8);
        } else if (transaction.getTransactionDateTime() != null) {
            businessDate = transaction.getTransactionDateTime()
                    .toInstant()
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .toString();
        }

        return CloudTransactionEvent.builder()
                .eventType("SIOCS_TRANSACTION")
                .eventId(UUID.randomUUID().toString())
                .source(properties.getSourceName())
                .publishedAt(TenantTimeZoneUtil.nowUtc())
                .tenantId(properties.getTenantId())
                .tenantTimezone(properties.getTenantTimezone())
                .externalId(transaction.getExternalId())
                .transactionKey(transactionKey)
                .requestId(transaction.getRequestId())
                .storeId(transaction.getStoreId())
                .businessDate(businessDate)
                .transactionDateTime(toUtcString(transaction.getTransactionDateTime()))
                .updateDateTime(toUtcString(transaction.getUpdateDateTime()))
                .transactionType(transaction.getType())
                .transactionTypeDesc(resolveTransactionTypeDesc(transaction.getType()))
                .processingStatus(transaction.getProcessingStatus())
                .processingStatusDesc(STATUS_DESC.getOrDefault(transaction.getProcessingStatus(), "UNKNOWN"))
                .lineItems(transaction.getLineItems())
                .lineItemCount(transaction.getLineItemCount())
                .totalQuantity(transaction.getTotalQuantity())
                .duplicateFlag(transaction.isDuplicateFlag())
                .duplicatePostingCount(transaction.getPostingCount())
                .checksum(ChecksumUtil.compute(transaction.getLineItems()))
                .build();
    }

    private String buildTransactionKey(CloudStagedTransaction transaction) {
        if (hasText(transaction.getExternalId())) {
            return properties.getOrgId() + "|" + transaction.getExternalId();
        }
        if (hasText(transaction.getSourceRecordKey())) {
            return properties.getOrgId() + "|SRC|" + transaction.getSourceRecordKey();
        }
        if (transaction.getFirstRowId() != null) {
            return properties.getOrgId() + "|ROW|" + transaction.getFirstRowId();
        }
        return properties.getOrgId() + "|UNKNOWN|" + java.util.UUID.randomUUID();
    }

    private boolean hasText(String value) {
        return value != null
                && !value.isBlank()
                && !"null".equalsIgnoreCase(value.trim());
    }

    private String toUtcString(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        Instant utc = TenantTimeZoneUtil.toUtc(
                timestamp.toInstant().toString(),
                properties.getTenantTimezone());
        return utc.toString();
    }

    private String resolveTransactionTypeDesc(Integer type) {
        if (type == null) {
            return "UNKNOWN";
        }
        return TYPE_DESC.getOrDefault(type, "TRANSACTION_" + type);
    }
}
