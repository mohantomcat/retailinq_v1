package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionSuppressionRuleDto {
    private UUID id;
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
    private boolean active;
    private String description;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
}
