package com.recon.integration.recon;

import java.util.Map;
import java.util.Objects;

/**
 * Conservative family mappings for the current SIOCS/MFCS inventory lane.
 * Ambiguous runtime codes stay UNKNOWN until validated from live payloads.
 */
public final class TransactionFamilyRegistry {

    private static final Map<Integer, TransactionFamilyConfig> INVENTORY_FAMILY_BY_TYPE =
            Map.ofEntries(
                    Map.entry(11, new TransactionFamilyConfig(
                            11,
                            "StoreToStoreTransfer",
                            TransactionFamily.TRANSFER_SHIPMENT,
                            TransactionPhase.SHIPMENT,
                            true,
                            false,
                            false,
                            "Store-to-store transfer treated as outbound shipment."
                    )),
                    Map.entry(12, new TransactionFamilyConfig(
                            12,
                            "StoreToWarehouseTransfer",
                            TransactionFamily.RTW,
                            TransactionPhase.RETURN,
                            true,
                            false,
                            true,
                            "Mapped to RTW based on current store-to-warehouse transfer semantics."
                    )),
                    Map.entry(21, new TransactionFamilyConfig(
                            21,
                            "StoreToStoreReceiving",
                            TransactionFamily.TRANSFER_RECEIPT,
                            TransactionPhase.RECEIPT,
                            true,
                            false,
                            false,
                            "Store-to-store receiving treated as inbound transfer receipt."
                    )),
                    Map.entry(22, new TransactionFamilyConfig(
                            22,
                            "WarehouseDelivery",
                            TransactionFamily.WHD,
                            TransactionPhase.RECEIPT,
                            true,
                            false,
                            false,
                            "Warehouse delivery treated as warehouse-to-store receipt."
                    )),
                    Map.entry(30, new TransactionFamilyConfig(
                            30,
                            "DirectStoreDelivery",
                            TransactionFamily.DSD,
                            TransactionPhase.RECEIPT,
                            true,
                            false,
                            false,
                            "Direct store delivery treated as receipt-side inventory movement."
                    )),
                    Map.entry(50, new TransactionFamilyConfig(
                            50,
                            "PurchaseOrder",
                            TransactionFamily.PO,
                            TransactionPhase.RECEIPT,
                            true,
                            false,
                            false,
                            "Purchase order traffic currently modeled as receipt-side inventory movement."
                    )),
                    Map.entry(60, new TransactionFamilyConfig(
                            60,
                            "ReturnToVendor",
                            TransactionFamily.RTV,
                            TransactionPhase.RETURN,
                            true,
                            false,
                            false,
                            "Return to vendor treated as outbound return."
                    )),
                    Map.entry(70, new TransactionFamilyConfig(
                            70,
                            "WarehouseDelivery",
                            TransactionFamily.WHD,
                            TransactionPhase.RECEIPT,
                            true,
                            false,
                            false,
                            "Warehouse delivery treated as warehouse-to-store receipt."
                    )),
                    Map.entry(80, new TransactionFamilyConfig(
                            80,
                            "StoreTransfer",
                            TransactionFamily.TRANSFER_SHIPMENT,
                            TransactionPhase.SHIPMENT,
                            true,
                            false,
                            true,
                            "Mapped to transfer shipment until a more specific live-payload distinction is available."
                    ))
            );

    private TransactionFamilyRegistry() {
    }

    public static TransactionFamilyConfig resolveInventoryFamily(Integer transactionTypeCode,
                                                                 String transactionTypeDescription) {
        TransactionFamilyConfig config = INVENTORY_FAMILY_BY_TYPE.get(transactionTypeCode);
        if (config != null) {
            return config;
        }
        return TransactionFamilyConfig.unknown(
                transactionTypeCode,
                normalizeDescription(transactionTypeCode, transactionTypeDescription)
        );
    }

    private static String normalizeDescription(Integer transactionTypeCode,
                                               String transactionTypeDescription) {
        String description = Objects.toString(transactionTypeDescription, "").trim();
        if (!description.isEmpty()) {
            return description;
        }
        return transactionTypeCode == null
                ? "UNKNOWN"
                : "TRANSACTION_" + transactionTypeCode;
    }
}
