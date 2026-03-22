package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RootCauseSummaryDto {
    private long totalCases;
    private long classifiedCases;
    private long unclassifiedCases;
    private long activeCases;
    private long breachedCases;
    private double classificationRate;
    private String topReasonCode;
    private String topReasonLabel;
    private String topCategory;
    private String topCategoryLabel;
}
