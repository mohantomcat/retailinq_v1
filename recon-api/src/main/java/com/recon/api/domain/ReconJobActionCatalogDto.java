package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ReconJobActionCatalogDto {
    String moduleId;
    String moduleLabel;
    String reconView;
    List<String> availableActions;
}
