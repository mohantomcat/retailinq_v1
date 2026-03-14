package com.recon.poller.domain;

import com.recon.poller.util.ReconcilableItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiocsLineItem implements ReconcilableItem {
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
            case 1 -> "Sale";
            case 2 -> "Return";
            case 3 -> "VoidSale";
            case 4 -> "VoidReturn";
            default -> "Unknown";
        };
    }
}
