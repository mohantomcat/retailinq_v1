package com.recon.api.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@Profile("prod")
public class ProductionSecurityProfileValidator implements ApplicationRunner {

    private static final List<String> INSECURE_JWT_VALUES = List.of(
            "your-256-bit-secret-key-change-in-production-must-be-long",
            "local-development-jwt-secret-change-before-any-shared-environment",
            "test-jwt-secret-for-automated-tests-only");

    private final Environment environment;

    public ProductionSecurityProfileValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> failures = new ArrayList<>();

        String jwtSecret = required(failures, "app.jwt.secret", "APP_JWT_SECRET");
        if (jwtSecret != null && (jwtSecret.length() < 32
                || INSECURE_JWT_VALUES.contains(jwtSecret))) {
            failures.add("APP_JWT_SECRET must be a production-only secret with at least 32 characters.");
        }

        String pgPassword = required(failures, "spring.datasource.password", "PG_PASSWORD");
        if ("recon123".equals(pgPassword)) {
            failures.add("PG_PASSWORD must not use the local development password.");
        }

        required(failures, "app.operations.connector-admin-password", "CONNECTOR_ADMIN_PASSWORD");
        validateCors(failures);
        validateElasticsearch(failures);

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Production security profile validation failed:\n - "
                            + String.join("\n - ", failures));
        }
    }

    private void validateCors(List<String> failures) {
        String allowedOrigins = required(failures,
                "app.security.cors.allowed-origins",
                "APP_CORS_ALLOWED_ORIGINS");
        if (allowedOrigins == null) {
            return;
        }
        List<String> origins = csv(allowedOrigins);
        if (origins.stream().anyMatch(origin -> origin.equals("*"))) {
            failures.add("APP_CORS_ALLOWED_ORIGINS must not contain '*'.");
        }
        if (origins.stream().anyMatch(this::isLocalOrigin)) {
            failures.add("APP_CORS_ALLOWED_ORIGINS must not contain localhost or loopback origins in prod.");
        }
    }

    private void validateElasticsearch(List<String> failures) {
        String scheme = Objects.toString(
                environment.getProperty("elasticsearch.scheme"), "").trim();
        if (!"https".equalsIgnoreCase(scheme)) {
            failures.add("ES_SCHEME must be https in prod.");
        }
        required(failures, "elasticsearch.username", "ES_USER");
        required(failures, "elasticsearch.password", "ES_PASSWORD");
    }

    private String required(List<String> failures, String property, String envVar) {
        String value = trimToNull(environment.getProperty(property));
        if (value == null) {
            failures.add(envVar + " must be configured for the prod profile.");
        }
        return value;
    }

    private List<String> csv(String value) {
        return Arrays.stream(Objects.toString(value, "").split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private boolean isLocalOrigin(String origin) {
        String normalized = origin.toLowerCase(Locale.ROOT);
        return normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("[::1]");
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
