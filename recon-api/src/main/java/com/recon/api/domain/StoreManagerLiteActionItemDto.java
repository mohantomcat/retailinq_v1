package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreManagerLiteActionItemDto {
    private String storeId;
    private String incidentKey;
    private String incidentTitle;
    private String ownershipStatus;
    private String actionLabel;
    private String actionReason;
    private String ownerSummary;
    private String dueAt;
    private String priorityReason;
    private Integer impactScore;
    private String impactBand;
    private BusinessValueContextDto businessValue;
}
