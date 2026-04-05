package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ReconModuleDto {
    String reconView;
    String tabId;
    String label;
    String targetSystem;
    String permissionCode;
    String groupCode;
    String groupLabel;
    Integer groupDisplayOrder;
    boolean groupSelectionRequired;
    Integer displayOrder;
    String configurationModuleId;
    List<String> operationsModuleIds;
    List<OperationModuleCatalogEntry> operationModules;
    List<String> integrationConnectorKeys;
}
