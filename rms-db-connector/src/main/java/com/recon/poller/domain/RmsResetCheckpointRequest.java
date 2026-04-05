package com.recon.rms.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RmsResetCheckpointRequest {
    private String lastProcessedTimestamp;
    private String lastProcessedExternalId;
    private Long lastProcessedId;
}

