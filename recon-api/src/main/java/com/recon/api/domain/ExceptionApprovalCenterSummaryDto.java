package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionApprovalCenterSummaryDto {
    private long pendingApprovals;
    private long overduePendingApprovals;
    private long approvedLast7Days;
    private long rejectedLast7Days;
    private long activePolicies;
}
