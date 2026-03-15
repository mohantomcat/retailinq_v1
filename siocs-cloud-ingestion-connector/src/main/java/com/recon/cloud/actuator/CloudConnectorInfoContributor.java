package com.recon.cloud.actuator;

import com.recon.cloud.config.CloudConnectorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloudConnectorInfoContributor implements InfoContributor {

    private final CloudConnectorProperties properties;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("cloudConnector", java.util.Map.of(
                "enabled", properties.isEnabled(),
                "connectorName", properties.getConnectorName(),
                "sourceName", properties.getSourceName(),
                "tenantId", properties.getTenantId(),
                "downloadIntervalMs", properties.getDownloadIntervalMs(),
                "publishIntervalMs", properties.getPublishIntervalMs(),
                "batchSize", properties.getBatchSize(),
                "publisherBatchSize", properties.getPublisherBatchSize()
        ));
    }
}
