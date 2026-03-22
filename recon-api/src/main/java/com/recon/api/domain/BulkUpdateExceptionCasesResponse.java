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
public class BulkUpdateExceptionCasesResponse {
    private int requestedCount;
    private int updatedCount;
    private int failedCount;
    private List<BulkExceptionCaseFailureDto> failures;
}
