package com.recon.api.domain;

import lombok.Data;

@Data
public class SubmitKnownIssueFeedbackRequest {
    private Boolean helpful;
    private String transactionKey;
    private String reconView;
    private String incidentKey;
    private String storeId;
    private String sourceView;
    private String feedbackNotes;
}
