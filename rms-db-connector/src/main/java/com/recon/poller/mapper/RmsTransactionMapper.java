package com.recon.rms.mapper;

import com.recon.rms.config.PollerConfig;
import com.recon.rms.domain.RmsTransactionEvent;
import com.recon.rms.domain.RmsTransactionRow;
import com.recon.rms.util.ChecksumUtil;
import com.recon.rms.util.TenantTimeZoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static java.util.Map.entry;

@Component
@RequiredArgsConstructor
public class RmsTransactionMapper {

    private final PollerConfig config;

    private static final Map<Integer, String> TYPE_DESC = Map.ofEntries(
            entry(10, "Transfer"),
            entry(11, "StoreToStoreTransfer"),
            entry(12, "StoreToWarehouseTransfer"),
            entry(20, "Receiving"),
            entry(21, "StoreToStoreReceiving"),
            entry(22, "WarehouseDelivery"),
            entry(30, "DirectStoreDelivery"),
            entry(40, "InventoryAdjustment"),
            entry(50, "PurchaseOrder"),
            entry(60, "ReturnToVendor"),
            entry(70, "WarehouseDelivery"),
            entry(80, "StoreTransfer")
    );

    private static final Map<Integer, String> STATUS_DESC = Map.of(
            0, "NEW",
            1, "PROCESSED",
            2, "FAILED",
            3, "RETRY",
            4, "REVERTED"
    );

    public RmsTransactionEvent mapToEvent(RmsTransactionRow row, long orgId) {
        String externalId = row.getExternalId();
        String transactionKey = orgId + "|" + externalId;
        String businessDate = row.getTransactionDateTime() == null
                ? null
                : row.getTransactionDateTime().toLocalDateTime().toLocalDate().toString();

        String transactionDateTimeUtc = null;
        String updateDateTimeUtc = null;

        if (row.getTransactionDateTime() != null) {
            Instant utc = TenantTimeZoneUtil.toUtc(
                    row.getTransactionDateTime().toInstant().toString(),
                    config.getTenantTimezone());
            transactionDateTimeUtc = utc.toString();
        }

        if (row.getUpdateDateTime() != null) {
            Instant utc = TenantTimeZoneUtil.toUtc(
                    row.getUpdateDateTime().toInstant().toString(),
                    config.getTenantTimezone());
            updateDateTimeUtc = utc.toString();
        }

        String checksum = ChecksumUtil.compute(row.getLineItems());

        return RmsTransactionEvent.builder()
                .schemaVersion(1)
                .eventType("RMS_TRANSACTION")
                .eventId(UUID.randomUUID().toString())
                .source("RMS")
                .publishedAt(TenantTimeZoneUtil.nowUtc())
                .tenantId(config.getTenantId())
                .tenantTimezone(config.getTenantTimezone())
                .externalId(externalId)
                .transactionKey(transactionKey)
                .requestId(row.getRequestId())
                .storeId(row.getStoreId())
                .businessDate(businessDate)
                .transactionDateTime(transactionDateTimeUtc)
                .updateDateTime(updateDateTimeUtc)
                .transactionType(row.getType())
                .transactionTypeDesc(TYPE_DESC.getOrDefault(row.getType(), "UNKNOWN"))
                .processingStatus(row.getProcessingStatus())
                .processingStatusDesc(STATUS_DESC.getOrDefault(row.getProcessingStatus(), "UNKNOWN"))
                .lineItems(row.getLineItems())
                .lineItemCount(row.getLineItemCount())
                .totalQuantity(row.getTotalQuantity())
                .duplicateFlag(row.isDuplicateFlag())
                .duplicatePostingCount(row.getPostingCount())
                .checksum(checksum)
                .build();
    }
}
