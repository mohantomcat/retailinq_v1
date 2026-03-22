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
public class ExceptionApprovalCenterResponse {
    private ExceptionApprovalCenterSummaryDto summary;
    private List<ExceptionApprovalRequestDto> pendingApprovals;
    private List<ExceptionApprovalRequestDto> recentDecisions;
    private List<ExceptionClosurePolicyDto> closurePolicies;
}
