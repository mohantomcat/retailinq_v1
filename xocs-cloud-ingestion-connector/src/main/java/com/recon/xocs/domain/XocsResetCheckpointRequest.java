package com.recon.xocs.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class XocsResetCheckpointRequest {
    private String lastSuccessTimestamp;
    private Long lastCursorId = 0L;
}
