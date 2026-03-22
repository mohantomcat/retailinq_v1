package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsHealthSummaryDto {
    private long totalModules;
    private long healthyModules;
    private long warningModules;
    private long criticalModules;
    private long staleModules;
    private long totalBacklogCount;
    private long activeCasesOnUnhealthyModules;
    private long breachedCasesOnUnhealthyModules;
}
