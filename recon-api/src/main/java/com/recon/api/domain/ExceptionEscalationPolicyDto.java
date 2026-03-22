package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionEscalationPolicyDto {
    private UUID id;
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
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
}
