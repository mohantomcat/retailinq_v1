package com.recon.flink.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class ReconEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String transactionKey;
    private String externalId;
    private String storeId;
    private String wkstnId;          // ADDED
    private String businessDate;
    private String reconView;
    private String simSource;
    private String reconStatus;
    private String transactionType;
    private Integer processingStatus;

    private String xstoreChecksum;
    private String siocsChecksum;
    private boolean checksumMatch;

    private ItemDiscrepancy[] discrepancies;

    private boolean duplicateFlag;
    private int duplicatePostingCount;

    private String reconciledAt;
    private long timerDriftMs;

    public ReconEvent() {
    }

    // ── Factory methods ────────────────────────────────────────────

    public static ReconEvent awaiting(FlatPosTransaction xstore,
                                      String reconView,
                                      String simSource) {
        return base(xstore)
                .reconView(reconView)
                .simSource(simSource)
                .reconStatus(awaitingStatus(reconView))
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent matched(FlatPosTransaction xstore,
                                     FlatSimTransaction siocs,
                                     String reconView) {
        return base(xstore)
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(ReconStatus.MATCHED.name())
                .processingStatus(siocs.getProcessingStatus())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(siocs.getChecksum())
                .checksumMatch(true)
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent missing(FlatPosTransaction xstore,
                                     String reconView,
                                     String simSource) {
        return base(xstore)
                .reconView(reconView)
                .simSource(simSource)
                .reconStatus(missingStatus(reconView))
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent matchedPos(FlatPosTransaction xstore,
                                        FlatPosTransaction counter,
                                        String reconView,
                                        String counterSource) {
        return base(xstore)
                .reconView(reconView)
                .simSource(counterSource)
                .reconStatus(ReconStatus.MATCHED.name())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(true)
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent softTtlWarning(
            FlatPosTransaction xstore,
            String reconView,
            String simSource) {
        return base(xstore)
                .reconView(reconView)
                .simSource(simSource)
                .reconStatus(ReconStatus.SOFT_TTL_WARNING.name())
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent duplicate(FlatSimTransaction siocs,
                                       String reconView) {
        return builder()
                .transactionKey(siocs.getTransactionKey())
                .externalId(siocs.getExternalId())
                .storeId(siocs.getStoreId())
                .wkstnId(siocs.getWkstnId() != null
                        ? siocs.getWkstnId()
                        : wkstnFromExternal(siocs.getExternalId()))
                .businessDate(siocs.getBusinessDate())
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(duplicateStatus(reconView))
                .processingStatus(siocs.getProcessingStatus())
                .duplicateFlag(true)
                .duplicatePostingCount(siocs.getDuplicatePostingCount())
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent processingFailed(
            FlatSimTransaction siocs,
            String reconView) {
        return builder()
                .transactionKey(siocs.getTransactionKey())
                .externalId(siocs.getExternalId())
                .storeId(siocs.getStoreId())
                .wkstnId(siocs.getWkstnId() != null
                        ? siocs.getWkstnId()
                        : wkstnFromExternal(siocs.getExternalId()))
                .businessDate(siocs.getBusinessDate())
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(processingFailedStatus(reconView))
                .processingStatus(siocs.getProcessingStatus())
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent reverted(FlatSimTransaction siocs,
                                      String reconView) {
        return builder()
                .transactionKey(siocs.getTransactionKey())
                .externalId(siocs.getExternalId())
                .storeId(siocs.getStoreId())
                .wkstnId(siocs.getWkstnId() != null
                        ? siocs.getWkstnId()
                        : wkstnFromExternal(siocs.getExternalId()))
                .businessDate(siocs.getBusinessDate())
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(revertedStatus(reconView))
                .processingStatus(siocs.getProcessingStatus())
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent processingPending(
            FlatSimTransaction siocs,
            String reconView) {
        return builder()
                .transactionKey(siocs.getTransactionKey())
                .externalId(siocs.getExternalId())
                .storeId(siocs.getStoreId())
                .wkstnId(siocs.getWkstnId() != null
                        ? siocs.getWkstnId()
                        : wkstnFromExternal(siocs.getExternalId()))
                .businessDate(siocs.getBusinessDate())
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(processingPendingStatus(reconView))
                .processingStatus(siocs.getProcessingStatus())
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent itemMissing(
            FlatPosTransaction xstore,
            FlatSimTransaction siocs,
            List<ItemDiscrepancy> disc,
            String reconView) {
        return base(xstore)
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(ReconStatus.ITEM_MISSING.name())
                .processingStatus(siocs.getProcessingStatus())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(siocs.getChecksum())
                .checksumMatch(false)
                .discrepancies(disc.toArray(new ItemDiscrepancy[0]))
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent quantityMismatch(
            FlatPosTransaction xstore,
            FlatSimTransaction siocs,
            List<ItemDiscrepancy> disc,
            String reconView) {
        return base(xstore)
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(ReconStatus.QUANTITY_MISMATCH.name())
                .processingStatus(siocs.getProcessingStatus())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(siocs.getChecksum())
                .checksumMatch(false)
                .discrepancies(disc.toArray(new ItemDiscrepancy[0]))
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent itemMissingPos(
            FlatPosTransaction xstore,
            FlatPosTransaction counter,
            List<ItemDiscrepancy> disc,
            String reconView,
            String counterSource) {
        return base(xstore)
                .reconView(reconView)
                .simSource(counterSource)
                .reconStatus(ReconStatus.ITEM_MISSING.name())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .discrepancies(disc.toArray(new ItemDiscrepancy[0]))
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent quantityMismatchPos(
            FlatPosTransaction xstore,
            FlatPosTransaction counter,
            List<ItemDiscrepancy> disc,
            String reconView,
            String counterSource) {
        return base(xstore)
                .reconView(reconView)
                .simSource(counterSource)
                .reconStatus(ReconStatus.QUANTITY_MISMATCH.name())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .discrepancies(disc.toArray(new ItemDiscrepancy[0]))
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent correctionMismatch(
            FlatSimTransaction siocs,
            String prevStatus,
            String prevChecksum,
            String reconView) {
        return builder()
                .transactionKey(siocs.getTransactionKey())
                .externalId(siocs.getExternalId())
                .storeId(siocs.getStoreId())
                .wkstnId(siocs.getWkstnId() != null
                        ? siocs.getWkstnId()
                        : wkstnFromExternal(siocs.getExternalId()))
                .businessDate(siocs.getBusinessDate())
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(correctedStatus(reconView))
                .processingStatus(siocs.getProcessingStatus())
                .siocsChecksum(siocs.getChecksum())
                .xstoreChecksum(prevChecksum)
                .checksumMatch(false)
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent lateCorrection(
            FlatSimTransaction siocs,
            String prevStatus,
            String reconView) {
        return builder()
                .transactionKey(siocs.getTransactionKey())
                .externalId(siocs.getExternalId())
                .storeId(siocs.getStoreId())
                .wkstnId(siocs.getWkstnId() != null
                        ? siocs.getWkstnId()
                        : wkstnFromExternal(siocs.getExternalId()))
                .businessDate(siocs.getBusinessDate())
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(lateMatchStatus(reconView))
                .processingStatus(siocs.getProcessingStatus())
                .reconciledAt(Instant.now().toString())
                .build();
    }

    private static String awaitingStatus(String reconView) {
        return "AWAITING_" + targetSystem(reconView);
    }

    private static String missingStatus(String reconView) {
        return "MISSING_IN_" + targetSystem(reconView);
    }

    private static String duplicateStatus(String reconView) {
        return "DUPLICATE_IN_" + targetSystem(reconView);
    }

    private static String processingPendingStatus(String reconView) {
        return "PROCESSING_PENDING_IN_" + targetSystem(reconView);
    }

    private static String processingFailedStatus(String reconView) {
        return "PROCESSING_FAILED_IN_" + targetSystem(reconView);
    }

    private static String revertedStatus(String reconView) {
        return "REVERTED_IN_" + targetSystem(reconView);
    }

    private static String correctedStatus(String reconView) {
        return "CORRECTED_IN_" + targetSystem(reconView);
    }

    private static String lateMatchStatus(String reconView) {
        return "LATE_MATCH_IN_" + targetSystem(reconView);
    }

    private static String targetSystem(String reconView) {
        if ("XSTORE_SIOCS".equalsIgnoreCase(reconView)) {
            return "SIOCS";
        }
        if ("XSTORE_XOCS".equalsIgnoreCase(reconView)) {
            return "XOCS";
        }
        return "SIM";
    }

    // Helper to derive wkstnId from externalId
    private static String wkstnFromExternal(String externalId) {
        if (externalId == null || externalId.length() != 22)
            return null;
        try {
            return String.valueOf(
                    Integer.parseInt(externalId.substring(5, 8)));
        } catch (Exception e) {
            return null;
        }
    }

    // ── Builder ────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    // base() populates all xstore fields including wkstnId
    private static Builder base(FlatPosTransaction xstore) {
        return builder()
                .transactionKey(xstore.getTransactionKey())
                .externalId(xstore.getExternalId())
                .storeId(xstore.getStoreId())
                .wkstnId(String.valueOf(xstore.getWkstnId()))  // ADDED
                .businessDate(xstore.getBusinessDate())
                .transactionType(xstore.getTransactionType())
                .xstoreChecksum(xstore.getChecksum());
    }

    public static class Builder {
        private final ReconEvent e = new ReconEvent();

        public Builder transactionKey(String v) {
            e.transactionKey = v;
            return this;
        }

        public Builder externalId(String v) {
            e.externalId = v;
            return this;
        }

        public Builder storeId(String v) {
            e.storeId = v;
            return this;
        }

        public Builder wkstnId(String v) {           // ADDED
            e.wkstnId = v;
            return this;
        }

        public Builder businessDate(String v) {
            e.businessDate = v;
            return this;
        }

        public Builder reconView(String v) {
            e.reconView = v;
            return this;
        }

        public Builder simSource(String v) {
            e.simSource = v;
            return this;
        }

        public Builder reconStatus(String v) {
            e.reconStatus = v;
            return this;
        }

        public Builder transactionType(String v) {
            e.transactionType = v;
            return this;
        }

        public Builder processingStatus(Integer v) {
            e.processingStatus = v;
            return this;
        }

        public Builder xstoreChecksum(String v) {
            e.xstoreChecksum = v;
            return this;
        }

        public Builder siocsChecksum(String v) {
            e.siocsChecksum = v;
            return this;
        }

        public Builder checksumMatch(boolean v) {
            e.checksumMatch = v;
            return this;
        }

        public Builder discrepancies(ItemDiscrepancy[] v) {
            e.discrepancies = v;
            return this;
        }

        public Builder duplicateFlag(boolean v) {
            e.duplicateFlag = v;
            return this;
        }

        public Builder duplicatePostingCount(int v) {
            e.duplicatePostingCount = v;
            return this;
        }

        public Builder reconciledAt(String v) {
            e.reconciledAt = v;
            return this;
        }

        public Builder timerDriftMs(long v) {
            e.timerDriftMs = v;
            return this;
        }

        public ReconEvent build() {
            return e;
        }
    }

    // ── Getters and Setters ────────────────────────────────────────

    public String getTransactionKey() {
        return transactionKey;
    }

    public void setTransactionKey(String v) {
        transactionKey = v;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String v) {
        externalId = v;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String v) {
        storeId = v;
    }

    public String getWkstnId() {
        return wkstnId;
    }      // ADDED

    public void setWkstnId(String v) {
        wkstnId = v;
    }  // ADDED

    public String getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(String v) {
        businessDate = v;
    }

    public String getReconStatus() {
        return reconStatus;
    }

    public void setReconStatus(String v) {
        reconStatus = v;
    }

    public String getReconView() {
        return reconView;
    }

    public void setReconView(String reconView) {
        this.reconView = reconView;
    }

    public String getSimSource() {
        return simSource;
    }

    public void setSimSource(String simSource) {
        this.simSource = simSource;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String v) {
        transactionType = v;
    }

    public Integer getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(Integer v) {
        processingStatus = v;
    }

    public String getXstoreChecksum() {
        return xstoreChecksum;
    }

    public void setXstoreChecksum(String v) {
        xstoreChecksum = v;
    }

    public String getSiocsChecksum() {
        return siocsChecksum;
    }

    public void setSiocsChecksum(String v) {
        siocsChecksum = v;
    }

    public boolean isChecksumMatch() {
        return checksumMatch;
    }

    public void setChecksumMatch(boolean v) {
        checksumMatch = v;
    }

    public ItemDiscrepancy[] getDiscrepancies() {
        return discrepancies;
    }

    public void setDiscrepancies(ItemDiscrepancy[] v) {
        discrepancies = v;
    }

    public boolean isDuplicateFlag() {
        return duplicateFlag;
    }

    public void setDuplicateFlag(boolean v) {
        duplicateFlag = v;
    }

    public int getDuplicatePostingCount() {
        return duplicatePostingCount;
    }

    public void setDuplicatePostingCount(int v) {
        duplicatePostingCount = v;
    }

    public String getReconciledAt() {
        return reconciledAt;
    }

    public void setReconciledAt(String v) {
        reconciledAt = v;
    }

    public long getTimerDriftMs() {
        return timerDriftMs;
    }

    public void setTimerDriftMs(long v) {
        timerDriftMs = v;
    }
}
