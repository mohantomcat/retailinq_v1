package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsCommandCenterActionDto {
    private String title;
    private String detail;
    private String severity;
    private String ownerLane;
    private String targetTab;
}
