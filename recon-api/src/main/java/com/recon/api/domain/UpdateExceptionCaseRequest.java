package com.recon.api.domain;

import lombok.Data;

@Data
public class UpdateExceptionCaseRequest {
    private String reconView;
    private String caseStatus;
    private String reasonCode;
    private String assigneeUsername;
    private String notes;
}
