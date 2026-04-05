package com.recon.rms.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TenantTimeZoneUtil {

    public static final ZoneId UTC = ZoneId.of("UTC");

    /**
     * Convert a local timestamp string from Oracle (in the tenant timezone) to UTC.
     * Used when reading timestamps from source Oracle systems.
     */
    public static Instant toUtc(String localTimestamp,
                                String tenantTimezone) {
        if (localTimestamp == null || localTimestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(localTimestamp);
        } catch (Exception e) {
            try {
                ZoneId zone = ZoneId.of(tenantTimezone);
                ZonedDateTime zdt = ZonedDateTime.parse(
                        localTimestamp,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(zone));
                return zdt.toInstant();
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }

    public static String toTenantDisplay(String utcTimestamp,
                                         String tenantTimezone,
                                         String dateFormat) {
        if (utcTimestamp == null) {
            return null;
        }
        try {
            ZoneId zone = ZoneId.of(tenantTimezone);
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern(dateFormat + " HH:mm:ss z").withZone(zone);
            return Instant.parse(utcTimestamp)
                    .atZone(zone)
                    .format(formatter);
        } catch (Exception e) {
            return utcTimestamp;
        }
    }

    public static String nowUtc() {
        return Instant.now().toString();
    }

    public static boolean isValidTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
