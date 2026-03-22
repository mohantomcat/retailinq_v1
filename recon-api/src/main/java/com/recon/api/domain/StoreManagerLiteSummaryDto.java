package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreManagerLiteSummaryDto {
    private long storesInView;
    private long activeIncidents;
    private long affectedTransactions;
    private long todayAffectedTransactions;
    private long openCases;
    private long actionRequiredIncidents;
    private long overdueActionIncidents;
    private BusinessValueContextDto businessValue;
}
