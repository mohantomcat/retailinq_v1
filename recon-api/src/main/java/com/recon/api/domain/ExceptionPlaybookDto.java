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
public class ExceptionPlaybookDto {
    private UUID id;
    private String playbookName;
    private String reconView;
    private String reconStatus;
    private String minSeverity;
    private String rootCauseCategory;
    private String reasonCode;
    private boolean active;
    private String description;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
    private List<ExceptionPlaybookStepDto> steps;
}
