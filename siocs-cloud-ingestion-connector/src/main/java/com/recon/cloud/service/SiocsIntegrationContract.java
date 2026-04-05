package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.integration.connector.IntegrationConnectorContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SiocsIntegrationContract implements IntegrationConnectorContract {

    private final CloudConnectorProperties properties;

    @Override
    public String connectorKey() {
        return properties.getConnectorName();
    }

    @Override
    public String connectorLabel() {
        return "SIOCS Cloud Connector";
    }

    @Override
    public String sourceSystem() {
        return properties.getSourceName();
    }

    @Override
    public String targetSystem() {
        return "RECON";
    }

    @Override
    public String flowKey() {
        return "siocs-canonical-transaction";
    }

    @Override
    public Set<String> supportedMessageTypes() {
        return Set.of("CANONICAL_TRANSACTION");
    }

    public String flowLabel() {
        return "SIOCS transaction feed";
    }

    public String connectorType() {
        return "ORDS_POLLING";
    }

    public String runtimeMode() {
        return "POLLING";
    }

    public String moduleKey() {
        return "siocs-cloud-connector";
    }

    public String businessObject() {
        return "RETAIL_TRANSACTION";
    }

    public String mappingName() {
        return "siocs-canonical-v1";
    }

    public String sourceSchemaKey() {
        return "siocs-transaction";
    }

    public String targetSchemaKey() {
        return "canonical-transaction";
    }

    public String mappingNotes() {
        return "SIOCS connector ingests mixed retail transaction domains and publishes POS and inventory flows onto dedicated raw topics";
    }
}
