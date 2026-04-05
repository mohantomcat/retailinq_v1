package com.recon.flink.processor;

import com.recon.flink.domain.DiscrepancyType;
import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.FlatSimTransaction;
import com.recon.flink.domain.ItemDiscrepancy;
import com.recon.flink.domain.MatchEvaluation;
import com.recon.flink.domain.MatchToleranceProfile;
import com.recon.flink.domain.ReconEvent;
import com.recon.flink.util.InventoryBusinessContextFactory;
import com.recon.flink.util.InventoryDirectionProfile;
import com.recon.flink.util.InventoryDirectionResolver;
import com.recon.flink.util.ItemDiffEngine;
import com.recon.flink.util.MatchScoringEngine;
import com.recon.flink.util.MatchToleranceResolver;
import com.recon.integration.recon.TransactionFamily;
import com.recon.integration.recon.TransactionFamilyConfig;
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
    private ValueState<String> finalizedStatus;
    private ValueState<String> originalChecksum;
    private ValueState<String> finalizedCounterpartySystem;

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

        StateTtlConfig correctionTtl = StateTtlConfig
                .newBuilder(Time.hours(56))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();

        sourceState = registerState("inventory-source", FlatSimTransaction.class, opTtl);
        counterState = registerState("inventory-counter", FlatSimTransaction.class, opTtl);
        finalizedFlag = registerState("inventory-finalized", Boolean.class, opTtl);
        finalizedStatus = registerState("inventory-finalized-status", String.class, correctionTtl);
        originalChecksum = registerState("inventory-orig-checksum", String.class, correctionTtl);
        finalizedCounterpartySystem = registerState("inventory-finalized-counterparty", String.class, correctionTtl);
    }

    @Override
    public void processElement1(FlatSimTransaction source,
                                Context ctx,
                                Collector<ReconEvent> out) throws Exception {
        handleIncoming(source, true, ctx, out);
    }

    @Override
    public void processElement2(FlatSimTransaction counter,
                                Context ctx,
                                Collector<ReconEvent> out) throws Exception {
        handleIncoming(counter, false, ctx, out);
    }

    @Override
    public void onTimer(long timestamp,
                        OnTimerContext ctx,
                        Collector<ReconEvent> out) throws Exception {
        if (Boolean.TRUE.equals(finalizedFlag.value())) {
            return;
        }

        FlatSimTransaction source = sourceState.value();
        FlatSimTransaction counter = counterState.value();
        InventoryDirectionProfile direction = direction(source, counter);
        FlatSimTransaction origin = direction.originTransaction(source, counter);
        FlatSimTransaction receiving = direction.counterpartyTransaction(source, counter);

        if (origin == null || receiving != null) {
            return;
        }

        long softFireTime = ctx.timestamp() - HARD_TTL_MS + SOFT_TTL_MS;
        if (timestamp <= softFireTime + 1000L) {
            log.warn("Soft TTL exceeded key={} reconView={} origin={} counterparty={}",
                    origin.getTransactionKey(), reconView, direction.originSystem(), direction.counterpartySystem());
            out.collect(ReconEvent.awaitingInventory(origin, reconView, direction.counterpartySystem()));
        } else {
            log.warn("Hard TTL exceeded - {} key={}", direction.missingStatus(), origin.getTransactionKey());
            out.collect(ReconEvent.missingInventory(origin, reconView, direction.counterpartySystem()));
            clearAllState(direction.missingStatus(), null, direction.counterpartySystem());
        }
    }

    private void handleIncoming(FlatSimTransaction incoming,
                                boolean fromSourceSide,
                                Context ctx,
                                Collector<ReconEvent> out) throws Exception {
        TransactionFamilyConfig familyConfig = InventoryBusinessContextFactory.resolveFamily(incoming);
        if (familyConfig.transactionFamily() == TransactionFamily.UNKNOWN) {
            log.debug("Unmapped inventory transaction family key={} type={} typeDesc={}",
                    incoming.getTransactionKey(), incoming.getTransactionType(), incoming.getTransactionTypeDesc());
        }

        String previousStatus = finalizedStatus.value();
        if (previousStatus != null) {
            String correctionCounterparty = finalizedCounterpartySystem.value();
            if (correctionCounterparty != null
                    && correctionCounterparty.equalsIgnoreCase(incoming.getSource())) {
                handleCorrection(incoming, previousStatus, correctionCounterparty, out);
            } else {
                log.debug("Late {} update ignored after finalization key={}",
                        incoming.getSource(), incoming.getTransactionKey());
            }
            return;
        }

        FlatSimTransaction existing = fromSourceSide ? sourceState.value() : counterState.value();
        if (sameSnapshot(existing, incoming)) {
            log.debug("Duplicate {} update dropped key={} status={}",
                    incoming.getSource(), incoming.getTransactionKey(), incoming.getProcessingStatus());
            return;
        }

        if (fromSourceSide) {
            sourceState.update(incoming);
        } else {
            counterState.update(incoming);
        }

        evaluate(ctx, out);
    }

    private void evaluate(Context ctx,
                          Collector<ReconEvent> out) throws Exception {
        FlatSimTransaction left = sourceState.value();
        FlatSimTransaction right = counterState.value();
        InventoryDirectionProfile direction = direction(left, right);
        FlatSimTransaction origin = direction.originTransaction(left, right);
        FlatSimTransaction counterparty = direction.counterpartyTransaction(left, right);

        if (origin != null && counterparty != null) {
            Integer processingStatus = counterparty.getProcessingStatus();
            if (processingStatus != null && processingStatus == 2) {
                out.collect(ReconEvent.processingFailedInventory(origin, counterparty, reconView));
                clearAllState(direction.processingFailedStatus(), null, direction.counterpartySystem());
                return;
            }
            if (processingStatus != null && processingStatus == 4) {
                out.collect(ReconEvent.revertedInventory(origin, counterparty, reconView));
                clearAllState(direction.revertedStatus(), null, direction.counterpartySystem());
                return;
            }
            if (processingStatus != null && (processingStatus == 0 || processingStatus == 3)) {
                out.collect(ReconEvent.processingPendingInventory(origin, counterparty, reconView));
                return;
            }

            reconcile(origin, counterparty, direction, out);
            return;
        }

        if (origin != null) {
            registerTimers(ctx, origin);
            out.collect(ReconEvent.awaitingInventory(origin, reconView, direction.counterpartySystem()));
        }
    }

    private void reconcile(FlatSimTransaction origin,
                           FlatSimTransaction counterparty,
                           InventoryDirectionProfile direction,
                           Collector<ReconEvent> out) throws Exception {
        TransactionFamilyConfig familyConfig = InventoryBusinessContextFactory.resolveFamily(origin);
        int totalLines = lineItemCount(origin);

        if (hasDuplicateItemsInTargetComparedToSource(origin, counterparty)) {
            out.collect(ReconEvent.duplicateInventory(
                    origin,
                    counterparty,
                    reconView,
                    MatchScoringEngine.outcome(
                            toleranceProfile,
                            18,
                            "MISMATCH",
                            "DUPLICATE",
                            "Duplicate counterparty posting detected during reconciliation for "
                                    + familyConfig.transactionFamily().name(),
                            totalLines
                    )
            ));
            clearAllState(direction.duplicateStatus(), origin.getChecksum(), direction.counterpartySystem());
            return;
        }

        if (origin.getChecksum() != null && origin.getChecksum().equals(counterparty.getChecksum())) {
            out.collect(ReconEvent.matchedInventory(
                    origin,
                    counterparty,
                    reconView,
                    List.of(),
                    MatchScoringEngine.exactChecksumMatch(toleranceProfile, totalLines)
            ));
            clearAllState("MATCHED", origin.getChecksum(), direction.counterpartySystem());
            return;
        }

        List<ItemDiscrepancy> discrepancies = ItemDiffEngine.diff(
                origin.getLineItems(),
                counterparty.getLineItems(),
                direction.originSystem(),
                direction.counterpartySystem(),
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
            out.collect(ReconEvent.matchedInventory(origin, counterparty, reconView, discrepancies, evaluation));
            clearAllState("MATCHED", origin.getChecksum(), direction.counterpartySystem());
            return;
        }

        boolean hasItemMissing = hasMaterialType(discrepancies, DiscrepancyType.ITEM_MISSING, DiscrepancyType.ITEM_EXTRA);
        if (hasItemMissing) {
            out.collect(ReconEvent.itemMissingInventory(origin, counterparty, discrepancies, reconView, evaluation));
            clearAllState("ITEM_MISSING", origin.getChecksum(), direction.counterpartySystem());
        } else {
            out.collect(ReconEvent.quantityMismatchInventory(origin, counterparty, discrepancies, reconView, evaluation));
            clearAllState("QUANTITY_MISMATCH", origin.getChecksum(), direction.counterpartySystem());
        }
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

    private void handleCorrection(FlatSimTransaction counterparty,
                                  String previousStatus,
                                  String counterpartySystem,
                                  Collector<ReconEvent> out) throws Exception {
        String previousChecksum = originalChecksum.value();
        boolean checksumChanged = previousChecksum != null && !previousChecksum.equals(counterparty.getChecksum());

        if ("MATCHED".equals(previousStatus) && checksumChanged) {
            out.collect(ReconEvent.correctionMismatch(
                    counterparty,
                    previousStatus,
                    previousChecksum,
                    reconView,
                    counterpartySystem
            ));
        } else if (Objects.equals(previousStatus, "MISSING_IN_" + counterpartySystem)) {
            out.collect(ReconEvent.lateCorrection(counterparty, previousStatus, reconView, counterpartySystem));
        } else {
            log.debug("Idempotent {} re-post ignored key={}", counterpartySystem, counterparty.getTransactionKey());
        }

        finalizedStatus.update("CORRECTED_IN_" + counterpartySystem);
    }

    private void clearAllState(String status,
                               String checksum,
                               String counterpartySystem) throws Exception {
        sourceState.clear();
        counterState.clear();
        finalizedFlag.update(true);
        if (status != null) {
            finalizedStatus.update(status);
        }
        if (checksum != null) {
            originalChecksum.update(checksum);
        }
        if (counterpartySystem != null) {
            finalizedCounterpartySystem.update(counterpartySystem);
        }
    }

    private void registerTimers(Context ctx,
                                FlatSimTransaction origin) {
        long eventTime = ctx.timestamp() != null ? ctx.timestamp() : ctx.timerService().currentProcessingTime();
        ctx.timerService().registerEventTimeTimer(eventTime + SOFT_TTL_MS);
        ctx.timerService().registerEventTimeTimer(eventTime + HARD_TTL_MS);
    }

    private InventoryDirectionProfile direction(FlatSimTransaction source,
                                                FlatSimTransaction counter) {
        return InventoryDirectionResolver.resolve(reconView, source, counter);
    }

    private boolean sameSnapshot(FlatSimTransaction existing,
                                 FlatSimTransaction incoming) {
        if (existing == null || incoming == null) {
            return false;
        }
        return Objects.equals(existing.getChecksum(), incoming.getChecksum())
                && Objects.equals(existing.getProcessingStatus(), incoming.getProcessingStatus())
                && existing.getDuplicatePostingCount() == incoming.getDuplicatePostingCount()
                && Objects.equals(existing.getTotalQuantity(), incoming.getTotalQuantity());
    }

    private String normalize(String value,
                             String fallback) {
        String normalized = Objects.toString(value, "").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private <T> ValueState<T> registerState(String name, Class<T> clazz, StateTtlConfig ttl) {
        ValueStateDescriptor<T> desc = new ValueStateDescriptor<>(name, clazz);
        desc.enableTimeToLive(ttl);
        return getRuntimeContext().getState(desc);
    }
}
