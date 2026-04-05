package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ReconGroupSelectionDto {
    String groupCode;
    String groupLabel;
    String groupDescription;
    Integer displayOrder;
    boolean selectionRequired;
    String selectedReconView;
    List<ReconModuleDto> modules;
}
