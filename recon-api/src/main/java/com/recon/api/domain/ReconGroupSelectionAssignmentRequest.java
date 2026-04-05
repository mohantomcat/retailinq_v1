package com.recon.api.domain;

import lombok.Data;

@Data
public class ReconGroupSelectionAssignmentRequest {
    private String groupCode;
    private String selectedReconView;
}
