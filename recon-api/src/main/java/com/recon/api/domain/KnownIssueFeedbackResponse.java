package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnownIssueFeedbackResponse {
    private UUID knownIssueId;
    private boolean helpful;
    private long helpfulCount;
    private long notHelpfulCount;
    private String message;
}
