package com.recon.api.domain;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String email;
}