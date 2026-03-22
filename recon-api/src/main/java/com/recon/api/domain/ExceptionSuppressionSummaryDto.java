package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionSuppressionSummaryDto {
    private long ruleCount;
    private long activeRuleCount;
    private long autoResolvedLast7Days;
    private long suppressedLast7Days;
}
