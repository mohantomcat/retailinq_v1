package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionPlaybookStepExecutionResponseDto {
    private ExceptionCaseDto caseData;
    private OperationActionResponseDto actionResult;
}
