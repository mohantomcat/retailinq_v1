package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OperationModuleCatalogEntry {

    private String reconView;
    private String moduleId;
    private String moduleLabel;
    private String category;
    private String baseUrlKey;
    private String statusPath;
    private String actionPathPrefix;

    @Default
    private List<String> safeActions = List.of();

    @Default
    private List<String> advancedActions = List.of();

    @Default
    private boolean basicAuth = false;

    private String resetPayloadMode;
    private Integer freshnessThresholdMinutes;

    @Default
    private boolean supportsRegisterFilter = false;
}
