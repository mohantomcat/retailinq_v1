package com.recon.integration.kafka;

import com.recon.integration.recon.TransactionDomain;

import java.util.Locale;

public final class KafkaTopicCatalog {

    private KafkaTopicCatalog() {
    }

    public static String rawTransactionTopic(String sourceSystem,
                                             TransactionDomain domain) {
        String normalizedSystem = normalize(sourceSystem);
        String normalizedDomain = normalizeDomain(domain);
        return normalizedSystem + "." + normalizedDomain + ".transactions.raw";
    }

    private static String normalize(String sourceSystem) {
        if (sourceSystem == null || sourceSystem.isBlank()) {
            throw new IllegalArgumentException("Source system is required for topic resolution");
        }
        return sourceSystem.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '-')
                .replace('_', '-');
    }

    private static String normalizeDomain(TransactionDomain domain) {
        if (domain == null) {
            return TransactionDomain.UNKNOWN.name().toLowerCase(Locale.ROOT);
        }
        return domain.name().toLowerCase(Locale.ROOT);
    }
}
