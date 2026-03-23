package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

@Service
public class ExceptionIncidentClassifierService {

    public IncidentClassification classify(ExceptionCase exceptionCase) {
        String reasonCode = Objects.toString(exceptionCase.getReasonCode(), "").toUpperCase(Locale.ROOT);
        String rootCauseCategory = Objects.toString(exceptionCase.getRootCauseCategory(), "").toUpperCase(Locale.ROOT);
        String reconStatus = Objects.toString(exceptionCase.getReconStatus(), "").toUpperCase(Locale.ROOT);

        if ("CONFIGURATION_ISSUE".equals(reasonCode) || "CONFIGURATION_ISSUE".equals(rootCauseCategory)) {
            return new IncidentClassification("CONFIGURATION_ISSUE", "Configuration issue");
        }
        if ("SOURCE_DATA_ISSUE".equals(reasonCode) || "SOURCE_DATA_ISSUE".equals(rootCauseCategory)) {
            return new IncidentClassification("SOURCE_DATA_ISSUE", "Source data issue");
        }
        if ("ITEM_SYNC_GAP".equals(reasonCode)
                || "ITEM_SYNC_GAP".equals(rootCauseCategory)
                || "ITEM_MISSING".equals(reconStatus)) {
            return new IncidentClassification("ITEM_SYNC_GAP", "Item sync gap");
        }
        if ("DUPLICATE_SUBMISSION".equals(reasonCode)
                || "DUPLICATE_PROCESSING".equals(rootCauseCategory)
                || reconStatus.startsWith("DUPLICATE_IN_")) {
            return new IncidentClassification("DUPLICATE_PROCESSING", "Duplicate processing");
        }
        if ("QUANTITY_VARIANCE".equals(reasonCode)
                || "TOTAL_CALCULATION_MISMATCH".equals(reasonCode)
                || "RECONCILIATION_VARIANCE".equals(rootCauseCategory)
                || reconStatus.contains("MISMATCH")) {
            return new IncidentClassification("RECONCILIATION_VARIANCE", "Transaction variance");
        }
        if (reconStatus.startsWith("PROCESSING_FAILED")) {
            return new IncidentClassification("PROCESSING_FAILURE", connectedSystemName(exceptionCase.getReconView()) + " processing failure");
        }
        if ("REPLICATION_LAG".equals(reasonCode)
                || "INTEGRATION_TIMING".equals(rootCauseCategory)
                || reconStatus.startsWith("MISSING_IN_")
                || reconStatus.startsWith("PROCESSING_PENDING")) {
            return new IncidentClassification("INTEGRATION_TIMING", connectedSystemName(exceptionCase.getReconView()) + " sync lag");
        }
        return new IncidentClassification("MANUAL_REVIEW", "Manual review required");
    }

    public String connectedSystemName(String reconView) {
        return switch (Objects.toString(reconView, "").toUpperCase(Locale.ROOT)) {
            case "XSTORE_SIM" -> "SIM";
            case "XSTORE_SIOCS" -> "SIOCS";
            case "XSTORE_XOCS" -> "XOCS";
            case "SIOCS_MFCS" -> "MFCS";
            default -> "Connector";
        };
    }

    public record IncidentClassification(String code, String title) {
    }
}
