package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class RootCauseTaxonomyService {

    private static final Map<String, String> LEGACY_REASON_MAP = Map.of(
            "QUANTITY_MISMATCH", "QUANTITY_VARIANCE",
            "TOTAL_MISMATCH", "TOTAL_CALCULATION_MISMATCH",
            "ITEM_MAPPING", "ITEM_SYNC_GAP",
            "DATA_QUALITY", "SOURCE_DATA_ISSUE",
            "CONFIGURATION", "CONFIGURATION_ISSUE"
    );

    private static final Map<String, String> CATEGORY_BY_REASON = Map.of(
            "REPLICATION_LAG", "INTEGRATION_TIMING",
            "DUPLICATE_SUBMISSION", "DUPLICATE_PROCESSING",
            "ITEM_SYNC_GAP", "ITEM_SYNC_GAP",
            "QUANTITY_VARIANCE", "RECONCILIATION_VARIANCE",
            "TOTAL_CALCULATION_MISMATCH", "RECONCILIATION_VARIANCE",
            "CONFIGURATION_ISSUE", "CONFIGURATION_ISSUE",
            "SOURCE_DATA_ISSUE", "SOURCE_DATA_ISSUE",
            "MANUAL_REVIEW_REQUIRED", "MANUAL_REVIEW"
    );

    public String normalizeReasonCode(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return LEGACY_REASON_MAP.getOrDefault(normalized, normalized);
    }

    public String normalizeCategory(String value) {
        return normalize(value);
    }

    public String deriveCategory(String reasonCode) {
        String normalizedReason = normalizeReasonCode(reasonCode);
        if (normalizedReason == null) {
            return null;
        }
        return CATEGORY_BY_REASON.get(normalizedReason);
    }

    public String effectiveReasonCode(ExceptionCase exceptionCase) {
        String reasonCode = normalizeReasonCode(exceptionCase.getReasonCode());
        return reasonCode != null ? reasonCode : "UNCLASSIFIED";
    }

    public String effectiveCategory(ExceptionCase exceptionCase) {
        String category = normalizeCategory(exceptionCase.getRootCauseCategory());
        if (category != null) {
            return category;
        }
        category = deriveCategory(exceptionCase.getReasonCode());
        return category != null ? category : "UNCLASSIFIED";
    }

    public String labelForReason(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "Unclassified";
        }
        return humanize(reasonCode);
    }

    public String labelForCategory(String category) {
        if (category == null || category.isBlank()) {
            return "Unclassified";
        }
        return humanize(category);
    }

    public String labelForGenericKey(String key) {
        if (key == null || key.isBlank()) {
            return "Unclassified";
        }
        return humanize(key);
    }

    private String normalize(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private String humanize(String value) {
        return value.replace('_', ' ');
    }
}
