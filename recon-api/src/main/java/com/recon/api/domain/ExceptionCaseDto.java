package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseDto {
    private UUID id;
    private String tenantId;
    private String transactionKey;
    private String reconView;
    private String caseStatus;
    private String reasonCode;
    private String assigneeUsername;
    private String notes;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
    private List<ExceptionCommentDto> comments;
}
