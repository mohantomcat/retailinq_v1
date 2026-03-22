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
    private String localeCode;

    // Wire format is always ISO 8601 yyyy-MM-dd.
    private String dateFormat;

    // Display-only pattern used for tenant-facing dates.
    private String dateDisplayFormat;
    private String weekStartDay;
    private String businessDays;
    private String workdayStartTime;
    private String workdayEndTime;
    private String holidayCalendar;
    private boolean active;
}
