package com.recon.flink.util;

import com.recon.flink.domain.FlatSimTransaction;
import com.recon.integration.recon.TransactionFamily;
import com.recon.integration.recon.TransactionFamilyConfig;
import com.recon.integration.recon.TransactionPhase;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public final class InventoryDirectionResolver {

    private static final String SIM = "SIM";
    private static final String SIOCS = "SIOCS";
    private static final String MFCS = "MFCS";
    private static final String RMS = "RMS";

    private InventoryDirectionResolver() {
    }

    public static InventoryDirectionProfile resolve(String reconView,
                                                    FlatSimTransaction left,
                                                    FlatSimTransaction right) {
        FlatSimTransaction primary = left != null ? left : right;
        TransactionFamilyConfig familyConfig = InventoryBusinessContextFactory.resolveFamily(primary);
        TransactionPhase defaultPhase = familyConfig.transactionPhase();

        if (!"SIOCS_MFCS".equalsIgnoreCase(reconView)) {
            return fixedProfile(reconView, defaultPhase);
        }
        if (familyConfig.transactionFamily() != TransactionFamily.WHD) {
            return new InventoryDirectionProfile(
                    reconView,
                    SIOCS,
                    MFCS,
                    defaultPhase,
                    false,
                    "Legacy fallback for SIOCS_MFCS outside confirmed WHD semantics."
            );
        }

        FlatSimTransaction siocs = transactionFromSystem(left, right, SIOCS);
        FlatSimTransaction mfcs = transactionFromSystem(left, right, MFCS);

        String receiverSystem = receiverSystemByProcessingState(siocs, mfcs);
        if (receiverSystem != null) {
            return whdProfile(
                    reconView,
                    opposite(receiverSystem),
                    receiverSystem,
                    true,
                    "Resolved WHD direction from counterparty processing state."
            );
        }

        String earlierSystem = earlierSystem(siocs, mfcs);
        if (earlierSystem != null) {
            return whdProfile(
                    reconView,
                    earlierSystem,
                    opposite(earlierSystem),
                    true,
                    "Resolved WHD direction from event timestamp ordering."
            );
        }

        if (siocs != null && mfcs == null) {
            return whdProfile(
                    reconView,
                    SIOCS,
                    MFCS,
                    false,
                    "Single-sided SIOCS WHD event; keeping conservative receipt-side fallback."
            );
        }
        if (mfcs != null && siocs == null) {
            return whdProfile(
                    reconView,
                    MFCS,
                    SIOCS,
                    false,
                    "Single-sided MFCS WHD event; keeping conservative shipment-side fallback."
            );
        }

        return whdProfile(
                reconView,
                SIOCS,
                MFCS,
                false,
                "Unable to resolve WHD direction from current payloads; using legacy fallback."
        );
    }

    private static InventoryDirectionProfile fixedProfile(String reconView,
                                                          TransactionPhase phase) {
        return switch (normalize(reconView)) {
            case "SIM_RMS" -> new InventoryDirectionProfile(reconView, SIM, RMS, phase, true,
                    "Fixed SIM to RMS inventory lane.");
            case "SIM_MFCS" -> new InventoryDirectionProfile(reconView, SIM, MFCS, phase, true,
                    "Fixed SIM to MFCS inventory lane.");
            case "SIOCS_RMS" -> new InventoryDirectionProfile(reconView, SIOCS, RMS, phase, true,
                    "Fixed SIOCS to RMS inventory lane.");
            default -> new InventoryDirectionProfile(reconView, SIOCS, MFCS, phase, true,
                    "Fixed SIOCS to MFCS inventory lane.");
        };
    }

    private static InventoryDirectionProfile whdProfile(String reconView,
                                                        String originSystem,
                                                        String counterpartySystem,
                                                        boolean resolved,
                                                        String reason) {
        return new InventoryDirectionProfile(
                reconView,
                originSystem,
                counterpartySystem,
                MFCS.equals(originSystem) ? TransactionPhase.SHIPMENT : TransactionPhase.RECEIPT,
                resolved,
                reason
        );
    }

    private static FlatSimTransaction transactionFromSystem(FlatSimTransaction left,
                                                            FlatSimTransaction right,
                                                            String system) {
        if (matchesSystem(left, system)) {
            return left;
        }
        if (matchesSystem(right, system)) {
            return right;
        }
        return null;
    }

    private static String receiverSystemByProcessingState(FlatSimTransaction siocs,
                                                          FlatSimTransaction mfcs) {
        if (isReceiverProcessing(siocs)) {
            return SIOCS;
        }
        if (isReceiverProcessing(mfcs)) {
            return MFCS;
        }
        return null;
    }

    private static boolean isReceiverProcessing(FlatSimTransaction transaction) {
        if (transaction == null || transaction.getProcessingStatus() == null) {
            return false;
        }
        return switch (transaction.getProcessingStatus()) {
            case 0, 2, 3, 4 -> true;
            default -> false;
        };
    }

    private static String earlierSystem(FlatSimTransaction siocs,
                                        FlatSimTransaction mfcs) {
        Long siocsTs = eventTime(siocs);
        Long mfcsTs = eventTime(mfcs);
        if (siocsTs == null || mfcsTs == null || Objects.equals(siocsTs, mfcsTs)) {
            return null;
        }
        return siocsTs < mfcsTs ? SIOCS : MFCS;
    }

    private static Long eventTime(FlatSimTransaction transaction) {
        if (transaction == null) {
            return null;
        }
        Long updateTime = parseIso(transaction.getUpdateDateTime());
        if (updateTime != null) {
            return updateTime;
        }
        return parseIso(transaction.getTransactionDateTime());
    }

    private static Long parseIso(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean matchesSystem(FlatSimTransaction transaction, String system) {
        return transaction != null && normalize(transaction.getSource()).equals(normalize(system));
    }

    private static String opposite(String system) {
        return MFCS.equals(system) ? SIOCS : MFCS;
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
    }
}
