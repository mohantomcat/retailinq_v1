package com.recon.cloud.actuator;

import com.recon.cloud.domain.CloudConnectorStatusResponse;
import com.recon.cloud.service.CloudConnectorAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloudConnectorHealthIndicator implements HealthIndicator {

    private final CloudConnectorAdminService adminService;

    @Override
    public Health health() {
        CloudConnectorStatusResponse status = adminService.getStatus();

        Health.Builder builder;
        if (!status.isEnabled()) {
            builder = Health.outOfService();
        } else if ("FAILED".equalsIgnoreCase(status.getCheckpointStatus())) {
            builder = Health.down();
        } else {
            builder = Health.up();
        }

        return builder
                .withDetail("connectorName", status.getConnectorName())
                .withDetail("sourceName", status.getSourceName())
                .withDetail("tenantId", status.getTenantId())
                .withDetail("checkpointStatus", status.getCheckpointStatus())
                .withDetail("lastCursorId", status.getLastCursorId())
                .withDetail("lastSuccessTimestamp", status.getLastSuccessTimestamp())
                .withDetail("lastPolledTimestamp", status.getLastPolledTimestamp())
                .withDetail("errorCount", status.getErrorCount())
                .withDetail("ingestionCounts", status.getIngestionCounts())
                .withDetail("lastErrorMessage", status.getLastErrorMessage())
                .build();
    }
}
