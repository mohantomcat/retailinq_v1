package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SystemEndpointOptionDto {
    String systemName;
    String systemLabel;
    String endpointMode;
    String connectorModuleId;
    String connectorLabel;
    String integrationConnectorKey;
    String baseUrlKey;
    Integer displayOrder;
    boolean implemented;
    boolean defaultSelection;
    String notes;
}
