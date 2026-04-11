package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuarterlyAccessReviewCycleResponse {
    private int queuedUsers;
    private int alreadyQueuedUsers;
    private int usersMissingManager;
}
