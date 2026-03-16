package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRecordDto {
    private String sourceType;
    private String moduleKey;
    private String actionType;
    private String actor;
    private String title;
    private String summary;
    private String referenceKey;
    private String status;
    private LocalDateTime eventTimestamp;
}
