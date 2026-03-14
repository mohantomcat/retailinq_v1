package com.recon.poller.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiocsResetCheckpointRequest {
    private String lastProcessedTimestamp;
    private String lastProcessedExternalId;
    private Long lastProcessedId;
}
