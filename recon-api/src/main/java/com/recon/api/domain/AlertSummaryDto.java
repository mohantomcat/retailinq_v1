package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertSummaryDto {
    private long activeRules;
    private long openEvents;
    private long acknowledgedEvents;
    private long resolvedEvents;
    private long criticalEvents;
}
