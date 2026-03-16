package com.recon.xocs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XocsConnectorActionResponse {
    private String action;
    private String status;
    private String message;
}
