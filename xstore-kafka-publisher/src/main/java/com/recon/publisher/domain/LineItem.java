package com.recon.publisher.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.recon.publisher.util.ReconcilableItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LineItem implements ReconcilableItem {

    private int lineSeq;
    private String lineType;
    private String itemId;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private BigDecimal unitPrice;
    private BigDecimal extendedAmount;
    private List<InventoryModifier> inventoryModifiers;

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
}