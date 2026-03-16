package com.recon.xocs.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class XocsReplayWindowRequest {
    private String replayMode;
    private String fromBusinessDate;
    private String toBusinessDate;
    private String storeId;
    private String wkstnId;
}
