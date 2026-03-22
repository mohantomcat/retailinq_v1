package com.recon.api.domain;

import lombok.Data;

@Data
public class SaveExceptionEscalationPolicyRequest {
    private String policyName;
    private String reconView;
    private String minSeverity;
    private Integer minImpactScore;
    private boolean triggerOnSlaBreach;
    private Integer agingHours;
    private Integer inactivityHours;
    private String escalateToUsername;
    private String escalateToRoleName;
    private String targetSeverity;
    private String noteTemplate;
    private boolean active;
    private String description;
}
