package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayWindowOperationRequest {
    private String replayMode;
    private String fromBusinessDate;
    private String toBusinessDate;
    private String storeId;
    private String wkstnId;
}
