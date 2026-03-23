package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ReconJobStepRunDto {
    UUID id;
    UUID stepDefinitionId;
    Integer stepOrder;
    String stepLabel;
    String stepType;
    String moduleId;
    String actionKey;
    String runStatus;
    String startedAt;
    String completedAt;
    Long durationMs;
    String message;
    Object requestPayload;
    Object responsePayload;
}
