package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PrivilegedActionAlertDto {
    private String id;
    private String actionType;
    private String title;
    private String detail;
    private String severity;
    private String actor;
    private String entityType;
    private String entityKey;
    private String status;
    private LocalDateTime eventAt;
}
