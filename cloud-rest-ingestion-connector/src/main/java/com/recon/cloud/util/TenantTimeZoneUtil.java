package com.recon.cloud.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TenantTimeZoneUtil {

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

    public static String nowUtc() {
        return Instant.now().toString();
    }
}
