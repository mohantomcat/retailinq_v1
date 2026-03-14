package com.recon.publisher.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class TimestampNormalizer {

    private static final Duration MAX_FUTURE = Duration.ofHours(1);
    private static final Duration MAX_PAST = Duration.ofDays(7);

    /**
     * Original method — assumes timestamp is already UTC or ISO.
     */
    public NormalizedTimestamp normalize(String raw, long storeId) {
        return normalizeWithTimezone(raw, storeId, "UTC");
    }

    /**
     * New method — converts tenant local time to UTC.
     * Use this for all Oracle source timestamps.
     */
    public NormalizedTimestamp normalizeWithTimezone(
            String raw, long storeId, String tenantTimezone) {

        if (raw == null || raw.isBlank()) {
            log.warn("Null/blank timestamp store={} — using now()",
                    storeId);
            return NormalizedTimestamp.fallback(Instant.now());
        }

        Instant parsed;
        try {
            // Try UTC ISO instant first
            parsed = Instant.parse(raw);
        } catch (Exception e) {
            try {
                // Try parsing as tenant local datetime
                ZoneId zone = ZoneId.of(
                        tenantTimezone != null
                                ? tenantTimezone : "UTC");
                LocalDateTime ldt = LocalDateTime.parse(raw,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                parsed = ldt.atZone(zone).toInstant();
            } catch (Exception ex) {
                log.warn("Unparseable timestamp={} store={} " +
                                "tz={} — using now()",
                        raw, storeId, tenantTimezone);
                return NormalizedTimestamp.fallback(Instant.now());
            }
        }

        Instant now = Instant.now();

        if (parsed.isAfter(now.plus(MAX_FUTURE))) {
            log.warn("Future timestamp store={} " +
                            "drift={}min — capping to now()",
                    storeId,
                    Duration.between(now, parsed).toMinutes());
            return NormalizedTimestamp.normalized(now, true);
        }

        if (parsed.isBefore(now.minus(MAX_PAST))) {
            log.warn("Very old timestamp store={} ts={}", storeId, raw);
            return NormalizedTimestamp.normalized(parsed, true);
        }

        return NormalizedTimestamp.normalized(parsed, false);
    }

    public static class NormalizedTimestamp {
        private final Instant timestamp;
        private final boolean wasDrifted;

        private NormalizedTimestamp(Instant ts, boolean drifted) {
            this.timestamp = ts;
            this.wasDrifted = drifted;
        }

        public static NormalizedTimestamp normalized(
                Instant ts, boolean drifted) {
            return new NormalizedTimestamp(ts, drifted);
        }

        public static NormalizedTimestamp fallback(Instant ts) {
            return new NormalizedTimestamp(ts, true);
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public boolean isWasDrifted() {
            return wasDrifted;
        }
    }
}