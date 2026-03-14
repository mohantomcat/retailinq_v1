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
            default -> "Unknown";
        };
    }
}
