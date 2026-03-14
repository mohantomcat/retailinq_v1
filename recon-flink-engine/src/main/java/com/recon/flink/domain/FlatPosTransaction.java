package com.recon.flink.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class FlatPosTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    // Identity
    private String transactionKey;
    private String externalId;
    private long organizationId;
    private String storeId;
    private String businessDate;
    private long wkstnId;
    private long transSeq;

    // Transaction details
    private String transactionType;
    private String beginDatetime;
    private String endDatetime;
    private String operatorId;
    private BigDecimal totalAmount;

    // Line items — array, not List<T>
    private FlatLineItem[] lineItems;

    // Reconciliation
    private String checksum;
    private boolean compressed;
    private boolean clockDriftDetected;

    public FlatPosTransaction() {
    }

    // Getters and setters
    public String getTransactionKey() {
        return transactionKey;
    }

    public void setTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(long organizationId) {
        this.organizationId = organizationId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(String businessDate) {
        this.businessDate = businessDate;
    }

    public long getWkstnId() {
        return wkstnId;
    }

    public void setWkstnId(long wkstnId) {
        this.wkstnId = wkstnId;
    }

    public long getTransSeq() {
        return transSeq;
    }

    public void setTransSeq(long transSeq) {
        this.transSeq = transSeq;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getBeginDatetime() {
        return beginDatetime;
    }

    public void setBeginDatetime(String beginDatetime) {
        this.beginDatetime = beginDatetime;
    }

    public String getEndDatetime() {
        return endDatetime;
    }

    public void setEndDatetime(String endDatetime) {
        this.endDatetime = endDatetime;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public FlatLineItem[] getLineItems() {
        return lineItems;
    }

    public void setLineItems(FlatLineItem[] lineItems) {
        this.lineItems = lineItems;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public boolean isClockDriftDetected() {
        return clockDriftDetected;
    }

    public void setClockDriftDetected(boolean clockDriftDetected) {
        this.clockDriftDetected = clockDriftDetected;
    }
}