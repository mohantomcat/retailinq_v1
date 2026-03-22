package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOperatingModelDto {
    private String tenantId;
    private String tenantName;
    private String timezone;
    private String countryCode;
    private String currencyCode;
    private String localeCode;
    private String dateDisplayFormat;
    private String weekStartDay;
    private List<String> businessDays;
    private String businessDaysLabel;
    private String workdayStartTime;
    private String workdayEndTime;
    private String workdayHoursLabel;
    private List<String> holidayDates;
    private long holidayCount;
    private String currentLocalTime;
    private String currentBusinessDate;
    private boolean calendarAwareSla;
}
