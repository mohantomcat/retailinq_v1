package com.recon.integration.recon;

public record TransactionFamilyConfig(
        Integer runtimeTypeCode,
        String runtimeTypeDescription,
        TransactionFamily transactionFamily,
        TransactionPhase transactionPhase,
        boolean quantityMetricsAvailable,
        boolean valueMetricsAvailable,
        boolean inferredMapping,
        String mappingNotes
) {

    public static TransactionFamilyConfig unknown(Integer runtimeTypeCode,
                                                  String runtimeTypeDescription) {
        return new TransactionFamilyConfig(
                runtimeTypeCode,
                runtimeTypeDescription,
                TransactionFamily.UNKNOWN,
                TransactionPhase.UNKNOWN,
                false,
                false,
                false,
                "No business family mapping has been confirmed for this runtime transaction type yet."
        );
    }
}
