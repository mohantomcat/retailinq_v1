package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySummaryDto {
    private long totalRecords;
    private long operationsCount;
    private long configurationCount;
    private long exceptionCount;
    private long alertCount;
}
