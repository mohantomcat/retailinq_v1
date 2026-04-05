package com.recon.api.domain;

import lombok.Data;

import java.util.List;

@Data
public class SaveTenantReconGroupSelectionsRequest {
    private List<ReconGroupSelectionAssignmentRequest> selections;
}
