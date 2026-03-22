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
public class SaveTenantOperatingModelRequest {
    private String timezone;
    private String countryCode;
    private String currencyCode;
    private String localeCode;
    private String dateDisplayFormat;
    private String weekStartDay;
    private List<String> businessDays;
    private String workdayStartTime;
    private String workdayEndTime;
    private List<String> holidayDates;
}
