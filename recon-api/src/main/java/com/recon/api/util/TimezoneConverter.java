package com.recon.api.util;

import com.recon.api.domain.TenantConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public class TimezoneConverter {

    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOCAL_INPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private TimezoneConverter() {
    }

    public static String toDisplay(String timestamp,
                                   TenantConfig tenant) {
        if (timestamp == null || tenant == null) {
            return timestamp;
        }
        try {
            ZonedDateTime zonedDateTime = parseTimestamp(timestamp, tenant);
            String pattern = resolveDisplayPattern(tenant) + " HH:mm:ss z";
            return zonedDateTime.format(DateTimeFormatter.ofPattern(pattern, resolveLocale(tenant)));
        } catch (Exception e) {
            return timestamp;
        }
    }

    public static String dateToDisplay(String isoDate,
                                       TenantConfig tenant) {
        if (isoDate == null || tenant == null) {
            return isoDate;
        }
        try {
            return LocalDate.parse(isoDate, ISO_DATE)
                    .format(DateTimeFormatter.ofPattern(resolveDisplayPattern(tenant), resolveLocale(tenant)));
        } catch (Exception e) {
            return isoDate;
        }
    }

    public static boolean isValidIsoDate(String date) {
        if (date == null || date.isBlank()) {
            return false;
        }
        try {
            LocalDate.parse(date, ISO_DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String[] toUtcDateRange(String isoDate,
                                          TenantConfig tenant) {
        if (isoDate == null || tenant == null) {
            return new String[]{null, null};
        }
        try {
            ZoneId zone = resolveZone(tenant);
            LocalDate date = LocalDate.parse(isoDate, ISO_DATE);
            Instant start = date.atStartOfDay(zone).toInstant();
            Instant end = date.atTime(23, 59, 59)
                    .atZone(zone)
                    .toInstant();
            return new String[]{
                    start.toString(),
                    end.toString()
            };
        } catch (Exception e) {
            return new String[]{null, null};
        }
    }

    public static String toLocalDateTimeInput(String timestamp, TenantConfig tenant) {
        if (timestamp == null || tenant == null) {
            return timestamp;
        }
        try {
            return parseTimestamp(timestamp, tenant)
                    .toLocalDateTime()
                    .format(LOCAL_INPUT_FORMATTER);
        } catch (Exception ex) {
            return timestamp;
        }
    }

    public static LocalDateTime parseLocalDateTimeInput(String localDateTimeValue, TenantConfig tenant) {
        if (localDateTimeValue == null || tenant == null) {
            return null;
        }
        try {
            LocalDateTime tenantLocal = LocalDateTime.parse(localDateTimeValue, LOCAL_INPUT_FORMATTER);
            return tenantLocal.atZone(resolveZone(tenant))
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception ex) {
            return LocalDateTime.parse(localDateTimeValue);
        }
    }

    public static String nowUtc() {
        return Instant.now().toString();
    }

    private static ZonedDateTime parseTimestamp(String timestamp, TenantConfig tenant) {
        ZoneId tenantZone = resolveZone(tenant);
        if (timestamp.endsWith("Z")) {
            return Instant.parse(timestamp).atZone(tenantZone);
        }
        try {
            return OffsetDateTime.parse(timestamp)
                    .atZoneSameInstant(tenantZone);
        } catch (Exception ignored) {
            return LocalDateTime.parse(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(tenantZone);
        }
    }

    private static String resolveDisplayPattern(TenantConfig tenant) {
        String pattern = Objects.toString(tenant.getDateDisplayFormat(), "").trim();
        return pattern.isEmpty() ? "dd-MMM-yyyy" : pattern;
    }

    private static ZoneId resolveZone(TenantConfig tenant) {
        try {
            return ZoneId.of(tenant.getTimezone());
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    private static Locale resolveLocale(TenantConfig tenant) {
        String localeCode = Objects.toString(tenant.getLocaleCode(), "").trim();
        if (localeCode.isEmpty()) {
            return Locale.getDefault();
        }
        try {
            return Locale.forLanguageTag(localeCode);
        } catch (Exception ex) {
            return Locale.getDefault();
        }
    }
}
