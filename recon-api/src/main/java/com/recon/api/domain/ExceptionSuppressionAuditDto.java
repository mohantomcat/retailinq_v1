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
public class ExceptionSuppressionAuditDto {
    private UUID id;
    private UUID caseId;
    private String transactionKey;
    private String reconView;
    private UUID ruleId;
    private String ruleName;
    private String actionType;
    private String resultStatus;
    private String resultMessage;
    private String createdBy;
    private String createdAt;
}
