package com.recon.flink.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class ItemDiscrepancy implements Serializable {

    private static final long serialVersionUID = 1L;

    private String itemId;
    private DiscrepancyType type;
    private BigDecimal xstoreQuantity;
    private BigDecimal siocsQuantity;
    private String xstoreUom;
    private String siocsUom;
    private String description;

    public ItemDiscrepancy() {
    }

    // Getters and setters
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String v) {
        itemId = v;
    }

    public DiscrepancyType getType() {
        return type;
    }

    public void setType(DiscrepancyType v) {
        type = v;
    }

    public BigDecimal getXstoreQuantity() {
        return xstoreQuantity;
    }

    public void setXstoreQuantity(BigDecimal v) {
        xstoreQuantity = v;
    }

    public BigDecimal getSiocsQuantity() {
        return siocsQuantity;
    }

    public void setSiocsQuantity(BigDecimal v) {
        siocsQuantity = v;
    }

    public String getXstoreUom() {
        return xstoreUom;
    }

    public void setXstoreUom(String v) {
        xstoreUom = v;
    }

    public String getSiocsUom() {
        return siocsUom;
    }

    public void setSiocsUom(String v) {
        siocsUom = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    // Builder — used by ItemDiffEngine
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ItemDiscrepancy d = new ItemDiscrepancy();

        public Builder itemId(String v) {
            d.itemId = v;
            return this;
        }

        public Builder type(DiscrepancyType v) {
            d.type = v;
            return this;
        }

        public Builder xstoreQuantity(BigDecimal v) {
            d.xstoreQuantity = v;
            return this;
        }

        public Builder siocsQuantity(BigDecimal v) {
            d.siocsQuantity = v;
            return this;
        }

        public Builder xstoreUom(String v) {
            d.xstoreUom = v;
            return this;
        }

        public Builder siocsUom(String v) {
            d.siocsUom = v;
            return this;
        }

        public Builder description(String v) {
            d.description = v;
            return this;
        }

        public ItemDiscrepancy build() {
            return d;
        }
    }
}