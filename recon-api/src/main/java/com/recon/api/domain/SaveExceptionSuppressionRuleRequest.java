package com.recon.api.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaveExceptionSuppressionRuleRequest {
    private String ruleName;
    private String reconView;
    private String reconStatus;
    private String maxSeverity;
    private String rootCauseCategory;
    private String reasonCode;
    private String storeId;
    private String actionType;
    private BigDecimal maxValueAtRisk;
    private Integer minRepeatCount;
    private boolean active = true;
    private String description;
}
