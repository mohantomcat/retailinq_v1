package com.recon.api.domain;

import lombok.Data;

@Data
public class SystemEndpointSelectionRequest {
    private String systemName;
    private String endpointMode;
}
