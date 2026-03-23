package com.recon.api.domain;

import lombok.Data;

@Data
public class SaveReconJobStepRequest {
    private String clientStepKey;
    private String stepLabel;
    private String stepType;
    private Integer stepOrder;
    private String moduleId;
    private String actionKey;
    private String dependsOnClientStepKey;
    private Integer settleDelaySeconds;
}
