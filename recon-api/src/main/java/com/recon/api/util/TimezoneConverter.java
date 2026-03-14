package com.recon.api.util;

import com.recon.api.domain.TenantConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimezoneConverter {

    // Universal wire format — ISO 8601, never changes
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Convert UTC timestamp to tenant local display format.
     * e.g. "2025-11-26T10:30:00Z" ->
     * "26-Nov-2025 16:00:00 IST" for India tenant
     */
    public static String toDisplay(String utcTimestamp,
                                   TenantConfig tenant) {
        if (utcTimestamp == null || tenant == null)
            return utcTimestamp;
        try {
            ZoneId zone = ZoneId.of(tenant.getTimezone());
            String displayFmt = tenant.getDateDisplayFormat() != null
                    ? tenant.getDateDisplayFormat() : "dd-MMM-yyyy";
            String pattern = displayFmt + " HH:mm:ss z";
            return Instant.parse(utcTimestamp)
                    .atZone(zone)
                    .format(DateTimeFormatter
                            .ofPattern(pattern)
                            .withZone(zone));
        } catch (Exception e) {
            return utcTimestamp;
        }
    }

    /**
     * Format ISO date for display in tenant locale.
     * e.g. "2025-11-26" -> "26-Nov-2025" for India
     * -> "11/26/2025" for US
     * -> "26/11/2025" for UK
     */
    public static String dateToDisplay(String isoDate,
                                       TenantConfig tenant) {
        if (isoDate == null || tenant == null) return isoDate;
        try {
            String displayFmt = tenant.getDateDisplayFormat() != null
                    ? tenant.getDateDisplayFormat() : "dd-MMM-yyyy";
            return LocalDate.parse(isoDate, ISO_DATE)
                    .format(DateTimeFormatter.ofPattern(displayFmt));
        } catch (Exception e) {
            return isoDate;
        }
    }

    /**
     * Validate incoming API date is ISO 8601 yyyy-MM-dd.
     * All API consumers must send dates in this format.
     */
    public static boolean isValidIsoDate(String date) {
        if (date == null || date.isBlank()) return false;
        try {
            LocalDate.parse(date, ISO_DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Convert ISO business date to UTC timestamp range
     * for reconciledAt range queries in Elasticsearch.
     * Accounts for tenant timezone — a business date of
     * 2025-11-26 in IST (UTC+5:30) spans
     * 2025-11-25T18:30:00Z to 2025-11-26T18:29:59Z
     */
    public static String[] toUtcDateRange(String isoDate,
                                          TenantConfig tenant) {
        if (isoDate == null || tenant == null)
            return new String[]{null, null};
        try {
            ZoneId zone = ZoneId.of(tenant.getTimezone());
            LocalDate date = LocalDate.parse(isoDate, ISO_DATE);
            Instant start = date.atStartOfDay(zone).toInstant();
            Instant end = date.atTime(23, 59, 59)
                    .atZone(zone).toInstant();
            return new String[]{
                    start.toString(),
                    end.toString()
            };
        } catch (Exception e) {
            return new String[]{null, null};
        }
    }

    public static String nowUtc() {
        return Instant.now().toString();
    }
}