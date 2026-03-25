package com.recon.xocs.service;

import com.recon.integration.connector.IntegrationConnectorContract;
import com.recon.xocs.config.XocsConnectorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class XocsIntegrationContract implements IntegrationConnectorContract {

    private final XocsConnectorProperties properties;

    @Override
    public String connectorKey() {
        return properties.getConnectorName();
    }

    @Override
    public String connectorLabel() {
        return "XOCS Cloud Connector";
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
        return "xocs-pos-transaction";
    }

    @Override
    public Set<String> supportedMessageTypes() {
        return Set.of(messageType());
    }

    public String flowLabel() {
        return "XOCS POS transaction feed";
    }

    public String connectorType() {
        return "ORDS_POLLING";
    }

    public String runtimeMode() {
        return "POLLING";
    }

    public String moduleKey() {
        return "xocs-cloud-connector";
    }

    public String businessObject() {
        return "POS_TRANSACTION";
    }

    public String mappingName() {
        return "xocs-canonical-v1";
    }

    public String messageType() {
        return "POS_TRANSACTION";
    }

    public String sourceSchemaKey() {
        return "xocs-pos-transaction";
    }

    public String targetSchemaKey() {
        return "canonical-transaction";
    }

    public String mappingNotes() {
        return "Pilot canonical mapping for XOCS ingestion";
    }
}
