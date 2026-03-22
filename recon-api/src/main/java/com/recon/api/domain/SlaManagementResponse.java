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
public class SlaManagementResponse {
    private List<ExceptionSlaRuleDto> rules;
    private SlaSummaryDto summary;
    private List<SlaAgingBreakdownDto> agingByAssignee;
    private List<SlaAgingBreakdownDto> agingByStore;
    private List<SlaAgingBreakdownDto> agingByModule;
    private TenantOperatingModelDto operatingModel;
}
