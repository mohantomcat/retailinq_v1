package com.recon.flink.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class FlatSimTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    // Identity
    private String transactionKey;
    private String externalId;
    private String source;
    private String storeId;
    private String wkstnId;
    private String businessDate;
    private Long requestId;

    // Transaction details
    private String transactionDateTime;
    private String updateDateTime;
    private Integer transactionType;
    private String transactionTypeDesc;

    // Processing state
    private Integer processingStatus;
    private String processingStatusDesc;

    // Line items — array, not List<T>
    private FlatLineItem[] lineItems;
    private int lineItemCount;
    private BigDecimal totalQuantity;

    // Duplicate detection
    private boolean duplicateFlag;
    private int duplicatePostingCount;

    // Reconciliation
    private String checksum;

    public FlatSimTransaction() {
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getWkstnId() {
        return wkstnId;
    }

    public void setWkstnId(String wkstnId) {
        this.wkstnId = wkstnId;
    }

    public String getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(String businessDate) {
        this.businessDate = businessDate;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getTransactionDateTime() {
        return transactionDateTime;
    }

    public void setTransactionDateTime(String transactionDateTime) {
        this.transactionDateTime = transactionDateTime;
    }

    public String getUpdateDateTime() {
        return updateDateTime;
    }

    public void setUpdateDateTime(String updateDateTime) {
        this.updateDateTime = updateDateTime;
    }

    public Integer getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(Integer transactionType) {
        this.transactionType = transactionType;
    }

    public String getTransactionTypeDesc() {
        return transactionTypeDesc;
    }

    public void setTransactionTypeDesc(String transactionTypeDesc) {
        this.transactionTypeDesc = transactionTypeDesc;
    }

    public Integer getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(Integer processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getProcessingStatusDesc() {
        return processingStatusDesc;
    }

    public void setProcessingStatusDesc(String processingStatusDesc) {
        this.processingStatusDesc = processingStatusDesc;
    }

    public FlatLineItem[] getLineItems() {
        return lineItems;
    }

    public void setLineItems(FlatLineItem[] lineItems) {
        this.lineItems = lineItems;
    }

    public int getLineItemCount() {
        return lineItemCount;
    }

    public void setLineItemCount(int lineItemCount) {
        this.lineItemCount = lineItemCount;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public boolean isDuplicateFlag() {
        return duplicateFlag;
    }

    public void setDuplicateFlag(boolean duplicateFlag) {
        this.duplicateFlag = duplicateFlag;
    }

    public int getDuplicatePostingCount() {
        return duplicatePostingCount;
    }

    public void setDuplicatePostingCount(int duplicatePostingCount) {
        this.duplicatePostingCount = duplicatePostingCount;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}
