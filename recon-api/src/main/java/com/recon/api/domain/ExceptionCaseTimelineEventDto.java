package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseTimelineEventDto {
    private String sourceType;
    private String eventType;
    private String title;
    private String summary;
    private String actor;
    private String status;
    private String eventAt;
}
