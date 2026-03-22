package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionAutomationCenterResponse {
    private List<ExceptionRoutingRuleDto> routingRules;
    private List<ExceptionPlaybookDto> playbooks;
    private List<ExceptionSuppressionRuleDto> suppressionRules;
    private ExceptionSuppressionSummaryDto suppressionSummary;
    private List<ExceptionSuppressionAuditDto> recentSuppressionActivity;
}
