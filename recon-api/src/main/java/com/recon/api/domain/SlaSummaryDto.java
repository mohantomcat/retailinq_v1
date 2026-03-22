package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaSummaryDto {
    private long activeCases;
    private long breachedCases;
    private long dueSoonCases;
    private long withinSlaCases;
    private double breachRate;
}
