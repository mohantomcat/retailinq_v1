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
public class StoreManagerLiteResponse {
    private String asOfBusinessDate;
    private TenantOperatingModelDto operatingModel;
    private List<String> storeOptions;
    private List<ExceptionIntegrationChannelDto> ticketChannels;
    private List<ExceptionIntegrationChannelDto> communicationChannels;
    private StoreManagerLiteSummaryDto summary;
    private List<StoreManagerLiteActionItemDto> actionItems;
    private List<StoreManagerLiteIncidentDto> incidents;
}
