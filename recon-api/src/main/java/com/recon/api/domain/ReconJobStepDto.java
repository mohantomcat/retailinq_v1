package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ReconJobStepDto {
    UUID id;
    Integer stepOrder;
    String stepLabel;
    String stepType;
    String moduleId;
    String actionKey;
    UUID dependsOnStepId;
    String dependsOnStepLabel;
    Integer settleDelaySeconds;
}
