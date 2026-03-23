package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginOptionsResponse {
    private String tenantId;
    private String tenantName;
    private boolean localLoginEnabled;
    private String preferredLoginMode;
    private boolean oidcEnabled;
    private String oidcDisplayName;
    private boolean samlEnabled;
    private String samlDisplayName;
    private boolean apiKeyAuthEnabled;
}
