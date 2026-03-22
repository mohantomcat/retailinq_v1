package com.recon.api.domain;

import lombok.Data;

import java.util.UUID;

@Data
public class SaveExceptionPlaybookStepRequest {
    private UUID id;
    private Integer stepOrder;
    private String stepTitle;
    private String stepDetail;
    private String operationModuleId;
    private String operationActionKey;
}
