package com.recon.integration.recon;

import java.util.LinkedHashSet;
import java.util.Set;

public final class TransactionDomainResolver {

    private static final Set<Integer> POS_TRANSACTION_TYPES = Set.of(1, 2, 3, 4, 5, 6, 8);
    private static final Set<Integer> INVENTORY_TRANSACTION_TYPES = Set.of(
            10, 11, 12,
            20, 21, 22,
            30, 40, 50, 60, 70, 80
    );
    private static final Set<Integer> SUPPORTED_TRANSACTION_TYPES = buildSupportedTransactionTypes();

    private TransactionDomainResolver() {
    }

    public static TransactionDomain resolve(Integer transactionTypeCode) {
        if (transactionTypeCode == null) {
            return TransactionDomain.UNKNOWN;
        }
        if (POS_TRANSACTION_TYPES.contains(transactionTypeCode)) {
            return TransactionDomain.POS;
        }
        if (INVENTORY_TRANSACTION_TYPES.contains(transactionTypeCode)) {
            return TransactionDomain.INVENTORY;
        }
        return TransactionDomain.UNKNOWN;
    }

    public static boolean isSupported(Integer transactionTypeCode) {
        return resolve(transactionTypeCode) != TransactionDomain.UNKNOWN;
    }

    public static Set<Integer> supportedTransactionTypes() {
        return SUPPORTED_TRANSACTION_TYPES;
    }

    private static Set<Integer> buildSupportedTransactionTypes() {
        LinkedHashSet<Integer> supported = new LinkedHashSet<>();
        supported.addAll(POS_TRANSACTION_TYPES);
        supported.addAll(INVENTORY_TRANSACTION_TYPES);
        return Set.copyOf(supported);
    }
}
