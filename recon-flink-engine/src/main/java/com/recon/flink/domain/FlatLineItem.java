package com.recon.flink.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class FlatLineItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String itemId;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private BigDecimal unitPrice;
    private BigDecimal extendedAmount;
    private String lineType;      // "Sale" or "Return"
    private int lineSeq;

    public FlatLineItem() {
    }

    // Getters and setters
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getExtendedAmount() {
        return extendedAmount;
    }

    public void setExtendedAmount(BigDecimal extendedAmount) {
        this.extendedAmount = extendedAmount;
    }

    public String getLineType() {
        return lineType;
    }

    public void setLineType(String lineType) {
        this.lineType = lineType;
    }

    public int getLineSeq() {
        return lineSeq;
    }

    public void setLineSeq(int lineSeq) {
        this.lineSeq = lineSeq;
    }
}