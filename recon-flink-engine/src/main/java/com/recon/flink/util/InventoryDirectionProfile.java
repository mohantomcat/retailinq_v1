package com.recon.flink.util;

import com.recon.flink.domain.FlatSimTransaction;
import com.recon.integration.recon.TransactionPhase;

import java.util.Locale;
import java.util.Objects;

public record InventoryDirectionProfile(
        String reconView,
        String originSystem,
        String counterpartySystem,
        TransactionPhase transactionPhase,
        boolean resolved,
        String resolutionReason
) {

    public boolean isOrigin(FlatSimTransaction transaction) {
        return matchesSystem(transaction, originSystem);
    }

    public boolean isCounterparty(FlatSimTransaction transaction) {
        return matchesSystem(transaction, counterpartySystem);
    }

    public FlatSimTransaction originTransaction(FlatSimTransaction left,
                                                FlatSimTransaction right) {
        if (isOrigin(left)) {
            return left;
        }
        if (isOrigin(right)) {
            return right;
        }
        return null;
    }

    public FlatSimTransaction counterpartyTransaction(FlatSimTransaction left,
                                                      FlatSimTransaction right) {
        if (isCounterparty(left)) {
            return left;
        }
        if (isCounterparty(right)) {
            return right;
        }
        return null;
    }

    public String awaitingStatus() {
        return "AWAITING_" + counterpartySystem;
    }

    public String missingStatus() {
        return "MISSING_IN_" + counterpartySystem;
    }

    public String duplicateStatus() {
        return "DUPLICATE_IN_" + counterpartySystem;
    }

    public String processingPendingStatus() {
        return "PROCESSING_PENDING_IN_" + counterpartySystem;
    }

    public String processingFailedStatus() {
        return "PROCESSING_FAILED_IN_" + counterpartySystem;
    }

    public String revertedStatus() {
        return "REVERTED_IN_" + counterpartySystem;
    }

    public String correctedStatus() {
        return "CORRECTED_IN_" + counterpartySystem;
    }

    public String lateMatchStatus() {
        return "LATE_MATCH_IN_" + counterpartySystem;
    }

    private boolean matchesSystem(FlatSimTransaction transaction, String system) {
        if (transaction == null || system == null) {
            return false;
        }
        return normalize(system).equals(normalize(transaction.getSource()));
    }

    private String normalize(String value) {
        return Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
    }
}
