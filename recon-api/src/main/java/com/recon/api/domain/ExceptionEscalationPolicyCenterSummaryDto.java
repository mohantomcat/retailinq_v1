package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionEscalationPolicyCenterSummaryDto {
    private long activePolicies;
    private long currentlyEscalatedCases;
    private long slaTriggeredPolicies;
    private long agingTriggeredPolicies;
    private long inactivityTriggeredPolicies;
}
