package com.recon.cloud.actuator;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.cloud.domain.CloudConnectorStatusResponse;
import com.recon.cloud.service.CloudConnectorAdminService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@RequiredArgsConstructor
public class CloudConnectorMetricsPublisher {

    private final MeterRegistry meterRegistry;
    private final CloudConnectorAdminService adminService;
    private final CloudConnectorProperties properties;

    private final Map<String, AtomicLong> statusGauges = new HashMap<>();
    private final AtomicLong errorCountGauge = new AtomicLong();
    private final AtomicLong oldestReadyAgeSecondsGauge = new AtomicLong();
    private final AtomicLong oldestFailedAgeSecondsGauge = new AtomicLong();
    private final AtomicLong checkpointLagSecondsGauge = new AtomicLong();

    @PostConstruct
    void registerMeters() {
        registerStatusGauge("READY");
        registerStatusGauge("PROCESSING");
        registerStatusGauge("PUBLISHED");
        registerStatusGauge("FAILED");
        registerStatusGauge("DLQ");

        Gauge.builder("mfcs.connector.errors.total", errorCountGauge, AtomicLong::get)
                .description("Total cloud connector ingestion errors")
                .register(meterRegistry);
        Gauge.builder("mfcs.connector.backlog.ready.oldest.age.seconds",
                        oldestReadyAgeSecondsGauge, AtomicLong::get)
                .description("Age in seconds of the oldest READY ingestion row")
                .register(meterRegistry);
        Gauge.builder("mfcs.connector.backlog.failed.oldest.age.seconds",
                        oldestFailedAgeSecondsGauge, AtomicLong::get)
                .description("Age in seconds of the oldest FAILED ingestion row")
                .register(meterRegistry);
        Gauge.builder("mfcs.connector.checkpoint.lag.seconds",
                        checkpointLagSecondsGauge, AtomicLong::get)
                .description("Lag in seconds since the last successful checkpoint")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString =
            "${mfcs.connector.metrics-refresh-interval-ms:60000}")
    public void refresh() {
        try {
            CloudConnectorStatusResponse status = adminService.getStatus();
            statusGauges.forEach((key, value) ->
                    value.set(status.getIngestionCounts().getOrDefault(key, 0L)));
            errorCountGauge.set(status.getErrorCount());
            oldestReadyAgeSecondsGauge.set(ageInSeconds(status.getOldestReadyTimestamp()));
            oldestFailedAgeSecondsGauge.set(ageInSeconds(status.getOldestFailedTimestamp()));
            checkpointLagSecondsGauge.set(ageInSeconds(status.getLastSuccessTimestamp()));
        } catch (Exception e) {
            log.warn("Failed to refresh cloud connector metrics: {}", e.getMessage());
        }
    }

    private void registerStatusGauge(String status) {
        AtomicLong gauge = new AtomicLong();
        statusGauges.put(status, gauge);
        Gauge.builder("mfcs.connector.ingestion.status.count", gauge, AtomicLong::get)
                .description("Cloud connector ingestion row count by status")
                .tag("status", status)
                .register(meterRegistry);
    }

    private long ageInSeconds(Timestamp timestamp) {
        return timestamp == null ? 0L : ageInSeconds(timestamp.toInstant());
    }

    private long ageInSeconds(Instant instant) {
        return instant == null ? 0L : Math.max(0L,
                Duration.between(instant, Instant.now()).getSeconds());
    }
}
