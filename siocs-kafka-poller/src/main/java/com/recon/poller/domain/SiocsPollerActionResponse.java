package com.recon.poller.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiocsPollerActionResponse {
    private String action;
    private String status;
    private String message;
}
