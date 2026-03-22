package com.recon.flink.util;

import com.recon.flink.domain.DiscrepancyType;
import com.recon.flink.domain.ItemDiscrepancy;
import com.recon.flink.domain.MatchEvaluation;
import com.recon.flink.domain.MatchToleranceProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MatchScoringEngine {

    private MatchScoringEngine() {
    }

    public static MatchEvaluation exactChecksumMatch(MatchToleranceProfile profile, int totalLines) {
        return new MatchEvaluation(
                100,
                "EXACT",
                "CHECKSUM",
                "Exact checksum and structural match",
                false,
                profile.profileName(),
                Math.max(totalLines, 0),
                0,
                0,
                0,
                null,
                null
        );
    }

    public static MatchEvaluation evaluate(MatchToleranceProfile profile,
                                           int totalLines,
                                           List<ItemDiscrepancy> discrepancies,
                                           BigDecimal amountVariance,
                                           BigDecimal amountVariancePercent) {
        List<ItemDiscrepancy> safeDiscrepancies = discrepancies == null ? List.of() : discrepancies;

        int toleratedCount = (int) safeDiscrepancies.stream()
                .filter(ItemDiscrepancy::isWithinTolerance)
                .count();
        int materialCount = safeDiscrepancies.size() - toleratedCount;
        int missingCount = countType(safeDiscrepancies, DiscrepancyType.ITEM_MISSING.name());
        int extraCount = countType(safeDiscrepancies, DiscrepancyType.ITEM_EXTRA.name());
        int quantityCount = countType(safeDiscrepancies, DiscrepancyType.QUANTITY_MISMATCH.name());
        int uomCount = countType(safeDiscrepancies, DiscrepancyType.UOM_MISMATCH.name());
        int amountCount = countType(safeDiscrepancies, DiscrepancyType.AMOUNT_MISMATCH.name());
        int matchedLineCount = Math.max(totalLines - materialCount, 0);

        int score = 100;
        score -= missingCount * 22;
        score -= extraCount * 22;
        score -= materialCountOf(safeDiscrepancies, DiscrepancyType.QUANTITY_MISMATCH.name()) * 10;
        score -= materialCountOf(safeDiscrepancies, DiscrepancyType.UOM_MISMATCH.name()) * 10;
        score -= materialCountOf(safeDiscrepancies, DiscrepancyType.AMOUNT_MISMATCH.name()) * 12;
        score -= toleratedCount * 3;
        score -= severityPenalty(maxPercent(
                maxQuantityPercent(safeDiscrepancies),
                positiveOrNull(amountVariancePercent)
        ));
        score = Math.max(0, Math.min(100, score));

        String band;
        String rule;
        String summary;
        boolean toleranceApplied = toleratedCount > 0;

        if (materialCount == 0 && (toleratedCount > 0 || isPositive(amountVariance))) {
            band = "TOLERATED";
            rule = "TOLERANCE_PROFILE";
            summary = "Matched within tolerance profile " + profile.profileName()
                    + " | tolerated variances " + Math.max(toleratedCount, isPositive(amountVariance) ? 1 : 0);
            score = Math.max(score, 90);
        } else if (materialCount == 0) {
            band = "EXACT";
            rule = "STRUCTURAL_MATCH";
            summary = "Items and totals matched after structural comparison";
            score = Math.max(score, 96);
        } else if (score >= 70) {
            band = "PARTIAL";
            rule = classifyRule(missingCount, extraCount, quantityCount, uomCount, amountCount);
            summary = buildVarianceSummary(missingCount, extraCount, quantityCount, uomCount, amountCount);
        } else {
            band = "MISMATCH";
            rule = classifyRule(missingCount, extraCount, quantityCount, uomCount, amountCount);
            summary = buildVarianceSummary(missingCount, extraCount, quantityCount, uomCount, amountCount);
        }

        return new MatchEvaluation(
                score,
                band,
                rule,
                summary,
                toleranceApplied,
                profile.profileName(),
                matchedLineCount,
                safeDiscrepancies.size(),
                toleratedCount,
                materialCount,
                maxQuantityPercent(safeDiscrepancies),
                positiveOrNull(amountVariancePercent)
        );
    }

    public static MatchEvaluation outcome(MatchToleranceProfile profile,
                                          int score,
                                          String band,
                                          String rule,
                                          String summary,
                                          int totalLines) {
        return new MatchEvaluation(
                score,
                band,
                rule,
                summary,
                false,
                profile.profileName(),
                Math.max(totalLines, 0),
                0,
                0,
                1,
                null,
                null
        );
    }

    public static ToleranceAssessment assessVariance(BigDecimal left,
                                                     BigDecimal right,
                                                     BigDecimal absoluteTolerance,
                                                     BigDecimal percentTolerance) {
        if (left == null || right == null) {
            return ToleranceAssessment.notApplicable();
        }
        BigDecimal variance = left.subtract(right).abs();
        BigDecimal variancePercent = percent(variance, baseline(left, right));
        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            return new ToleranceAssessment(true, variance, variancePercent, null, null);
        }
        if (absoluteTolerance != null && variance.compareTo(absoluteTolerance) <= 0) {
            return new ToleranceAssessment(true, variance, variancePercent, "ABSOLUTE", absoluteTolerance);
        }
        if (percentTolerance != null
                && variancePercent != null
                && variancePercent.compareTo(percentTolerance) <= 0) {
            return new ToleranceAssessment(true, variance, variancePercent, "PERCENT", percentTolerance);
        }
        return new ToleranceAssessment(false, variance, variancePercent, null, null);
    }

    private static String classifyRule(int missingCount,
                                       int extraCount,
                                       int quantityCount,
                                       int uomCount,
                                       int amountCount) {
        if (missingCount > 0 || extraCount > 0) {
            return "ITEM_GAP";
        }
        if (quantityCount > 0) {
            return "QUANTITY_VARIANCE";
        }
        if (amountCount > 0) {
            return "AMOUNT_VARIANCE";
        }
        if (uomCount > 0) {
            return "UOM_VARIANCE";
        }
        return "MIXED_VARIANCE";
    }

    private static String buildVarianceSummary(int missingCount,
                                               int extraCount,
                                               int quantityCount,
                                               int uomCount,
                                               int amountCount) {
        StringBuilder builder = new StringBuilder("Variance detected");
        if (missingCount > 0) {
            builder.append(" | missing items ").append(missingCount);
        }
        if (extraCount > 0) {
            builder.append(" | extra items ").append(extraCount);
        }
        if (quantityCount > 0) {
            builder.append(" | quantity variances ").append(quantityCount);
        }
        if (uomCount > 0) {
            builder.append(" | UOM variances ").append(uomCount);
        }
        if (amountCount > 0) {
            builder.append(" | amount variances ").append(amountCount);
        }
        return builder.toString();
    }

    private static int countType(List<ItemDiscrepancy> discrepancies, String type) {
        return (int) discrepancies.stream()
                .filter(discrepancy -> Objects.equals(normalize(discrepancy.getType() != null ? discrepancy.getType().name() : null), type))
                .count();
    }

    private static int materialCountOf(List<ItemDiscrepancy> discrepancies, String type) {
        return (int) discrepancies.stream()
                .filter(discrepancy -> !discrepancy.isWithinTolerance())
                .filter(discrepancy -> Objects.equals(normalize(discrepancy.getType() != null ? discrepancy.getType().name() : null), type))
                .count();
    }

    private static BigDecimal maxQuantityPercent(List<ItemDiscrepancy> discrepancies) {
        return discrepancies.stream()
                .map(ItemDiscrepancy::getVariancePercent)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private static int severityPenalty(BigDecimal percent) {
        if (percent == null) {
            return 0;
        }
        if (percent.compareTo(new BigDecimal("20")) >= 0) {
            return 18;
        }
        if (percent.compareTo(new BigDecimal("10")) >= 0) {
            return 12;
        }
        if (percent.compareTo(new BigDecimal("5")) >= 0) {
            return 7;
        }
        if (percent.compareTo(new BigDecimal("1")) >= 0) {
            return 3;
        }
        return 1;
    }

    private static BigDecimal baseline(BigDecimal left, BigDecimal right) {
        BigDecimal leftAbs = left.abs();
        BigDecimal rightAbs = right.abs();
        BigDecimal max = leftAbs.max(rightAbs);
        return max.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : max;
    }

    private static BigDecimal percent(BigDecimal variance, BigDecimal baseline) {
        if (variance == null || baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return variance.multiply(new BigDecimal("100"))
                .divide(baseline, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal maxPercent(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.max(right);
    }

    private static String normalize(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal positiveOrNull(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public record ToleranceAssessment(boolean withinTolerance,
                                      BigDecimal variance,
                                      BigDecimal variancePercent,
                                      String toleranceType,
                                      BigDecimal toleranceValue) {

        public static ToleranceAssessment notApplicable() {
            return new ToleranceAssessment(false, null, null, null, null);
        }
    }
}
