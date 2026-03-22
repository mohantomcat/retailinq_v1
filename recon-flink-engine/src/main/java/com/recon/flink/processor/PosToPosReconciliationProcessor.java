package com.recon.flink.processor;

import com.recon.flink.domain.DiscrepancyType;
import com.recon.flink.domain.FlatPosTransaction;
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

import java.util.List;
import java.util.Objects;
import java.math.BigDecimal;

@Slf4j
public class PosToPosReconciliationProcessor extends KeyedCoProcessFunction<String, FlatPosTransaction, FlatPosTransaction, ReconEvent> {

    private static final long SOFT_TTL_MS = 30 * 60 * 1000L;
    private static final long HARD_TTL_MS = 8 * 60 * 60 * 1000L;

    private final String reconView;
    private final String counterSource;
    private final MatchToleranceProfile toleranceProfile;

    private ValueState<FlatPosTransaction> xstoreState;
    private ValueState<FlatPosTransaction> counterState;

    public PosToPosReconciliationProcessor(String reconView, String counterSource) {
        this.reconView = reconView;
        this.counterSource = counterSource;
        this.toleranceProfile = MatchToleranceResolver.resolve(reconView);
    }

    @Override
    public void open(Configuration config) {
        StateTtlConfig ttl = StateTtlConfig.newBuilder(Time.hours(9))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();
        xstoreState = registerState("xstore-pos", FlatPosTransaction.class, ttl);
        counterState = registerState("counter-pos", FlatPosTransaction.class, ttl);
    }

    @Override
    public void processElement1(FlatPosTransaction xstore,
                                Context ctx,
                                Collector<ReconEvent> out) throws Exception {
        FlatPosTransaction counter = counterState.value();
        if (counter != null) {
            reconcile(xstore, counter, out);
            counterState.clear();
            return;
        }

        xstoreState.update(xstore);
        long eventTime = ctx.timestamp();
        ctx.timerService().registerEventTimeTimer(eventTime + SOFT_TTL_MS);
        ctx.timerService().registerEventTimeTimer(eventTime + HARD_TTL_MS);
        out.collect(ReconEvent.awaiting(xstore, reconView, counterSource));
    }

    @Override
    public void processElement2(FlatPosTransaction counter,
                                Context ctx,
                                Collector<ReconEvent> out) throws Exception {
        FlatPosTransaction xstore = xstoreState.value();
        if (xstore == null) {
            counterState.update(counter);
            return;
        }
        reconcile(xstore, counter, out);
        xstoreState.clear();
        counterState.clear();
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<ReconEvent> out) throws Exception {
        FlatPosTransaction xstore = xstoreState.value();
        if (xstore == null || counterState.value() != null) {
            return;
        }
        long softFireTime = ctx.timestamp() - HARD_TTL_MS + SOFT_TTL_MS;
        if (timestamp <= softFireTime + 1000L) {
            out.collect(ReconEvent.softTtlWarning(xstore, reconView, counterSource));
        } else {
            out.collect(ReconEvent.missing(xstore, reconView, counterSource));
            xstoreState.clear();
        }
    }

    private void reconcile(FlatPosTransaction xstore,
                           FlatPosTransaction counter,
                           Collector<ReconEvent> out) {
        int totalLines = lineItemCount(xstore);
        BigDecimal amountVariance = amountVariance(xstore.getTotalAmount(), counter.getTotalAmount());
        MatchScoringEngine.ToleranceAssessment amountAssessment = MatchScoringEngine.assessVariance(
                xstore.getTotalAmount(),
                counter.getTotalAmount(),
                toleranceProfile.amountAbsoluteTolerance(),
                toleranceProfile.amountPercentTolerance()
        );

        if (xstore.getChecksum() != null && xstore.getChecksum().equals(counter.getChecksum()) && !isPositive(amountVariance)) {
            out.collect(ReconEvent.matchedPos(
                    xstore,
                    counter,
                    reconView,
                    counterSource,
                    List.of(),
                    MatchScoringEngine.exactChecksumMatch(toleranceProfile, totalLines),
                    null,
                    null
            ));
            return;
        }

        List<ItemDiscrepancy> discrepancies = ItemDiffEngine.diff(
                xstore.getLineItems(), counter.getLineItems(), "Xstore", counterSource, toleranceProfile);
        MatchEvaluation evaluation = MatchScoringEngine.evaluate(
                toleranceProfile,
                totalLines,
                discrepancies,
                amountVariance,
                amountAssessment.variancePercent()
        );

        if (evaluation.materialDiscrepancyCount() == 0
                && (!isPositive(amountVariance) || amountAssessment.withinTolerance())) {
            out.collect(ReconEvent.matchedPos(
                    xstore,
                    counter,
                    reconView,
                    counterSource,
                    discrepancies,
                    evaluation,
                    amountVariance,
                    amountAssessment.variancePercent()
            ));
            return;
        }

        boolean hasItemGap = hasMaterialType(discrepancies, DiscrepancyType.ITEM_MISSING, DiscrepancyType.ITEM_EXTRA);
        boolean hasQuantityGap = hasMaterialType(discrepancies, DiscrepancyType.QUANTITY_MISMATCH, DiscrepancyType.UOM_MISMATCH);
        boolean hasAmountGap = hasMaterialType(discrepancies, DiscrepancyType.AMOUNT_MISMATCH)
                || (isPositive(amountVariance) && !amountAssessment.withinTolerance());

        if (hasItemGap) {
            out.collect(ReconEvent.itemMissingPos(
                    xstore, counter, discrepancies, reconView, counterSource, evaluation));
        } else if (hasQuantityGap) {
            out.collect(ReconEvent.quantityMismatchPos(
                    xstore, counter, discrepancies, reconView, counterSource, evaluation, amountVariance, amountAssessment.variancePercent()));
        } else if (hasAmountGap) {
            out.collect(ReconEvent.totalMismatchPos(
                    xstore, counter, discrepancies, reconView, counterSource, evaluation, amountVariance, amountAssessment.variancePercent()));
        }
    }

    private <T> ValueState<T> registerState(String name, Class<T> clazz, StateTtlConfig ttl) {
        ValueStateDescriptor<T> desc = new ValueStateDescriptor<>(name, clazz);
        desc.enableTimeToLive(ttl);
        return getRuntimeContext().getState(desc);
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

    private BigDecimal amountVariance(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.subtract(right).abs();
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
