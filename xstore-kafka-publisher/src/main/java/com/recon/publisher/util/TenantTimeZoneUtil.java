package com.recon.publisher.util;
// (use com.recon.poller.util for siocs-kafka-poller)

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TenantTimeZoneUtil {

    // All internal processing uses UTC.
    // This util is used ONLY for:
    // 1. Converting Oracle source timestamps to UTC on read
    // 2. Formatting display timestamps for API responses

    public static final ZoneId UTC = ZoneId.of("UTC");

    /**
     * Convert a local timestamp string from Oracle
     * (in tenant's timezone) to UTC Instant.
     * Used when reading timestamps from Xstore/SIOCS Oracle.
     */
    public static Instant toUtc(String localTimestamp,
                                String tenantTimezone) {
        if (localTimestamp == null
                || localTimestamp.isBlank()) {
            return Instant.now();
        }
        try {
            // Try parsing as ISO instant first (already UTC)
            return Instant.parse(localTimestamp);
        } catch (Exception e) {
            try {
                // Parse as local datetime in tenant timezone
                ZoneId zone = ZoneId.of(tenantTimezone);
                ZonedDateTime zdt = ZonedDateTime.parse(
                        localTimestamp,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                                .withZone(zone));
                return zdt.toInstant();
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }

    /**
     * Format a UTC Instant for display in tenant's local timezone.
     * Used ONLY in API response layer — never in storage/processing.
     */
    public static String toTenantDisplay(
            String utcTimestamp,
            String tenantTimezone,
            String dateFormat) {
        if (utcTimestamp == null) return null;
        try {
            ZoneId zone = ZoneId.of(tenantTimezone);
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern(
                                    dateFormat + " HH:mm:ss z")
                            .withZone(zone);
            return Instant.parse(utcTimestamp)
                    .atZone(zone)
                    .format(formatter);
        } catch (Exception e) {
            return utcTimestamp;
        }
    }

    /**
     * Get current UTC timestamp as ISO string.
     * Use this everywhere instead of LocalDateTime.now()
     * or new Date().
     */
    public static String nowUtc() {
        return Instant.now().toString();
    }

    /**
     * Validate timezone string is a valid IANA timezone.
     * Use when accepting timezone from API or config.
     */
    public static boolean isValidTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}