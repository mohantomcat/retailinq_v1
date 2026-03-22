package com.recon.api.domain;

import lombok.Data;

@Data
public class SaveExceptionRoutingRuleRequest {
    private String ruleName;
    private String reconView;
    private String reconStatus;
    private String minSeverity;
    private String rootCauseCategory;
    private String reasonCode;
    private String storeId;
    private String targetAssigneeUsername;
    private String targetRoleName;
    private Integer priority;
    private boolean active = true;
    private String description;
}
