package com.recon.rms.domain;

import com.recon.rms.util.ReconcilableItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RmsLineItem implements ReconcilableItem {
    private Long id;
    private String itemId;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private Integer type;
    private Integer processingStatus;
    private String transactionExtendedId;

    @Override
    public String getItemId() {
        return itemId;
    }

    @Override
    public BigDecimal getQuantity() {
        return quantity;
    }

    @Override
    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    @Override
    public String getLineType() {
        return switch (type != null ? type : -1) {
            case 10 -> "Transfer";
            case 11 -> "StoreToStoreTransfer";
            case 12 -> "StoreToWarehouseTransfer";
            case 20 -> "Receiving";
            case 21 -> "StoreToStoreReceiving";
            case 22, 70 -> "WarehouseDelivery";
            case 30 -> "DirectStoreDelivery";
            case 40 -> "InventoryAdjustment";
            case 50 -> "PurchaseOrder";
            case 60 -> "ReturnToVendor";
            case 80 -> "StoreTransfer";
            default -> "Unknown";
        };
    }
}

