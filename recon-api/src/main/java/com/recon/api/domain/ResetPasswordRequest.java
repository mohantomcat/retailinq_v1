package com.recon.api.domain;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String newPassword;
}