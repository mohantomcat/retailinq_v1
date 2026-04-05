package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SystemEndpointProfileDto {
    String systemName;
    String systemLabel;
    String selectedEndpointMode;
    String selectedConnectorModuleId;
    String selectedConnectorLabel;
    String selectedIntegrationConnectorKey;
    String selectedBaseUrlKey;
    List<SystemEndpointOptionDto> options;
}
