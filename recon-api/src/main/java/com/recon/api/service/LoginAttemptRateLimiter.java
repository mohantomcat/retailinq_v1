package com.recon.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LoginAttemptRateLimiter {

    private final ConcurrentMap<String, AttemptWindow> attempts =
            new ConcurrentHashMap<>();

    @Value("${app.security.auth.rate-limit-max-attempts:10}")
    private int maxAttempts;

    @Value("${app.security.auth.rate-limit-window-seconds:60}")
    private long windowSeconds;

    public void assertAllowed(String tenantId, String username) {
        if (maxAttempts <= 0 || windowSeconds <= 0) {
            return;
        }
        AttemptWindow window = attempts.get(key(tenantId, username));
        Instant now = Instant.now();
        if (window != null
                && window.expiresAt().isAfter(now)
                && window.count() >= maxAttempts) {
            throw new RuntimeException(
                    "Too many login attempts. Try again later.");
        }
    }

    public void recordFailure(String tenantId, String username) {
        if (maxAttempts <= 0 || windowSeconds <= 0) {
            return;
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(windowSeconds);
        attempts.compute(key(tenantId, username), (ignored, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                return new AttemptWindow(1, expiresAt);
            }
            return new AttemptWindow(current.count() + 1,
                    current.expiresAt());
        });
    }

    public void recordSuccess(String tenantId, String username) {
        attempts.remove(key(tenantId, username));
    }

    private String key(String tenantId, String username) {
        return Objects.toString(tenantId, "").trim().toLowerCase(Locale.ROOT)
                + "|"
                + Objects.toString(username, "").trim().toLowerCase(Locale.ROOT);
    }

    private record AttemptWindow(int count, Instant expiresAt) {
    }
}
