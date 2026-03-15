package com.recon.xocs.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class IdcsAccessTokenResponse {
    @JsonAlias("access_token")
    private String accessToken;
    @JsonAlias("expires_in")
    private Long expiresIn;
    @JsonAlias("token_type")
    private String tokenType;
}
