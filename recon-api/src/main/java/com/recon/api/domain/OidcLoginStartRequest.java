package com.recon.api.domain;

import lombok.Data;

@Data
public class OidcLoginStartRequest {
    private String tenantId;
}
