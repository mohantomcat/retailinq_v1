package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SamlLoginStartResponse {
    private String redirectUrl;
    private String relayState;
    private LocalDateTime expiresAt;
}
