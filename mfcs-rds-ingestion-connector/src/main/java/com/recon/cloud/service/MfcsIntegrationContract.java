package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import com.recon.integration.connector.IntegrationConnectorContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class MfcsIntegrationContract implements IntegrationConnectorContract {

    private final CloudConnectorProperties properties;

    @Override
    public String connectorKey() {
        return properties.getConnectorName();
    }

    @Override
    public String connectorLabel() {
        return "MFCS RDS Connector";
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
        return "mfcs-canonical-transaction";
    }

    @Override
    public Set<String> supportedMessageTypes() {
        return Set.of("CANONICAL_TRANSACTION");
    }

    public String flowLabel() {
        return "MFCS canonical transaction feed";
    }

    public String connectorType() {
        return "ORDS_POLLING";
    }

    public String runtimeMode() {
        return "POLLING";
    }

    public String moduleKey() {
        return "mfcs-rds-connector";
    }

    public String businessObject() {
        return "INVENTORY_TRANSACTION";
    }

    public String mappingName() {
        return "mfcs-canonical-v1";
    }

    public String sourceSchemaKey() {
        return "mfcs-transaction";
    }

    public String targetSchemaKey() {
        return "canonical-transaction";
    }

    public String mappingNotes() {
        return "Pilot canonical mapping for MFCS ingestion";
    }
}
