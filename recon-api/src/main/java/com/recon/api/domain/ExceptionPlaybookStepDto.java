package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionPlaybookStepDto {
    private UUID id;
    private Integer stepOrder;
    private String stepTitle;
    private String stepDetail;
    private String operationModuleId;
    private String operationActionKey;
    private boolean actionConfigured;
    private boolean actionExecutable;
    private String actionExecutionMode;
    private String actionSupportMessage;
}
