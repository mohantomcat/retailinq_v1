package com.recon.api.domain;

import lombok.Data;

@Data
public class SaveExceptionSlaRuleRequest {
    private Integer targetMinutes;
    private String description;
}
