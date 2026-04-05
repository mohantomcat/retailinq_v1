package com.recon.poller.mapper;

import com.recon.poller.config.PollerConfig;
import com.recon.poller.domain.SimTransactionEvent;
import com.recon.poller.domain.SiocsTransactionRow;
import com.recon.poller.util.ChecksumUtil;
import com.recon.poller.util.TenantTimeZoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import static java.util.Map.entry;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SiocsTransactionMapper {

    private final PollerConfig config;

    private static final Map<Integer, String> TYPE_DESC = Map.ofEntries(
            entry(1, "Sale"),
            entry(2, "Return"),
            entry(3, "VoidSale"),
            entry(4, "VoidReturn"),
            entry(5, "CancelReservation"),
            entry(6, "New"),
            entry(8, "Fulfill"),
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

    public SimTransactionEvent mapToEvent(
            SiocsTransactionRow row, long orgId) {

        String externalId = row.getExternalId();
        String transactionKey = orgId + "|" + externalId;

        // Extract businessDate from externalId (positions 8-16)
        // and convert to UTC date
        String businessDate = null;
        String storeId = null;
        if (externalId != null && externalId.length() == 22) {
            storeId = trimLeadingZeroes(externalId.substring(0, 5));

            String rawDate = externalId.substring(14, 22); // 20251126
            businessDate = rawDate.substring(0, 4) + "-" +  // yyyy
                    rawDate.substring(4, 6) + "-" +  // MM
                    rawDate.substring(6, 8);          // dd
        }

        // Convert Oracle timestamps to UTC
        String transactionDateTimeUtc = null;
        String updateDateTimeUtc = null;

        if (row.getTransactionDateTime() != null) {
            Instant utc = TenantTimeZoneUtil.toUtc(
                    row.getTransactionDateTime().toInstant()
                            .toString(),
                    config.getTenantTimezone());
            transactionDateTimeUtc = utc.toString();
        }

        if (row.getUpdateDateTime() != null) {
            Instant utc = TenantTimeZoneUtil.toUtc(
                    row.getUpdateDateTime().toInstant()
                            .toString(),
                    config.getTenantTimezone());
            updateDateTimeUtc = utc.toString();
        }

        String checksum = ChecksumUtil.compute(
                row.getLineItems());

        return SimTransactionEvent.builder()
                .schemaVersion(1)
                .eventType("SIM_TRANSACTION")
                .eventId(UUID.randomUUID().toString())
                .source("SIOCS")
                .publishedAt(TenantTimeZoneUtil.nowUtc())
                .tenantId(config.getTenantId())
                .tenantTimezone(config.getTenantTimezone())
                .externalId(externalId)
                .transactionKey(transactionKey)
                .requestId(row.getRequestId())
                .storeId(storeId)
                .businessDate(businessDate)
                .transactionDateTime(transactionDateTimeUtc)
                .updateDateTime(updateDateTimeUtc)
                .transactionType(row.getType())
                .transactionTypeDesc(
                        TYPE_DESC.getOrDefault(row.getType(), "UNKNOWN"))
                .processingStatus(row.getProcessingStatus())
                .processingStatusDesc(
                        STATUS_DESC.getOrDefault(
                                row.getProcessingStatus(), "UNKNOWN"))
                .lineItems(row.getLineItems())
                .lineItemCount(row.getLineItemCount())
                .totalQuantity(row.getTotalQuantity())
                .duplicateFlag(row.isDuplicateFlag())
                .duplicatePostingCount(row.getPostingCount())
                .checksum(checksum)
                .build();
    }

    private String trimLeadingZeroes(String value) {
        String normalized = value.replaceFirst("^0+(?!$)", "");
        return normalized.isBlank() ? "0" : normalized;
    }
}
