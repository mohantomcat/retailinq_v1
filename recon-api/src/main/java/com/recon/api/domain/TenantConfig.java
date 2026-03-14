package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfig {
    private String tenantId;
    private String tenantName;
    private String timezone;
    private String countryCode;
    private String currencyCode;
    
    // Wire format — always ISO 8601 yyyy-MM-dd, never changes
    // dateFormat field kept for backward compat but unused for parsing
    private String dateFormat;

    // Display format — locale specific, used only for UI display
    // e.g. dd-MMM-yyyy (India/Egypt), MM/dd/yyyy (US),
    //      dd/MM/yyyy (UK), yyyy/MM/dd (Japan)
    private String dateDisplayFormat;

    private boolean active;
}