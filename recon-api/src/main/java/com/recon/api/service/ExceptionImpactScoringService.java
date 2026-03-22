package com.recon.api.service;

import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExceptionImpactScoringService {

    private static final int HIGH_IMPACT_THRESHOLD = 70;

    private final ExceptionSlaService exceptionSlaService;
    private final ExceptionOperationalOwnershipService exceptionOperationalOwnershipService;
    private final ExceptionBusinessValueService exceptionBusinessValueService;

    public ImpactAssessment assess(ExceptionCase exceptionCase,
                                   BusinessValueContextDto businessValue,
                                   long storeOpenCaseCount,
                                   long repeatIssueCount,
                                   boolean pendingApproval) {
        int impactScore = severityScore(exceptionCase.getSeverity())
                + statusScore(exceptionCase.getReconStatus())
                + (isOverdue(exceptionCase) ? 26 : 0)
                + (isUnassigned(exceptionCase) ? 16 : 0)
                + (pendingApproval ? 5 : 0)
                + ownershipRiskScore(exceptionCase)
                + storeConcentrationScore(storeOpenCaseCount)
                + repeatIssueScore(repeatIssueCount)
                + exceptionBusinessValueService.impactBoost(businessValue)
                + ageScore(exceptionSlaService.resolveAgeHours(exceptionCase));
        return new ImpactAssessment(impactScore, impactBandInternal(impactScore));
    }

    public boolean isEscalated(ExceptionCase exceptionCase) {
        return "ESCALATED".equalsIgnoreCase(Objects.toString(exceptionCase.getEscalationState(), ""));
    }

    public int highImpactThreshold() {
        return HIGH_IMPACT_THRESHOLD;
    }

    public String impactBand(int impactScore) {
        return impactBandInternal(impactScore);
    }

    private int severityScore(String severity) {
        return switch (Objects.toString(severity, "").toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 30;
            case "HIGH" -> 22;
            case "MEDIUM" -> 12;
            case "LOW" -> 4;
            default -> 0;
        };
    }

    private int statusScore(String reconStatus) {
        String normalized = Objects.toString(reconStatus, "").toUpperCase(Locale.ROOT);
        if (normalized.startsWith("PROCESSING_FAILED")) {
            return 18;
        }
        if (normalized.startsWith("MISSING_IN_")) {
            return 14;
        }
        if (normalized.contains("MISMATCH") || "ITEM_MISSING".equals(normalized)) {
            return 12;
        }
        if (normalized.startsWith("DUPLICATE_IN_")) {
            return 10;
        }
        if (normalized.startsWith("PROCESSING_PENDING")) {
            return 8;
        }
        return 4;
    }

    private int storeConcentrationScore(long storeOpenCaseCount) {
        return (int) Math.min(18L, Math.max(0L, (storeOpenCaseCount - 1L) * 4L));
    }

    private int repeatIssueScore(long repeatIssueCount) {
        return (int) Math.min(18L, Math.max(0L, (repeatIssueCount - 1L) * 5L));
    }

    private int ownershipRiskScore(ExceptionCase exceptionCase) {
        if (exceptionOperationalOwnershipService.hasOwnershipGap(exceptionCase)) {
            return 10;
        }
        if (exceptionOperationalOwnershipService.isActionOverdue(exceptionCase)) {
            return 7;
        }
        if (exceptionOperationalOwnershipService.isActionDue(exceptionCase)) {
            return 4;
        }
        return 0;
    }

    private int ageScore(long caseAgeHours) {
        if (caseAgeHours >= 72) {
            return 14;
        }
        if (caseAgeHours >= 24) {
            return 10;
        }
        if (caseAgeHours >= 8) {
            return 6;
        }
        if (caseAgeHours >= 2) {
            return 3;
        }
        return 0;
    }

    private String impactBandInternal(int impactScore) {
        if (impactScore >= 85) {
            return "CRITICAL";
        }
        if (impactScore >= HIGH_IMPACT_THRESHOLD) {
            return "HIGH";
        }
        if (impactScore >= 45) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean isUnassigned(ExceptionCase exceptionCase) {
        return (exceptionCase.getAssigneeUsername() == null || exceptionCase.getAssigneeUsername().isBlank())
                && (exceptionCase.getAssignedRoleName() == null || exceptionCase.getAssignedRoleName().isBlank());
    }

    private boolean isOverdue(ExceptionCase exceptionCase) {
        return "BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(exceptionCase));
    }

    public record ImpactAssessment(int score, String band) {
    }
}
