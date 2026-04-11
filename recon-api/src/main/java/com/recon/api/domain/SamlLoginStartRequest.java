package com.recon.api.domain;

import lombok.Data;

@Data
public class SamlLoginStartRequest {
    private String tenantId;
}
