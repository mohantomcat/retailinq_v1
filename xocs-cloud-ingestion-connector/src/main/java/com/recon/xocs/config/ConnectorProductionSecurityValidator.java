package com.recon.xocs.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@Profile("prod")
public class ConnectorProductionSecurityValidator implements ApplicationRunner {

    private final Environment environment;

    public ConnectorProductionSecurityValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> failures = new ArrayList<>();
        required(failures, "spring.datasource.password", "PG_PASSWORD");
        validateKafka(failures);
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Production connector security validation failed:\n - "
                            + String.join("\n - ", failures));
        }
    }

    private void validateKafka(List<String> failures) {
        String protocol = trimToNull(environment.getProperty(
                "spring.kafka.properties.security.protocol"));
        if (protocol == null || "PLAINTEXT".equals(protocol.toUpperCase(Locale.ROOT))) {
            failures.add("KAFKA_SECURITY_PROTOCOL must be configured and must not be PLAINTEXT in prod.");
        }
    }

    private void required(List<String> failures, String property, String envVar) {
        if (trimToNull(environment.getProperty(property)) == null) {
            failures.add(envVar + " must be configured for the prod profile.");
        }
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
