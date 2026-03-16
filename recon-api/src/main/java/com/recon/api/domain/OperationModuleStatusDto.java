package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationModuleStatusDto {
    private String moduleId;
    private String moduleLabel;
    private String reconView;
    private String category;
    private boolean reachable;
    private List<String> availableActions;
    private List<String> advancedActions;
    private Map<String, Object> status;
}
