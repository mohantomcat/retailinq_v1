package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationEntryDto {
    private String key;
    private String label;
    private String description;
    private String envVar;
    private String defaultValue;
    private String effectiveValue;
    private String overrideValue;
    private boolean sensitive;
    private boolean editable;
    private String applyMode;
}
