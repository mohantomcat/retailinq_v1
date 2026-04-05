package com.recon.flink.domain;

import com.recon.flink.util.InventoryBusinessContext;
import com.recon.flink.util.InventoryBusinessContextFactory;
import com.recon.flink.util.InventoryDirectionProfile;
import com.recon.flink.util.InventoryDirectionResolver;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
    private String tenantId;
    private String transactionFamily;
    private String transactionPhase;
    private String originSystem;
    private String counterpartySystem;
    private boolean directionResolved;
    private String businessReference;
    private String headerMatchKey;
    private String aggregateKey;
    private boolean sourcePresent;
    private boolean targetPresent;
    private String sourceDocumentRef;
    private String targetDocumentRef;
    private String[] sourceLineMatchKeys;
    private String[] targetLineMatchKeys;
    private Integer sourceItemCount;
    private Integer targetItemCount;
    private BigDecimal sourceTotalQuantity;
    private BigDecimal targetTotalQuantity;
    private BigDecimal quantityVariance;
    private boolean quantityMetricsAvailable;
    private boolean valueMetricsAvailable;
    private Integer processingStatus;

    private String xstoreChecksum;
    private String siocsChecksum;
    private boolean checksumMatch;

    private ItemDiscrepancy[] discrepancies;

    private boolean duplicateFlag;
    private int duplicatePostingCount;
    private BigDecimal transactionAmount;
    private BigDecimal amountVariance;
    private BigDecimal amountVariancePercent;
    private Integer lineItemCount;
    private Integer affectedItemCount;
    private BigDecimal totalQuantity;
    private BigDecimal quantityImpact;

    private Integer matchScore;
    private String matchBand;
    private String matchRule;
    private String matchSummary;
    private boolean toleranceApplied;
    private String toleranceProfile;
    private Integer matchedLineCount;
    private Integer discrepantLineCount;
    private Integer toleratedDiscrepancyCount;
    private Integer materialDiscrepancyCount;
    private BigDecimal quantityVariancePercent;

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
        return matched(xstore, siocs, reconView, List.of(), null);
    }

    public static ReconEvent matched(FlatPosTransaction xstore,
                                     FlatSimTransaction siocs,
                                     String reconView,
                                     List<ItemDiscrepancy> discrepancies,
                                     MatchEvaluation evaluation) {
        Builder builder = base(xstore)
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(ReconStatus.MATCHED.name())
                .processingStatus(siocs.getProcessingStatus())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(siocs.getChecksum())
                .checksumMatch(Objects.equals(xstore.getChecksum(), siocs.getChecksum()))
                .affectedItemCount(discrepancyCount(discrepancies))
                .quantityImpact(quantityImpact(discrepancies))
                .discrepancies(toArray(discrepancies))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
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

    public static ReconEvent awaitingInventory(FlatSimTransaction source,
                                               String reconView,
                                               String counterSource) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, null);
        return applyInventoryContext(base(source), source, null, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(awaitingStatus(direction))
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent missingInventory(FlatSimTransaction source,
                                              String reconView,
                                              String counterSource) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, null);
        return applyInventoryContext(base(source), source, null, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(missingStatus(direction))
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent matchedInventory(FlatSimTransaction source,
                                              FlatSimTransaction counter,
                                              String reconView,
                                              List<ItemDiscrepancy> discrepancies,
                                              MatchEvaluation evaluation) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, counter);
        Builder builder = applyInventoryContext(base(source), source, counter, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(ReconStatus.MATCHED.name())
                .processingStatus(counter.getProcessingStatus())
                .xstoreChecksum(source.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(Objects.equals(source.getChecksum(), counter.getChecksum()))
                .affectedItemCount(discrepancyCount(discrepancies))
                .quantityImpact(quantityImpact(discrepancies))
                .discrepancies(toArray(discrepancies))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent itemMissingInventory(FlatSimTransaction source,
                                                  FlatSimTransaction counter,
                                                  List<ItemDiscrepancy> disc,
                                                  String reconView,
                                                  MatchEvaluation evaluation) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, counter);
        Builder builder = applyInventoryContext(base(source), source, counter, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(ReconStatus.ITEM_MISSING.name())
                .processingStatus(counter.getProcessingStatus())
                .xstoreChecksum(source.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .affectedItemCount(discrepancyCount(disc))
                .quantityImpact(quantityImpact(disc))
                .discrepancies(toArray(disc))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent quantityMismatchInventory(FlatSimTransaction source,
                                                       FlatSimTransaction counter,
                                                       List<ItemDiscrepancy> disc,
                                                       String reconView,
                                                       MatchEvaluation evaluation) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, counter);
        Builder builder = applyInventoryContext(base(source), source, counter, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(ReconStatus.QUANTITY_MISMATCH.name())
                .processingStatus(counter.getProcessingStatus())
                .xstoreChecksum(source.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .affectedItemCount(discrepancyCount(disc))
                .quantityImpact(quantityImpact(disc))
                .discrepancies(toArray(disc))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent matchedPos(FlatPosTransaction xstore,
                                        FlatPosTransaction counter,
                                        String reconView,
                                        String counterSource) {
        return matchedPos(xstore, counter, reconView, counterSource, List.of(), null, null, null);
    }

    public static ReconEvent matchedPos(FlatPosTransaction xstore,
                                        FlatPosTransaction counter,
                                        String reconView,
                                        String counterSource,
                                        List<ItemDiscrepancy> discrepancies,
                                        MatchEvaluation evaluation,
                                        BigDecimal amountVariance,
                                        BigDecimal amountVariancePercent) {
        Builder builder = base(xstore)
                .reconView(reconView)
                .simSource(counterSource)
                .reconStatus(ReconStatus.MATCHED.name())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(Objects.equals(xstore.getChecksum(), counter.getChecksum()))
                .amountVariance(amountVariance)
                .amountVariancePercent(amountVariancePercent)
                .affectedItemCount(discrepancyCount(discrepancies))
                .quantityImpact(quantityImpact(discrepancies))
                .discrepancies(toArray(discrepancies))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
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
        return duplicate(siocs, reconView, null);
    }

    public static ReconEvent duplicate(FlatSimTransaction siocs,
                                       String reconView,
                                       MatchEvaluation evaluation) {
        Builder builder = builder()
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
                .lineItemCount(siocs.getLineItemCount())
                .totalQuantity(siocs.getTotalQuantity())
                .duplicateFlag(true)
                .duplicatePostingCount(siocs.getDuplicatePostingCount())
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent duplicateInventory(FlatSimTransaction source,
                                                FlatSimTransaction counter,
                                                String reconView,
                                                MatchEvaluation evaluation) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, counter);
        Builder builder = applyInventoryContext(base(source), source, counter, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(duplicateStatus(direction))
                .processingStatus(counter.getProcessingStatus())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .duplicateFlag(true)
                .duplicatePostingCount(counter.getDuplicatePostingCount())
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
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
                .lineItemCount(siocs.getLineItemCount())
                .totalQuantity(siocs.getTotalQuantity())
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
                .lineItemCount(siocs.getLineItemCount())
                .totalQuantity(siocs.getTotalQuantity())
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
                .lineItemCount(siocs.getLineItemCount())
                .totalQuantity(siocs.getTotalQuantity())
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent itemMissing(
            FlatPosTransaction xstore,
            FlatSimTransaction siocs,
            List<ItemDiscrepancy> disc,
            String reconView) {
        return itemMissing(xstore, siocs, disc, reconView, null);
    }

    public static ReconEvent itemMissing(
            FlatPosTransaction xstore,
            FlatSimTransaction siocs,
            List<ItemDiscrepancy> disc,
            String reconView,
            MatchEvaluation evaluation) {
        Builder builder = base(xstore)
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(ReconStatus.ITEM_MISSING.name())
                .processingStatus(siocs.getProcessingStatus())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(siocs.getChecksum())
                .checksumMatch(false)
                .affectedItemCount(discrepancyCount(disc))
                .quantityImpact(quantityImpact(disc))
                .discrepancies(toArray(disc))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent quantityMismatch(
            FlatPosTransaction xstore,
            FlatSimTransaction siocs,
            List<ItemDiscrepancy> disc,
            String reconView) {
        return quantityMismatch(xstore, siocs, disc, reconView, null);
    }

    public static ReconEvent quantityMismatch(
            FlatPosTransaction xstore,
            FlatSimTransaction siocs,
            List<ItemDiscrepancy> disc,
            String reconView,
            MatchEvaluation evaluation) {
        Builder builder = base(xstore)
                .reconView(reconView)
                .simSource(siocs.getSource())
                .reconStatus(ReconStatus.QUANTITY_MISMATCH.name())
                .processingStatus(siocs.getProcessingStatus())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(siocs.getChecksum())
                .checksumMatch(false)
                .affectedItemCount(discrepancyCount(disc))
                .quantityImpact(quantityImpact(disc))
                .discrepancies(toArray(disc))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent itemMissingPos(
            FlatPosTransaction xstore,
            FlatPosTransaction counter,
            List<ItemDiscrepancy> disc,
            String reconView,
            String counterSource) {
        return itemMissingPos(xstore, counter, disc, reconView, counterSource, null);
    }

    public static ReconEvent itemMissingPos(
            FlatPosTransaction xstore,
            FlatPosTransaction counter,
            List<ItemDiscrepancy> disc,
            String reconView,
            String counterSource,
            MatchEvaluation evaluation) {
        Builder builder = base(xstore)
                .reconView(reconView)
                .simSource(counterSource)
                .reconStatus(ReconStatus.ITEM_MISSING.name())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .affectedItemCount(discrepancyCount(disc))
                .quantityImpact(quantityImpact(disc))
                .discrepancies(toArray(disc))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent quantityMismatchPos(
            FlatPosTransaction xstore,
            FlatPosTransaction counter,
            List<ItemDiscrepancy> disc,
            String reconView,
            String counterSource) {
        return quantityMismatchPos(xstore, counter, disc, reconView, counterSource, null, null, null);
    }

    public static ReconEvent quantityMismatchPos(
            FlatPosTransaction xstore,
            FlatPosTransaction counter,
            List<ItemDiscrepancy> disc,
            String reconView,
            String counterSource,
            MatchEvaluation evaluation,
            BigDecimal amountVariance,
            BigDecimal amountVariancePercent) {
        Builder builder = base(xstore)
                .reconView(reconView)
                .simSource(counterSource)
                .reconStatus(ReconStatus.QUANTITY_MISMATCH.name())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .amountVariance(amountVariance)
                .amountVariancePercent(amountVariancePercent)
                .affectedItemCount(discrepancyCount(disc))
                .quantityImpact(quantityImpact(disc))
                .discrepancies(toArray(disc))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent totalMismatchPos(
            FlatPosTransaction xstore,
            FlatPosTransaction counter,
            String reconView,
            String counterSource) {
        return totalMismatchPos(
                xstore,
                counter,
                List.of(),
                reconView,
                counterSource,
                null,
                amountVariance(xstore.getTotalAmount(), counter.getTotalAmount()),
                null
        );
    }

    public static ReconEvent totalMismatchPos(
            FlatPosTransaction xstore,
            FlatPosTransaction counter,
            List<ItemDiscrepancy> disc,
            String reconView,
            String counterSource,
            MatchEvaluation evaluation,
            BigDecimal amountVariance,
            BigDecimal amountVariancePercent) {
        Builder builder = base(xstore)
                .reconView(reconView)
                .simSource(counterSource)
                .reconStatus(ReconStatus.TOTAL_MISMATCH.name())
                .xstoreChecksum(xstore.getChecksum())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .amountVariance(amountVariance != null
                        ? amountVariance
                        : amountVariance(xstore.getTotalAmount(), counter.getTotalAmount()))
                .amountVariancePercent(amountVariancePercent)
                .affectedItemCount(discrepancyCount(disc))
                .quantityImpact(quantityImpact(disc))
                .discrepancies(toArray(disc))
                .reconciledAt(Instant.now().toString());
        return applyMatchEvaluation(builder, evaluation).build();
    }

    public static ReconEvent correctionMismatch(
            FlatSimTransaction siocs,
            String prevStatus,
            String prevChecksum,
            String reconView) {
        return correctionMismatch(siocs, prevStatus, prevChecksum, reconView, null);
    }

    public static ReconEvent correctionMismatch(
            FlatSimTransaction siocs,
            String prevStatus,
            String prevChecksum,
            String reconView,
            String counterpartySystem) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, null, siocs);
        String effectiveCounterparty = counterpartySystem != null ? counterpartySystem : direction.counterpartySystem();
        return applyInventoryContext(builder(), null, siocs, reconView, direction)
                .transactionKey(siocs.getTransactionKey())
                .externalId(siocs.getExternalId())
                .storeId(siocs.getStoreId())
                .wkstnId(siocs.getWkstnId() != null
                        ? siocs.getWkstnId()
                        : wkstnFromExternal(siocs.getExternalId()))
                .businessDate(siocs.getBusinessDate())
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(correctedStatusForCounterparty(effectiveCounterparty))
                .processingStatus(siocs.getProcessingStatus())
                .lineItemCount(siocs.getLineItemCount())
                .totalQuantity(siocs.getTotalQuantity())
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
        return lateCorrection(siocs, prevStatus, reconView, null);
    }

    public static ReconEvent lateCorrection(
            FlatSimTransaction siocs,
            String prevStatus,
            String reconView,
            String counterpartySystem) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, null, siocs);
        String effectiveCounterparty = counterpartySystem != null ? counterpartySystem : direction.counterpartySystem();
        return applyInventoryContext(builder(), null, siocs, reconView, direction)
                .transactionKey(siocs.getTransactionKey())
                .externalId(siocs.getExternalId())
                .storeId(siocs.getStoreId())
                .wkstnId(siocs.getWkstnId() != null
                        ? siocs.getWkstnId()
                        : wkstnFromExternal(siocs.getExternalId()))
                .businessDate(siocs.getBusinessDate())
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(lateMatchStatusForCounterparty(effectiveCounterparty))
                .processingStatus(siocs.getProcessingStatus())
                .lineItemCount(siocs.getLineItemCount())
                .totalQuantity(siocs.getTotalQuantity())
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent processingPendingInventory(FlatSimTransaction source,
                                                        FlatSimTransaction counter,
                                                        String reconView) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, counter);
        return applyInventoryContext(base(counter), source, counter, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(processingPendingStatus(direction))
                .processingStatus(counter.getProcessingStatus())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent revertedInventory(FlatSimTransaction source,
                                               FlatSimTransaction counter,
                                               String reconView) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, counter);
        return applyInventoryContext(base(counter), source, counter, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(revertedStatus(direction))
                .processingStatus(counter.getProcessingStatus())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .reconciledAt(Instant.now().toString())
                .build();
    }

    public static ReconEvent processingFailedInventory(FlatSimTransaction source,
                                                       FlatSimTransaction counter,
                                                       String reconView) {
        InventoryDirectionProfile direction = inventoryDirection(reconView, source, counter);
        return applyInventoryContext(base(counter), source, counter, reconView, direction)
                .reconView(reconView)
                .simSource(direction.originSystem())
                .reconStatus(processingFailedStatus(direction))
                .processingStatus(counter.getProcessingStatus())
                .siocsChecksum(counter.getChecksum())
                .checksumMatch(false)
                .reconciledAt(Instant.now().toString())
                .build();
    }

    private static String awaitingStatus(String reconView) {
        return "AWAITING_" + targetSystem(reconView);
    }

    private static String awaitingStatus(InventoryDirectionProfile direction) {
        return direction.awaitingStatus();
    }

    private static String missingStatus(String reconView) {
        return "MISSING_IN_" + targetSystem(reconView);
    }

    private static String missingStatus(InventoryDirectionProfile direction) {
        return direction.missingStatus();
    }

    private static String duplicateStatus(String reconView) {
        return "DUPLICATE_IN_" + targetSystem(reconView);
    }

    private static String duplicateStatus(InventoryDirectionProfile direction) {
        return direction.duplicateStatus();
    }

    private static String processingPendingStatus(String reconView) {
        return "PROCESSING_PENDING_IN_" + targetSystem(reconView);
    }

    private static String processingPendingStatus(InventoryDirectionProfile direction) {
        return direction.processingPendingStatus();
    }

    private static String processingFailedStatus(String reconView) {
        return "PROCESSING_FAILED_IN_" + targetSystem(reconView);
    }

    private static String processingFailedStatus(InventoryDirectionProfile direction) {
        return direction.processingFailedStatus();
    }

    private static String revertedStatus(String reconView) {
        return "REVERTED_IN_" + targetSystem(reconView);
    }

    private static String revertedStatus(InventoryDirectionProfile direction) {
        return direction.revertedStatus();
    }

    private static String correctedStatus(String reconView) {
        return "CORRECTED_IN_" + targetSystem(reconView);
    }

    private static String correctedStatusForCounterparty(String counterpartySystem) {
        return "CORRECTED_IN_" + counterpartySystem;
    }

    private static String lateMatchStatus(String reconView) {
        return "LATE_MATCH_IN_" + targetSystem(reconView);
    }

    private static String lateMatchStatusForCounterparty(String counterpartySystem) {
        return "LATE_MATCH_IN_" + counterpartySystem;
    }

    private static String targetSystem(String reconView) {
        if ("XSTORE_SIOCS".equalsIgnoreCase(reconView)) {
            return "SIOCS";
        }
        if ("XSTORE_XOCS".equalsIgnoreCase(reconView)) {
            return "XOCS";
        }
        if ("SIOCS_MFCS".equalsIgnoreCase(reconView)) {
            return "MFCS";
        }
        if ("SIM_MFCS".equalsIgnoreCase(reconView)) {
            return "MFCS";
        }
        if ("SIM_RMS".equalsIgnoreCase(reconView)) {
            return "RMS";
        }
        if ("SIOCS_RMS".equalsIgnoreCase(reconView)) {
            return "RMS";
        }
        return "SIM";
    }

    // Helper to derive wkstnId from externalId
    private static String wkstnFromExternal(String externalId) {
        if (externalId == null || externalId.length() != 22) {
            return null;
        }
        try {
            return String.valueOf(Integer.parseInt(externalId.substring(5, 8)));
        } catch (Exception ignored) {
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
                .wkstnId(String.valueOf(xstore.getWkstnId()))
                .businessDate(xstore.getBusinessDate())
                .transactionType(xstore.getTransactionType())
                .transactionAmount(xstore.getTotalAmount())
                .lineItemCount(lineItemCount(xstore))
                .totalQuantity(totalQuantity(xstore))
                .xstoreChecksum(xstore.getChecksum());
    }

    private static Builder base(FlatSimTransaction source) {
        return builder()
                .transactionKey(source.getTransactionKey())
                .externalId(source.getExternalId())
                .tenantId(source.getTenantId())
                .storeId(source.getStoreId())
                .wkstnId(source.getWkstnId())
                .businessDate(source.getBusinessDate())
                .transactionType(source.getTransactionTypeDesc() != null
                        ? source.getTransactionTypeDesc()
                        : source.getTransactionType() != null ? String.valueOf(source.getTransactionType()) : null)
                .lineItemCount(source.getLineItemCount())
                .totalQuantity(source.getTotalQuantity())
                .xstoreChecksum(source.getChecksum());
    }

    private static Builder applyInventoryBusinessContext(Builder builder,
                                                         FlatSimTransaction source,
                                                         FlatSimTransaction target) {
        InventoryBusinessContext context = InventoryBusinessContextFactory.from(source, target);
        return builder
                .tenantId(context.tenantId())
                .transactionFamily(context.transactionFamily())
                .transactionPhase(context.transactionPhase())
                .businessReference(context.businessReference())
                .headerMatchKey(context.headerMatchKey())
                .aggregateKey(context.aggregateKey())
                .sourcePresent(source != null)
                .targetPresent(target != null)
                .sourceDocumentRef(context.sourceDocumentRef())
                .targetDocumentRef(context.targetDocumentRef())
                .sourceLineMatchKeys(toKeyArray(context.sourceLineMatchKeys()))
                .targetLineMatchKeys(toKeyArray(context.targetLineMatchKeys()))
                .sourceItemCount(context.sourceItemCount())
                .targetItemCount(context.targetItemCount())
                .sourceTotalQuantity(context.sourceTotalQuantity())
                .targetTotalQuantity(context.targetTotalQuantity())
                .quantityVariance(context.quantityVariance())
                .quantityMetricsAvailable(context.quantityMetricsAvailable())
                .valueMetricsAvailable(context.valueMetricsAvailable());
    }

    private static Builder applyInventoryContext(Builder builder,
                                                 FlatSimTransaction source,
                                                 FlatSimTransaction target,
                                                 String reconView,
                                                 InventoryDirectionProfile direction) {
        Builder contextBuilder = applyInventoryBusinessContext(builder, source, target)
                .originSystem(direction.originSystem())
                .counterpartySystem(direction.counterpartySystem())
                .directionResolved(direction.resolved());
        if (direction.resolved() && direction.transactionPhase() != null) {
            contextBuilder.transactionPhase(direction.transactionPhase().name());
        }
        return contextBuilder;
    }

    private static InventoryDirectionProfile inventoryDirection(String reconView,
                                                                FlatSimTransaction source,
                                                                FlatSimTransaction target) {
        return InventoryDirectionResolver.resolve(reconView, source, target);
    }

    private static Builder applyMatchEvaluation(Builder builder, MatchEvaluation evaluation) {
        if (evaluation == null) {
            return builder;
        }
        return builder
                .matchScore(evaluation.matchScore())
                .matchBand(evaluation.matchBand())
                .matchRule(evaluation.matchRule())
                .matchSummary(evaluation.matchSummary())
                .toleranceApplied(evaluation.toleranceApplied())
                .toleranceProfile(evaluation.toleranceProfile())
                .matchedLineCount(evaluation.matchedLineCount())
                .discrepantLineCount(evaluation.discrepantLineCount())
                .toleratedDiscrepancyCount(evaluation.toleratedDiscrepancyCount())
                .materialDiscrepancyCount(evaluation.materialDiscrepancyCount())
                .quantityVariancePercent(evaluation.quantityVariancePercent())
                .amountVariancePercent(evaluation.amountVariancePercent());
    }

    private static Integer lineItemCount(FlatPosTransaction xstore) {
        return xstore.getLineItems() != null ? xstore.getLineItems().length : null;
    }

    private static BigDecimal totalQuantity(FlatPosTransaction xstore) {
        if (xstore.getLineItems() == null || xstore.getLineItems().length == 0) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (FlatLineItem lineItem : xstore.getLineItems()) {
            if (lineItem != null && lineItem.getQuantity() != null) {
                total = total.add(lineItem.getQuantity().abs());
            }
        }
        return total.compareTo(BigDecimal.ZERO) > 0 ? total : null;
    }

    private static Integer discrepancyCount(List<ItemDiscrepancy> discrepancies) {
        return discrepancies != null ? discrepancies.size() : 0;
    }

    private static ItemDiscrepancy[] toArray(List<ItemDiscrepancy> discrepancies) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return new ItemDiscrepancy[0];
        }
        return discrepancies.toArray(new ItemDiscrepancy[0]);
    }

    private static BigDecimal quantityImpact(List<ItemDiscrepancy> discrepancies) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ItemDiscrepancy discrepancy : discrepancies) {
            if (discrepancy == null) {
                continue;
            }
            BigDecimal left = discrepancy.getXstoreQuantity();
            BigDecimal right = discrepancy.getSiocsQuantity();
            if (left != null && right != null) {
                total = total.add(left.subtract(right).abs());
            } else if (left != null) {
                total = total.add(left.abs());
            } else if (right != null) {
                total = total.add(right.abs());
            }
        }
        return total.compareTo(BigDecimal.ZERO) > 0 ? total : null;
    }

    private static BigDecimal amountVariance(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.subtract(right).abs();
    }

    private static String[] toKeyArray(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new String[0];
        }
        return keys.toArray(new String[0]);
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

        public Builder tenantId(String v) {
            e.tenantId = v;
            return this;
        }

        public Builder transactionFamily(String v) {
            e.transactionFamily = v;
            return this;
        }

        public Builder transactionPhase(String v) {
            e.transactionPhase = v;
            return this;
        }

        public Builder originSystem(String v) {
            e.originSystem = v;
            return this;
        }

        public Builder counterpartySystem(String v) {
            e.counterpartySystem = v;
            return this;
        }

        public Builder directionResolved(boolean v) {
            e.directionResolved = v;
            return this;
        }

        public Builder businessReference(String v) {
            e.businessReference = v;
            return this;
        }

        public Builder headerMatchKey(String v) {
            e.headerMatchKey = v;
            return this;
        }

        public Builder aggregateKey(String v) {
            e.aggregateKey = v;
            return this;
        }

        public Builder sourcePresent(boolean v) {
            e.sourcePresent = v;
            return this;
        }

        public Builder targetPresent(boolean v) {
            e.targetPresent = v;
            return this;
        }

        public Builder sourceDocumentRef(String v) {
            e.sourceDocumentRef = v;
            return this;
        }

        public Builder targetDocumentRef(String v) {
            e.targetDocumentRef = v;
            return this;
        }

        public Builder sourceLineMatchKeys(String[] v) {
            e.sourceLineMatchKeys = v;
            return this;
        }

        public Builder targetLineMatchKeys(String[] v) {
            e.targetLineMatchKeys = v;
            return this;
        }

        public Builder sourceItemCount(Integer v) {
            e.sourceItemCount = v;
            return this;
        }

        public Builder targetItemCount(Integer v) {
            e.targetItemCount = v;
            return this;
        }

        public Builder sourceTotalQuantity(BigDecimal v) {
            e.sourceTotalQuantity = v;
            return this;
        }

        public Builder targetTotalQuantity(BigDecimal v) {
            e.targetTotalQuantity = v;
            return this;
        }

        public Builder quantityVariance(BigDecimal v) {
            e.quantityVariance = v;
            return this;
        }

        public Builder quantityMetricsAvailable(boolean v) {
            e.quantityMetricsAvailable = v;
            return this;
        }

        public Builder valueMetricsAvailable(boolean v) {
            e.valueMetricsAvailable = v;
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

        public Builder transactionAmount(BigDecimal v) {
            e.transactionAmount = v;
            return this;
        }

        public Builder amountVariance(BigDecimal v) {
            e.amountVariance = v;
            return this;
        }

        public Builder amountVariancePercent(BigDecimal v) {
            e.amountVariancePercent = v;
            return this;
        }

        public Builder lineItemCount(Integer v) {
            e.lineItemCount = v;
            return this;
        }

        public Builder affectedItemCount(Integer v) {
            e.affectedItemCount = v;
            return this;
        }

        public Builder totalQuantity(BigDecimal v) {
            e.totalQuantity = v;
            return this;
        }

        public Builder quantityImpact(BigDecimal v) {
            e.quantityImpact = v;
            return this;
        }

        public Builder matchScore(Integer v) {
            e.matchScore = v;
            return this;
        }

        public Builder matchBand(String v) {
            e.matchBand = v;
            return this;
        }

        public Builder matchRule(String v) {
            e.matchRule = v;
            return this;
        }

        public Builder matchSummary(String v) {
            e.matchSummary = v;
            return this;
        }

        public Builder toleranceApplied(boolean v) {
            e.toleranceApplied = v;
            return this;
        }

        public Builder toleranceProfile(String v) {
            e.toleranceProfile = v;
            return this;
        }

        public Builder matchedLineCount(Integer v) {
            e.matchedLineCount = v;
            return this;
        }

        public Builder discrepantLineCount(Integer v) {
            e.discrepantLineCount = v;
            return this;
        }

        public Builder toleratedDiscrepancyCount(Integer v) {
            e.toleratedDiscrepancyCount = v;
            return this;
        }

        public Builder materialDiscrepancyCount(Integer v) {
            e.materialDiscrepancyCount = v;
            return this;
        }

        public Builder quantityVariancePercent(BigDecimal v) {
            e.quantityVariancePercent = v;
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTransactionFamily() {
        return transactionFamily;
    }

    public void setTransactionFamily(String transactionFamily) {
        this.transactionFamily = transactionFamily;
    }

    public String getTransactionPhase() {
        return transactionPhase;
    }

    public void setTransactionPhase(String transactionPhase) {
        this.transactionPhase = transactionPhase;
    }

    public String getOriginSystem() {
        return originSystem;
    }

    public void setOriginSystem(String originSystem) {
        this.originSystem = originSystem;
    }

    public String getCounterpartySystem() {
        return counterpartySystem;
    }

    public void setCounterpartySystem(String counterpartySystem) {
        this.counterpartySystem = counterpartySystem;
    }

    public boolean isDirectionResolved() {
        return directionResolved;
    }

    public void setDirectionResolved(boolean directionResolved) {
        this.directionResolved = directionResolved;
    }

    public String getBusinessReference() {
        return businessReference;
    }

    public void setBusinessReference(String businessReference) {
        this.businessReference = businessReference;
    }

    public String getHeaderMatchKey() {
        return headerMatchKey;
    }

    public void setHeaderMatchKey(String headerMatchKey) {
        this.headerMatchKey = headerMatchKey;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public void setAggregateKey(String aggregateKey) {
        this.aggregateKey = aggregateKey;
    }

    public boolean isSourcePresent() {
        return sourcePresent;
    }

    public void setSourcePresent(boolean sourcePresent) {
        this.sourcePresent = sourcePresent;
    }

    public boolean isTargetPresent() {
        return targetPresent;
    }

    public void setTargetPresent(boolean targetPresent) {
        this.targetPresent = targetPresent;
    }

    public String getSourceDocumentRef() {
        return sourceDocumentRef;
    }

    public void setSourceDocumentRef(String sourceDocumentRef) {
        this.sourceDocumentRef = sourceDocumentRef;
    }

    public String getTargetDocumentRef() {
        return targetDocumentRef;
    }

    public void setTargetDocumentRef(String targetDocumentRef) {
        this.targetDocumentRef = targetDocumentRef;
    }

    public String[] getSourceLineMatchKeys() {
        return sourceLineMatchKeys;
    }

    public void setSourceLineMatchKeys(String[] sourceLineMatchKeys) {
        this.sourceLineMatchKeys = sourceLineMatchKeys;
    }

    public String[] getTargetLineMatchKeys() {
        return targetLineMatchKeys;
    }

    public void setTargetLineMatchKeys(String[] targetLineMatchKeys) {
        this.targetLineMatchKeys = targetLineMatchKeys;
    }

    public Integer getSourceItemCount() {
        return sourceItemCount;
    }

    public void setSourceItemCount(Integer sourceItemCount) {
        this.sourceItemCount = sourceItemCount;
    }

    public Integer getTargetItemCount() {
        return targetItemCount;
    }

    public void setTargetItemCount(Integer targetItemCount) {
        this.targetItemCount = targetItemCount;
    }

    public BigDecimal getSourceTotalQuantity() {
        return sourceTotalQuantity;
    }

    public void setSourceTotalQuantity(BigDecimal sourceTotalQuantity) {
        this.sourceTotalQuantity = sourceTotalQuantity;
    }

    public BigDecimal getTargetTotalQuantity() {
        return targetTotalQuantity;
    }

    public void setTargetTotalQuantity(BigDecimal targetTotalQuantity) {
        this.targetTotalQuantity = targetTotalQuantity;
    }

    public BigDecimal getQuantityVariance() {
        return quantityVariance;
    }

    public void setQuantityVariance(BigDecimal quantityVariance) {
        this.quantityVariance = quantityVariance;
    }

    public boolean isQuantityMetricsAvailable() {
        return quantityMetricsAvailable;
    }

    public void setQuantityMetricsAvailable(boolean quantityMetricsAvailable) {
        this.quantityMetricsAvailable = quantityMetricsAvailable;
    }

    public boolean isValueMetricsAvailable() {
        return valueMetricsAvailable;
    }

    public void setValueMetricsAvailable(boolean valueMetricsAvailable) {
        this.valueMetricsAvailable = valueMetricsAvailable;
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

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(BigDecimal v) {
        transactionAmount = v;
    }

    public BigDecimal getAmountVariance() {
        return amountVariance;
    }

    public void setAmountVariance(BigDecimal v) {
        amountVariance = v;
    }

    public BigDecimal getAmountVariancePercent() {
        return amountVariancePercent;
    }

    public void setAmountVariancePercent(BigDecimal v) {
        amountVariancePercent = v;
    }

    public Integer getLineItemCount() {
        return lineItemCount;
    }

    public void setLineItemCount(Integer v) {
        lineItemCount = v;
    }

    public Integer getAffectedItemCount() {
        return affectedItemCount;
    }

    public void setAffectedItemCount(Integer v) {
        affectedItemCount = v;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal v) {
        totalQuantity = v;
    }

    public BigDecimal getQuantityImpact() {
        return quantityImpact;
    }

    public void setQuantityImpact(BigDecimal v) {
        quantityImpact = v;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer v) {
        matchScore = v;
    }

    public String getMatchBand() {
        return matchBand;
    }

    public void setMatchBand(String v) {
        matchBand = v;
    }

    public String getMatchRule() {
        return matchRule;
    }

    public void setMatchRule(String v) {
        matchRule = v;
    }

    public String getMatchSummary() {
        return matchSummary;
    }

    public void setMatchSummary(String v) {
        matchSummary = v;
    }

    public boolean isToleranceApplied() {
        return toleranceApplied;
    }

    public void setToleranceApplied(boolean v) {
        toleranceApplied = v;
    }

    public String getToleranceProfile() {
        return toleranceProfile;
    }

    public void setToleranceProfile(String v) {
        toleranceProfile = v;
    }

    public Integer getMatchedLineCount() {
        return matchedLineCount;
    }

    public void setMatchedLineCount(Integer v) {
        matchedLineCount = v;
    }

    public Integer getDiscrepantLineCount() {
        return discrepantLineCount;
    }

    public void setDiscrepantLineCount(Integer v) {
        discrepantLineCount = v;
    }

    public Integer getToleratedDiscrepancyCount() {
        return toleratedDiscrepancyCount;
    }

    public void setToleratedDiscrepancyCount(Integer v) {
        toleratedDiscrepancyCount = v;
    }

    public Integer getMaterialDiscrepancyCount() {
        return materialDiscrepancyCount;
    }

    public void setMaterialDiscrepancyCount(Integer v) {
        materialDiscrepancyCount = v;
    }

    public BigDecimal getQuantityVariancePercent() {
        return quantityVariancePercent;
    }

    public void setQuantityVariancePercent(BigDecimal v) {
        quantityVariancePercent = v;
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
