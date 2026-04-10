package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OidcLoginStartResponse {
    private String authorizationUrl;
    private String state;
    private LocalDateTime expiresAt;
}
