package com.recon.flink.processor;

import com.recon.flink.domain.DiscrepancyType;
import com.recon.flink.domain.FlatLineItem;
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
public class InventoryToInventoryReconciliationProcessor
        extends KeyedCoProcessFunction<String, FlatSimTransaction, FlatSimTransaction, ReconEvent> {

    private static final long SOFT_TTL_MS = 30 * 60 * 1000L;
    private static final long HARD_TTL_MS = 8 * 60 * 60 * 1000L;

    private final String reconView;
    private final String sourceLabel;
    private final String counterSourceLabel;
    private final MatchToleranceProfile toleranceProfile;

    private ValueState<FlatSimTransaction> sourceState;
    private ValueState<FlatSimTransaction> counterState;
    private ValueState<Boolean> finalizedFlag;
    private ValueState<Long> sourceSeenAt;
    private ValueState<Integer> lastCounterStatus;
    private ValueState<String> finalizedStatus;
    private ValueState<String> originalChecksum;

    public InventoryToInventoryReconciliationProcessor(String reconView,
                                                       String sourceLabel,
                                                       String counterSourceLabel) {
        this.reconView = reconView;
        this.sourceLabel = sourceLabel;
        this.counterSourceLabel = counterSourceLabel;
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

        sourceState = registerState("inventory-source", FlatSimTransaction.class, opTtl);
        counterState = registerState("inventory-counter", FlatSimTransaction.class, opTtl);
        finalizedFlag = registerState("inventory-finalized", Boolean.class, opTtl);
        sourceSeenAt = registerState("inventory-source-seen", Long.class, dedupTtl);
        lastCounterStatus = registerState("inventory-counter-status", Integer.class, dedupTtl);
        finalizedStatus = registerState("inventory-finalized-status", String.class, correctionTtl);
        originalChecksum = registerState("inventory-orig-checksum", String.class, correctionTtl);
    }

    @Override
    public void processElement1(FlatSimTransaction source,
                                Context ctx,
                                Collector<ReconEvent> out) throws Exception {
        if (sourceSeenAt.value() != null) {
            log.debug("Duplicate {} source dropped: {}", sourceLabel, source.getTransactionKey());
            return;
        }
        sourceSeenAt.update(ctx.timerService().currentProcessingTime());

        FlatSimTransaction counter = counterState.value();
        if (isReconciliableCounter(counter)) {
            reconcile(source, counter, out);
            counterState.clear();
            return;
        }

        sourceState.update(source);
        long eventTime = ctx.timestamp();
        ctx.timerService().registerEventTimeTimer(eventTime + SOFT_TTL_MS);
        ctx.timerService().registerEventTimeTimer(eventTime + HARD_TTL_MS);

        if (counter == null) {
            out.collect(ReconEvent.awaitingInventory(source, reconView, counterSourceLabel));
        }
    }

    @Override
    public void processElement2(FlatSimTransaction counter,
                                Context ctx,
                                Collector<ReconEvent> out) throws Exception {
        String prevStatus = finalizedStatus.value();
        if (prevStatus != null) {
            handleCorrection(counter, prevStatus, out);
            return;
        }

        Integer lastStatus = lastCounterStatus.value();
        if (lastStatus != null && lastStatus.equals(counter.getProcessingStatus())) {
            log.debug("Duplicate {} target dropped key={}", counterSourceLabel, counter.getTransactionKey());
            return;
        }
        lastCounterStatus.update(counter.getProcessingStatus());

        if (counter.getProcessingStatus() != null && counter.getProcessingStatus() == 2) {
            out.collect(ReconEvent.processingFailed(counter, reconView));
            clearAllState(processingFailedStatus(), null);
            return;
        }
        if (counter.getProcessingStatus() != null && counter.getProcessingStatus() == 4) {
            out.collect(ReconEvent.reverted(counter, reconView));
            clearAllState(revertedStatus(), null);
            return;
        }
        if (counter.getProcessingStatus() != null
                && (counter.getProcessingStatus() == 0 || counter.getProcessingStatus() == 3)) {
            counterState.update(counter);
            out.collect(ReconEvent.processingPending(counter, reconView));
            return;
        }

        FlatSimTransaction source = sourceState.value();
        if (source == null) {
            counterState.update(counter);
            return;
        }

        reconcile(source, counter, out);
        sourceState.clear();
    }

    @Override
    public void onTimer(long timestamp,
                        OnTimerContext ctx,
                        Collector<ReconEvent> out) throws Exception {
        if (Boolean.TRUE.equals(finalizedFlag.value())) {
            return;
        }

        FlatSimTransaction source = sourceState.value();
        if (source == null) {
            return;
        }

        long softFireTime = ctx.timestamp() - HARD_TTL_MS + SOFT_TTL_MS;
        if (timestamp <= softFireTime + 1000L) {
            log.warn("Soft TTL exceeded key={} reconView={}", source.getTransactionKey(), reconView);
            out.collect(ReconEvent.awaitingInventory(source, reconView, counterSourceLabel));
        } else {
            log.warn("Hard TTL exceeded - {} key={}", missingStatus(), source.getTransactionKey());
            out.collect(ReconEvent.missingInventory(source, reconView, counterSourceLabel));
            clearAllState(missingStatus(), null);
        }
    }

    private void reconcile(FlatSimTransaction source,
                           FlatSimTransaction counter,
                           Collector<ReconEvent> out) throws Exception {
        int totalLines = lineItemCount(source);

        if (hasDuplicateItemsInTargetComparedToSource(source, counter)) {
            out.collect(ReconEvent.duplicate(
                    counter,
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
            clearAllState(duplicateStatus(), source.getChecksum());
            return;
        }

        if (source.getChecksum() != null && source.getChecksum().equals(counter.getChecksum())) {
            out.collect(ReconEvent.matchedInventory(
                    source,
                    counter,
                    reconView,
                    List.of(),
                    MatchScoringEngine.exactChecksumMatch(toleranceProfile, totalLines)
            ));
            clearAllState("MATCHED", source.getChecksum());
            return;
        }

        List<ItemDiscrepancy> discrepancies = ItemDiffEngine.diff(
                source.getLineItems(),
                counter.getLineItems(),
                sourceLabel,
                counterSourceLabel,
                toleranceProfile
        );
        MatchEvaluation evaluation = MatchScoringEngine.evaluate(
                toleranceProfile,
                totalLines,
                discrepancies,
                null,
                null
        );

        if (evaluation.materialDiscrepancyCount() == 0) {
            out.collect(ReconEvent.matchedInventory(source, counter, reconView, discrepancies, evaluation));
            clearAllState("MATCHED", source.getChecksum());
            return;
        }

        boolean hasItemMissing = hasMaterialType(discrepancies, DiscrepancyType.ITEM_MISSING, DiscrepancyType.ITEM_EXTRA);
        if (hasItemMissing) {
            out.collect(ReconEvent.itemMissingInventory(source, counter, discrepancies, reconView, evaluation));
            clearAllState("ITEM_MISSING", source.getChecksum());
        } else {
            out.collect(ReconEvent.quantityMismatchInventory(source, counter, discrepancies, reconView, evaluation));
            clearAllState("QUANTITY_MISMATCH", source.getChecksum());
        }
    }

    private boolean isReconciliableCounter(FlatSimTransaction counter) {
        if (counter == null) {
            return false;
        }
        Integer status = counter.getProcessingStatus();
        return status == null || (status != 0 && status != 3);
    }

    private boolean hasDuplicateItemsInTargetComparedToSource(FlatSimTransaction source,
                                                              FlatSimTransaction counter) {
        Map<String, Integer> sourceCounts = itemOccurrenceCounts(source.getLineItems());
        Map<String, Integer> counterCounts = itemOccurrenceCounts(counter.getLineItems());
        for (Map.Entry<String, Integer> entry : counterCounts.entrySet()) {
            int sourceCount = sourceCounts.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > sourceCount && sourceCount > 0) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> itemOccurrenceCounts(FlatLineItem[] items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (items == null) {
            return counts;
        }
        for (FlatLineItem item : items) {
            if (item == null || item.getItemId() == null || item.getItemId().isBlank()) {
                continue;
            }
            String key = normalize(item.getLineType(), "Unknown") + "|" + item.getItemId().trim();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
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

    private int lineItemCount(FlatSimTransaction transaction) {
        return transaction != null && transaction.getLineItems() != null ? transaction.getLineItems().length : 0;
    }

    private void handleCorrection(FlatSimTransaction counter,
                                  String prevStatus,
                                  Collector<ReconEvent> out) throws Exception {
        String prevChecksum = originalChecksum.value();
        boolean checksumChanged = prevChecksum != null && !prevChecksum.equals(counter.getChecksum());

        if ("MATCHED".equals(prevStatus) && checksumChanged) {
            out.collect(ReconEvent.correctionMismatch(counter, prevStatus, prevChecksum, reconView));
        } else if (missingStatus().equals(prevStatus)) {
            out.collect(ReconEvent.lateCorrection(counter, prevStatus, reconView));
        } else {
            log.debug("Idempotent {} re-post ignored key={}", counterSourceLabel, counter.getTransactionKey());
        }

        finalizedStatus.update(correctedStatus());
    }

    private void clearAllState(String status, String checksum) throws Exception {
        sourceState.clear();
        counterState.clear();
        finalizedFlag.update(true);
        if (status != null) {
            finalizedStatus.update(status);
        }
        if (checksum != null) {
            originalChecksum.update(checksum);
        }
    }

    private String normalize(String value, String fallback) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String missingStatus() {
        return "MISSING_IN_" + targetSystem();
    }

    private String processingFailedStatus() {
        return "PROCESSING_FAILED_IN_" + targetSystem();
    }

    private String revertedStatus() {
        return "REVERTED_IN_" + targetSystem();
    }

    private String duplicateStatus() {
        return "DUPLICATE_IN_" + targetSystem();
    }

    private String correctedStatus() {
        return "CORRECTED_IN_" + targetSystem();
    }

    private String targetSystem() {
        return "SIOCS_MFCS".equalsIgnoreCase(reconView) ? "MFCS" : "SIM";
    }

    private <T> ValueState<T> registerState(String name, Class<T> clazz, StateTtlConfig ttl) {
        ValueStateDescriptor<T> desc = new ValueStateDescriptor<>(name, clazz);
        desc.enableTimeToLive(ttl);
        return getRuntimeContext().getState(desc);
    }
}
