package com.recon.flink.util;

import com.recon.flink.domain.MatchToleranceProfile;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public final class MatchToleranceResolver {

    private static final BigDecimal DEFAULT_QUANTITY_ABS = new BigDecimal("0.01");
    private static final BigDecimal DEFAULT_QUANTITY_PCT = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_AMOUNT_ABS = new BigDecimal("0.05");
    private static final BigDecimal DEFAULT_AMOUNT_PCT = new BigDecimal("0.10");

    private MatchToleranceResolver() {
    }

    public static MatchToleranceProfile resolve(String reconView) {
        String normalizedView = normalize(reconView);
        String profilePrefix = normalizedView == null
                ? "default"
                : normalizedView.toLowerCase(Locale.ROOT);

        BigDecimal quantityAbs = readDecimal(
                "recon.engine.tolerance." + profilePrefix + ".quantity.abs",
                readDecimal("recon.engine.tolerance.quantity.abs", DEFAULT_QUANTITY_ABS)
        );
        BigDecimal quantityPct = readDecimal(
                "recon.engine.tolerance." + profilePrefix + ".quantity.pct",
                readDecimal("recon.engine.tolerance.quantity.pct", DEFAULT_QUANTITY_PCT)
        );
        BigDecimal amountAbs = readDecimal(
                "recon.engine.tolerance." + profilePrefix + ".amount.abs",
                readDecimal("recon.engine.tolerance.amount.abs", DEFAULT_AMOUNT_ABS)
        );
        BigDecimal amountPct = readDecimal(
                "recon.engine.tolerance." + profilePrefix + ".amount.pct",
                readDecimal("recon.engine.tolerance.amount.pct", DEFAULT_AMOUNT_PCT)
        );

        return new MatchToleranceProfile(
                (normalizedView == null ? "DEFAULT" : normalizedView) + "_STANDARD",
                quantityAbs,
                quantityPct,
                amountAbs,
                amountPct
        );
    }

    private static BigDecimal readDecimal(String key, BigDecimal fallback) {
        String value = firstNonBlank(
                System.getProperty(key),
                System.getenv(toEnvKey(key))
        );
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static String normalize(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
