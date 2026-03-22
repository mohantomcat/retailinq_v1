package com.recon.flink.processor;

import com.recon.flink.domain.DiscrepancyType;
import com.recon.flink.domain.FlatPosTransaction;
import com.recon.flink.domain.FlatSimTransaction;
import com.recon.flink.domain.ItemDiscrepancy;
import com.recon.flink.domain.MatchEvaluation;
import com.recon.flink.domain.MatchToleranceProfile;
import com.recon.flink.domain.ReconEvent;
import com.recon.flink.util.ItemDiffEngine;
import com.recon.flink.util.MatchScoringEngine;
import com.recon.flink.util.MatchToleranceResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class ReconciliationProcessor
        extends KeyedCoProcessFunction
        <String,
                FlatPosTransaction,
                FlatSimTransaction,
                ReconEvent> {

    private static final long SOFT_TTL_MS = 30 * 60 * 1000L;
    private static final long HARD_TTL_MS = 8 * 60 * 60 * 1000L;

    private final String reconView;
    private final String expectedSimSource;
    private final MatchToleranceProfile toleranceProfile;

    private ValueState<FlatPosTransaction> xstoreState;
    private ValueState<FlatSimTransaction> siocsState;
    private ValueState<Boolean> finalizedFlag;
    private ValueState<Long> xstoreSeenAt;
    private ValueState<Integer> lastSiocsStatus;
    private ValueState<String> finalizedStatus;
    private ValueState<String> originalChecksum;

    public ReconciliationProcessor(String reconView, String expectedSimSource) {
        this.reconView = reconView;
        this.expectedSimSource = expectedSimSource;
        this.toleranceProfile = MatchToleranceResolver.resolve(reconView);
    }

    @Override
    public void open(Configuration config) {
        StateTtlConfig opTtl = StateTtlConfig
                .newBuilder(Time.hours(9))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();

        StateTtlConfig dedupTtl = StateTtlConfig
                .newBuilder(Time.hours(32))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();

        StateTtlConfig correctionTtl = StateTtlConfig
                .newBuilder(Time.hours(56))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();

        xstoreState = registerState("xstore", FlatPosTransaction.class, opTtl);
        siocsState = registerState("siocs", FlatSimTransaction.class, opTtl);
        finalizedFlag = registerState("finalized", Boolean.class, opTtl);
        xstoreSeenAt = registerState("xstore-seen", Long.class, dedupTtl);
        lastSiocsStatus = registerState("siocs-status", Integer.class, dedupTtl);
        finalizedStatus = registerState("finalized-status", String.class, correctionTtl);
        originalChecksum = registerState("orig-checksum", String.class, correctionTtl);
    }

    @Override
    public void processElement1(
            FlatPosTransaction xstore,
            Context ctx,
            Collector<ReconEvent> out) throws Exception {

        if (xstoreSeenAt.value() != null) {
            log.debug("Duplicate Xstore dropped: {}", xstore.getTransactionKey());
            return;
        }
        xstoreSeenAt.update(ctx.timerService().currentProcessingTime());

        FlatSimTransaction siocs = siocsState.value();
        if (siocs != null
                && siocs.getProcessingStatus() != null
                && siocs.getProcessingStatus() != 0
                && siocs.getProcessingStatus() != 3) {
            reconcile(xstore, siocs, out);
            siocsState.clear();
            return;
        }

        xstoreState.update(xstore);
        long eventTime = ctx.timestamp();
        ctx.timerService().registerEventTimeTimer(eventTime + SOFT_TTL_MS);
        ctx.timerService().registerEventTimeTimer(eventTime + HARD_TTL_MS);

        if (siocs == null) {
            out.collect(ReconEvent.awaiting(xstore, reconView, expectedSimSource));
        }
    }

    @Override
    public void processElement2(
            FlatSimTransaction siocs,
            Context ctx,
            Collector<ReconEvent> out) throws Exception {

        String prevStatus = finalizedStatus.value();
        if (prevStatus != null) {
            handleCorrection(siocs, prevStatus, out);
            return;
        }

        Integer lastStatus = lastSiocsStatus.value();
        if (lastStatus != null && lastStatus.equals(siocs.getProcessingStatus())) {
            log.debug("True duplicate SIOCS dropped key={}", siocs.getTransactionKey());
            return;
        }
        lastSiocsStatus.update(siocs.getProcessingStatus());

        if (siocs.getProcessingStatus() == 2) {
            out.collect(ReconEvent.processingFailed(siocs, reconView));
            clearAllState(processingFailedStatus(), null);
            return;
        }
        if (siocs.getProcessingStatus() == 4) {
            out.collect(ReconEvent.reverted(siocs, reconView));
            clearAllState(revertedStatus(), null);
            return;
        }

        if (siocs.getProcessingStatus() == 0 || siocs.getProcessingStatus() == 3) {
            siocsState.update(siocs);
            out.collect(ReconEvent.processingPending(siocs, reconView));
            return;
        }

        FlatPosTransaction xstore = xstoreState.value();
        if (xstore == null) {
            siocsState.update(siocs);
            return;
        }

        reconcile(xstore, siocs, out);
        xstoreState.clear();
    }

    @Override
    public void onTimer(long timestamp,
                        OnTimerContext ctx,
                        Collector<ReconEvent> out) throws Exception {

        if (Boolean.TRUE.equals(finalizedFlag.value())) {
            return;
        }

        FlatPosTransaction xstore = xstoreState.value();
        if (xstore == null) {
            return;
        }

        long softFireTime = ctx.timestamp() - HARD_TTL_MS + SOFT_TTL_MS;

        if (timestamp <= softFireTime + 1000L) {
            log.warn("Soft TTL exceeded key={}", xstore.getTransactionKey());
            out.collect(ReconEvent.softTtlWarning(xstore, reconView, expectedSimSource));
        } else {
            log.warn("Hard TTL exceeded - {} key={}", missingStatus(), xstore.getTransactionKey());
            out.collect(ReconEvent.missing(xstore, reconView, expectedSimSource));
            clearAllState(missingStatus(), null);
        }
    }

    private void reconcile(FlatPosTransaction xstore,
                           FlatSimTransaction siocs,
                           Collector<ReconEvent> out) throws Exception {

        int totalLines = lineItemCount(xstore);

        if (hasDuplicateItemsInTargetComparedToXstore(xstore, siocs)) {
            out.collect(ReconEvent.duplicate(
                    siocs,
                    reconView,
                    MatchScoringEngine.outcome(
                            toleranceProfile,
                            18,
                            "MISMATCH",
                            "DUPLICATE",
                            "Duplicate target posting detected during reconciliation",
                            totalLines
                    )
            ));
            clearAllState(duplicateStatus(), xstore.getChecksum());
            return;
        }

        if (xstore.getChecksum() != null
                && xstore.getChecksum().equals(siocs.getChecksum())) {
            out.collect(ReconEvent.matched(
                    xstore,
                    siocs,
                    reconView,
                    List.of(),
                    MatchScoringEngine.exactChecksumMatch(toleranceProfile, totalLines)
            ));
            clearAllState("MATCHED", xstore.getChecksum());
            return;
        }

        List<ItemDiscrepancy> discrepancies = ItemDiffEngine.diff(
                xstore.getLineItems(), siocs.getLineItems(), "Xstore", expectedSimSource, toleranceProfile);
        MatchEvaluation evaluation = MatchScoringEngine.evaluate(
                toleranceProfile,
                totalLines,
                discrepancies,
                null,
                null
        );

        if (evaluation.materialDiscrepancyCount() == 0) {
            out.collect(ReconEvent.matched(xstore, siocs, reconView, discrepancies, evaluation));
            clearAllState("MATCHED", xstore.getChecksum());
            return;
        }

        boolean hasItemMissing = hasMaterialType(discrepancies, DiscrepancyType.ITEM_MISSING, DiscrepancyType.ITEM_EXTRA);

        if (hasItemMissing) {
            out.collect(ReconEvent.itemMissing(xstore, siocs, discrepancies, reconView, evaluation));
            clearAllState("ITEM_MISSING", xstore.getChecksum());
        } else {
            out.collect(ReconEvent.quantityMismatch(xstore, siocs, discrepancies, reconView, evaluation));
            clearAllState("QUANTITY_MISMATCH", xstore.getChecksum());
        }
    }

    private boolean hasDuplicateItemsInTargetComparedToXstore(FlatPosTransaction xstore,
                                                              FlatSimTransaction siocs) {
        Map<String, Integer> xstoreCounts = itemOccurrenceCounts(xstore.getLineItems());
        Map<String, Integer> siocsCounts = itemOccurrenceCounts(siocs.getLineItems());

        for (Map.Entry<String, Integer> entry : siocsCounts.entrySet()) {
            int xstoreCount = xstoreCounts.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > xstoreCount && xstoreCount > 0) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> itemOccurrenceCounts(com.recon.flink.domain.FlatLineItem[] items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (items == null) {
            return counts;
        }
        for (com.recon.flink.domain.FlatLineItem item : items) {
            if (item == null || item.getItemId() == null || item.getItemId().isBlank()) {
                continue;
            }
            String key = normalize(item.getLineType(), "Unknown") + "|" + item.getItemId().trim();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private String normalize(String value, String fallback) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private boolean hasMaterialType(List<ItemDiscrepancy> discrepancies, DiscrepancyType... types) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return false;
        }
        List<DiscrepancyType> allowed = List.of(types);
        return discrepancies.stream()
                .filter(discrepancy -> !discrepancy.isWithinTolerance())
                .anyMatch(discrepancy -> allowed.contains(discrepancy.getType()));
    }

    private int lineItemCount(FlatPosTransaction xstore) {
        return xstore != null && xstore.getLineItems() != null ? xstore.getLineItems().length : 0;
    }

    private void handleCorrection(FlatSimTransaction siocs,
                                  String prevStatus,
                                  Collector<ReconEvent> out) throws Exception {
        String prevChecksum = originalChecksum.value();
        boolean checksumChanged = prevChecksum != null
                && !prevChecksum.equals(siocs.getChecksum());

        if ("MATCHED".equals(prevStatus) && checksumChanged) {
            out.collect(ReconEvent.correctionMismatch(siocs, prevStatus, prevChecksum, reconView));
        } else if (missingStatus().equals(prevStatus)) {
            out.collect(ReconEvent.lateCorrection(siocs, prevStatus, reconView));
        } else {
            log.debug("Idempotent re-post ignored key={}", siocs.getTransactionKey());
        }

        finalizedStatus.update(correctedStatus());
    }

    private void clearAllState(String status, String checksum) throws Exception {
        xstoreState.clear();
        siocsState.clear();
        finalizedFlag.update(true);
        if (status != null) {
            finalizedStatus.update(status);
        }
        if (checksum != null) {
            originalChecksum.update(checksum);
        }
    }

    private String missingStatus() {
        return "XSTORE_SIOCS".equalsIgnoreCase(reconView)
                ? "MISSING_IN_SIOCS"
                : "MISSING_IN_SIM";
    }

    private String processingFailedStatus() {
        return "XSTORE_SIOCS".equalsIgnoreCase(reconView)
                ? "PROCESSING_FAILED_IN_SIOCS"
                : "PROCESSING_FAILED_IN_SIM";
    }

    private String revertedStatus() {
        return "XSTORE_SIOCS".equalsIgnoreCase(reconView)
                ? "REVERTED_IN_SIOCS"
                : "REVERTED_IN_SIM";
    }

    private String duplicateStatus() {
        return "XSTORE_SIOCS".equalsIgnoreCase(reconView)
                ? "DUPLICATE_IN_SIOCS"
                : "DUPLICATE_IN_SIM";
    }

    private String correctedStatus() {
        return "XSTORE_SIOCS".equalsIgnoreCase(reconView)
                ? "CORRECTED_IN_SIOCS"
                : "CORRECTED_IN_SIM";
    }

    private <T> ValueState<T> registerState(String name, Class<T> clazz, StateTtlConfig ttl) {
        ValueStateDescriptor<T> desc = new ValueStateDescriptor<>(name, clazz);
        desc.enableTimeToLive(ttl);
        return getRuntimeContext().getState(desc);
    }
}
