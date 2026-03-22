package com.recon.api.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SaveExceptionPlaybookRequest {
    private String playbookName;
    private String reconView;
    private String reconStatus;
    private String minSeverity;
    private String rootCauseCategory;
    private String reasonCode;
    private boolean active = true;
    private String description;
    private List<SaveExceptionPlaybookStepRequest> steps = new ArrayList<>();
}
