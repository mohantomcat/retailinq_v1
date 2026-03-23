package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ReconJobTemplateDto {
    String templateKey;
    String templateName;
    String description;
    String reconView;
    String cronExpression;
    String windowType;
    String endOfDayLocalTime;
    Integer businessDateOffsetDays;
    List<ReconJobStepDto> steps;
}
