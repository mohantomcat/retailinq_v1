package com.recon.api.domain;

import lombok.Data;

@Data
public class ReviewUserAccessRequest {
    private String decision;
    private String notes;
    private Integer nextReviewInDays;
    private Boolean deactivateUser;
}
