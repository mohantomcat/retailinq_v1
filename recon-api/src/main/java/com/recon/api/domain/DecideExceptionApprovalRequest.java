package com.recon.api.domain;

import lombok.Data;

@Data
public class DecideExceptionApprovalRequest {
    private String decision;
    private String decisionNotes;
}
