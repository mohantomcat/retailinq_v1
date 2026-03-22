package com.recon.api.service;

import com.recon.api.domain.SaveTenantOperatingModelRequest;
import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final String DEFAULT_TIMEZONE = "UTC";
    private static final String DEFAULT_COUNTRY_CODE = "US";
    private static final String DEFAULT_CURRENCY_CODE = "USD";
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_DATE_DISPLAY_FORMAT = "dd-MMM-yyyy";
    private static final String DEFAULT_WEEK_START_DAY = "MONDAY";
    private static final String DEFAULT_BUSINESS_DAYS = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY";
    private static final String DEFAULT_WORKDAY_START_TIME = "06:00";
    private static final String DEFAULT_WORKDAY_END_TIME = "22:00";

    private final TenantRepository tenantRepository;

    public TenantConfig getTenant(String tenantId) {
        TenantConfig config = tenantRepository.findById(tenantId)
                .orElseGet(() -> TenantConfig.builder()
                        .tenantId(tenantId)
                        .tenantName("Unknown")
                        .build());
        return normalizeConfig(config);
    }

    public List<TenantConfig> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::normalizeConfig)
                .toList();
    }

    @Transactional
    public TenantConfig saveOperatingModel(String tenantId, SaveTenantOperatingModelRequest request) {
        TenantConfig current = getTenant(tenantId);
        SaveTenantOperatingModelRequest normalized = normalizeRequest(current, request);
        if (!tenantRepository.updateOperatingModel(tenantId, normalized)) {
            throw new IllegalArgumentException("Tenant operating model could not be updated");
        }
        return getTenant(tenantId);
    }

    private TenantConfig normalizeConfig(TenantConfig config) {
        String tenantId = trimToNull(config.getTenantId());
        String tenantName = trimToNull(config.getTenantName());
        String countryCode = normalizeCountryCode(config.getCountryCode(), DEFAULT_COUNTRY_CODE);
        String timezone = normalizeTimezone(config.getTimezone(), DEFAULT_TIMEZONE);
        String currencyCode = normalizeCurrencyCode(config.getCurrencyCode(), defaultCurrencyForCountry(countryCode));
        String localeCode = normalizeLocaleCode(config.getLocaleCode(), defaultLocaleForCountry(countryCode));
        String dateFormat = defaultIfBlank(config.getDateFormat(), DEFAULT_DATE_FORMAT);
        String dateDisplayFormat = normalizeDateDisplayFormat(config.getDateDisplayFormat(), localeCode);
        String weekStartDay = normalizeDayOfWeek(config.getWeekStartDay(), DEFAULT_WEEK_START_DAY);
        String businessDays = normalizeBusinessDayCsv(config.getBusinessDays());
        String workdayStartTime = normalizeTime(config.getWorkdayStartTime(), DEFAULT_WORKDAY_START_TIME);
        String workdayEndTime = normalizeTime(config.getWorkdayEndTime(), DEFAULT_WORKDAY_END_TIME);
        if (!isWorkdayRangeValid(workdayStartTime, workdayEndTime)) {
            workdayStartTime = DEFAULT_WORKDAY_START_TIME;
            workdayEndTime = DEFAULT_WORKDAY_END_TIME;
        }

        return TenantConfig.builder()
                .tenantId(tenantId)
                .tenantName(tenantName == null ? "Unknown" : tenantName)
                .timezone(timezone)
                .countryCode(countryCode)
                .currencyCode(currencyCode)
                .localeCode(localeCode)
                .dateFormat(dateFormat)
                .dateDisplayFormat(dateDisplayFormat)
                .weekStartDay(weekStartDay)
                .businessDays(businessDays)
                .workdayStartTime(workdayStartTime)
                .workdayEndTime(workdayEndTime)
                .holidayCalendar(normalizeHolidayCsv(config.getHolidayCalendar()))
                .active(config.isActive() || config.getTenantId() == null)
                .build();
    }

    private SaveTenantOperatingModelRequest normalizeRequest(TenantConfig current,
                                                             SaveTenantOperatingModelRequest request) {
        SaveTenantOperatingModelRequest safeRequest = request != null ? request : new SaveTenantOperatingModelRequest();
        String countryCode = normalizeCountryCode(
                safeRequest.getCountryCode(),
                current.getCountryCode());
        String localeCode = normalizeLocaleCode(
                safeRequest.getLocaleCode(),
                current.getLocaleCode() != null ? current.getLocaleCode() : defaultLocaleForCountry(countryCode));
        String workdayStartTime = normalizeTime(
                safeRequest.getWorkdayStartTime(),
                current.getWorkdayStartTime());
        String workdayEndTime = normalizeTime(
                safeRequest.getWorkdayEndTime(),
                current.getWorkdayEndTime());
        if (!isWorkdayRangeValid(workdayStartTime, workdayEndTime)) {
            workdayStartTime = current.getWorkdayStartTime();
            workdayEndTime = current.getWorkdayEndTime();
        }

        return SaveTenantOperatingModelRequest.builder()
                .timezone(normalizeTimezone(safeRequest.getTimezone(), current.getTimezone()))
                .countryCode(countryCode)
                .currencyCode(normalizeCurrencyCode(
                        safeRequest.getCurrencyCode(),
                        current.getCurrencyCode() != null ? current.getCurrencyCode() : defaultCurrencyForCountry(countryCode)))
                .localeCode(localeCode)
                .dateDisplayFormat(normalizeDateDisplayFormat(
                        safeRequest.getDateDisplayFormat(),
                        localeCode != null ? localeCode : current.getLocaleCode()))
                .weekStartDay(normalizeDayOfWeek(safeRequest.getWeekStartDay(), current.getWeekStartDay()))
                .businessDays(normalizeBusinessDays(
                        safeRequest.getBusinessDays(),
                        current.getBusinessDays()))
                .workdayStartTime(workdayStartTime)
                .workdayEndTime(workdayEndTime)
                .holidayDates(normalizeHolidayDates(
                        safeRequest.getHolidayDates(),
                        current.getHolidayCalendar()))
                .build();
    }

    private List<String> normalizeBusinessDays(List<String> requestedDays, String fallbackCsv) {
        List<String> normalized = new ArrayList<>();
        List<String> source = requestedDays == null || requestedDays.isEmpty()
                ? parseCsv(fallbackCsv)
                : requestedDays;
        for (String day : source) {
            String normalizedDay = normalizeDayOfWeek(day, null);
            if (normalizedDay != null && !normalized.contains(normalizedDay)) {
                normalized.add(normalizedDay);
            }
        }
        if (normalized.isEmpty()) {
            return parseCsv(DEFAULT_BUSINESS_DAYS);
        }
        return normalized;
    }

    private List<String> normalizeHolidayDates(List<String> requestedDates, String fallbackCsv) {
        List<String> normalized = new ArrayList<>();
        List<String> source = requestedDates == null ? parseCsv(fallbackCsv) : requestedDates;
        for (String date : source) {
            String normalizedDate = normalizeHolidayDate(date);
            if (normalizedDate != null && !normalized.contains(normalizedDate)) {
                normalized.add(normalizedDate);
            }
        }
        return normalized;
    }

    private String normalizeBusinessDayCsv(String businessDays) {
        return String.join(",", normalizeBusinessDays(null, businessDays));
    }

    private String normalizeHolidayCsv(String holidayCalendar) {
        return String.join(",", normalizeHolidayDates(null, holidayCalendar));
    }

    private String normalizeHolidayDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeTimezone(String timezone, String fallback) {
        String candidate = defaultIfBlank(timezone, fallback);
        if (candidate == null) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(candidate).getId();
        } catch (Exception ex) {
            return fallback != null ? fallback : DEFAULT_TIMEZONE;
        }
    }

    private String normalizeCountryCode(String countryCode, String fallback) {
        String candidate = trimToNull(countryCode);
        if (candidate == null) {
            return fallback != null ? fallback : DEFAULT_COUNTRY_CODE;
        }
        return candidate.toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrencyCode(String currencyCode, String fallback) {
        String candidate = trimToNull(currencyCode);
        if (candidate == null) {
            return fallback != null ? fallback : DEFAULT_CURRENCY_CODE;
        }
        return candidate.toUpperCase(Locale.ROOT);
    }

    private String normalizeLocaleCode(String localeCode, String fallback) {
        String candidate = trimToNull(localeCode);
        if (candidate == null) {
            return fallback != null ? fallback : "en-US";
        }
        try {
            Locale locale = Locale.forLanguageTag(candidate);
            return locale.toLanguageTag();
        } catch (Exception ex) {
            return fallback != null ? fallback : "en-US";
        }
    }

    private String normalizeDateDisplayFormat(String pattern, String localeCode) {
        String fallback = preferredDateDisplayFormat(localeCode);
        String candidate = defaultIfBlank(pattern, fallback);
        try {
            DateTimeFormatter.ofPattern(candidate);
            return candidate;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String preferredDateDisplayFormat(String localeCode) {
        String normalizedLocale = normalizeLocaleCode(localeCode, "en-US");
        if (normalizedLocale.startsWith("en-US")) {
            return "MM/dd/yyyy";
        }
        if (normalizedLocale.startsWith("ja") || normalizedLocale.startsWith("ko") || normalizedLocale.startsWith("zh")) {
            return "yyyy-MM-dd";
        }
        return DEFAULT_DATE_DISPLAY_FORMAT;
    }

    private String normalizeDayOfWeek(String value, String fallback) {
        String candidate = trimToNull(value);
        if (candidate == null) {
            return fallback;
        }
        try {
            return DayOfWeek.valueOf(candidate.toUpperCase(Locale.ROOT)).name();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String normalizeTime(String value, String fallback) {
        String candidate = defaultIfBlank(value, fallback);
        if (candidate == null) {
            return fallback;
        }
        try {
            return LocalTime.parse(candidate).format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private boolean isWorkdayRangeValid(String startTime, String endTime) {
        try {
            return LocalTime.parse(startTime).isBefore(LocalTime.parse(endTime));
        } catch (Exception ex) {
            return false;
        }
    }

    private String defaultLocaleForCountry(String countryCode) {
        return switch (Objects.toString(countryCode, DEFAULT_COUNTRY_CODE).toUpperCase(Locale.ROOT)) {
            case "IN" -> "en-IN";
            case "GB" -> "en-GB";
            case "AE" -> "ar-AE";
            case "SA" -> "ar-SA";
            case "EG" -> "ar-EG";
            case "JP" -> "ja-JP";
            case "DE" -> "de-DE";
            case "FR" -> "fr-FR";
            case "ES" -> "es-ES";
            case "BR" -> "pt-BR";
            default -> "en-US";
        };
    }

    private String defaultCurrencyForCountry(String countryCode) {
        return switch (Objects.toString(countryCode, DEFAULT_COUNTRY_CODE).toUpperCase(Locale.ROOT)) {
            case "IN" -> "INR";
            case "GB" -> "GBP";
            case "AE" -> "AED";
            case "SA" -> "SAR";
            case "EG" -> "EGP";
            case "JP" -> "JPY";
            case "DE", "FR", "ES" -> "EUR";
            case "BR" -> "BRL";
            default -> DEFAULT_CURRENCY_CODE;
        };
    }

    private List<String> parseCsv(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return List.of();
        }
        return List.of(trimmed.split(",")).stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
