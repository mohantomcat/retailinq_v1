package com.recon.flink.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class ItemDiscrepancy implements Serializable {

    private static final long serialVersionUID = 1L;

    private String itemId;
    private String lineType;
    private DiscrepancyType type;
    private BigDecimal xstoreQuantity;
    private BigDecimal siocsQuantity;
    private BigDecimal xstoreAmount;
    private BigDecimal siocsAmount;
    private BigDecimal varianceQuantity;
    private BigDecimal variancePercent;
    private BigDecimal varianceAmount;
    private BigDecimal varianceAmountPercent;
    private String xstoreUom;
    private String siocsUom;
    private boolean withinTolerance;
    private String toleranceType;
    private BigDecimal toleranceValue;
    private String severityBand;
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

    public String getLineType() {
        return lineType;
    }

    public void setLineType(String v) {
        lineType = v;
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

    public BigDecimal getXstoreAmount() {
        return xstoreAmount;
    }

    public void setXstoreAmount(BigDecimal v) {
        xstoreAmount = v;
    }

    public BigDecimal getSiocsAmount() {
        return siocsAmount;
    }

    public void setSiocsAmount(BigDecimal v) {
        siocsAmount = v;
    }

    public BigDecimal getVarianceQuantity() {
        return varianceQuantity;
    }

    public void setVarianceQuantity(BigDecimal v) {
        varianceQuantity = v;
    }

    public BigDecimal getVariancePercent() {
        return variancePercent;
    }

    public void setVariancePercent(BigDecimal v) {
        variancePercent = v;
    }

    public BigDecimal getVarianceAmount() {
        return varianceAmount;
    }

    public void setVarianceAmount(BigDecimal v) {
        varianceAmount = v;
    }

    public BigDecimal getVarianceAmountPercent() {
        return varianceAmountPercent;
    }

    public void setVarianceAmountPercent(BigDecimal v) {
        varianceAmountPercent = v;
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

    public boolean isWithinTolerance() {
        return withinTolerance;
    }

    public void setWithinTolerance(boolean v) {
        withinTolerance = v;
    }

    public String getToleranceType() {
        return toleranceType;
    }

    public void setToleranceType(String v) {
        toleranceType = v;
    }

    public BigDecimal getToleranceValue() {
        return toleranceValue;
    }

    public void setToleranceValue(BigDecimal v) {
        toleranceValue = v;
    }

    public String getSeverityBand() {
        return severityBand;
    }

    public void setSeverityBand(String v) {
        severityBand = v;
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

        public Builder lineType(String v) {
            d.lineType = v;
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

        public Builder xstoreAmount(BigDecimal v) {
            d.xstoreAmount = v;
            return this;
        }

        public Builder siocsAmount(BigDecimal v) {
            d.siocsAmount = v;
            return this;
        }

        public Builder varianceQuantity(BigDecimal v) {
            d.varianceQuantity = v;
            return this;
        }

        public Builder variancePercent(BigDecimal v) {
            d.variancePercent = v;
            return this;
        }

        public Builder varianceAmount(BigDecimal v) {
            d.varianceAmount = v;
            return this;
        }

        public Builder varianceAmountPercent(BigDecimal v) {
            d.varianceAmountPercent = v;
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

        public Builder withinTolerance(boolean v) {
            d.withinTolerance = v;
            return this;
        }

        public Builder toleranceType(String v) {
            d.toleranceType = v;
            return this;
        }

        public Builder toleranceValue(BigDecimal v) {
            d.toleranceValue = v;
            return this;
        }

        public Builder severityBand(String v) {
            d.severityBand = v;
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
