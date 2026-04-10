package com.recon.api.domain;

import lombok.Data;

@Data
public class OidcLoginCallbackRequest {
    private String code;
    private String state;
    private String error;
    private String errorDescription;
}
