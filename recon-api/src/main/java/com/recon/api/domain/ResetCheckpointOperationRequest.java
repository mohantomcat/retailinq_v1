package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetCheckpointOperationRequest {
    private String resetMode;
    private String timestamp;
    private String businessDate;
    private String externalId;
    private Long cursorId;
    private Long processedId;
}
