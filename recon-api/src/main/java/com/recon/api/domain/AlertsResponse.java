package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertsResponse {
    private AlertSummaryDto summary;
    private List<AlertRuleDto> rules;
    private List<AlertEventDto> events;
}
