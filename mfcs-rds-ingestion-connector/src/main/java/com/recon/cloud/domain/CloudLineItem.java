package com.recon.cloud.domain;

import com.recon.cloud.util.ReconcilableItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudLineItem implements ReconcilableItem {
    private Long id;
    private String itemId;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private Integer type;
    private Integer processingStatus;
    private String transactionExtendedId;

    @Override
    public String getLineType() {
        return switch (type != null ? type : -1) {
            case 1 -> "Sale";
            case 2 -> "Return";
            case 3 -> "VoidSale";
            case 4 -> "VoidReturn";
            case 10, 11, 12 -> "Transfer";
            case 20, 21, 22 -> "Receiving";
            case 30 -> "DirectStoreDelivery";
            case 40 -> "InventoryAdjustment";
            case 50 -> "PurchaseOrder";
            case 60 -> "ReturnToVendor";
            case 70 -> "WarehouseDelivery";
            case 80 -> "StoreTransfer";
            default -> "Unknown";
        };
    }
}
