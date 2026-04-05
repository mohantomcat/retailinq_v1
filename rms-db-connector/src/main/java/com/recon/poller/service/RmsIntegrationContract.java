package com.recon.rms.service;

import com.recon.integration.connector.IntegrationConnectorContract;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RmsIntegrationContract implements IntegrationConnectorContract {

    @Override
    public String connectorKey() {
        return "rms-poller";
    }

    @Override
    public String connectorLabel() {
        return "RMS Poller";
    }

    @Override
    public String sourceSystem() {
        return "RMS";
    }

    @Override
    public String targetSystem() {
        return "RECON";
    }

    @Override
    public String flowKey() {
        return "rms-transaction-poll";
    }

    @Override
    public Set<String> supportedMessageTypes() {
        return Set.of(messageType());
    }

    public String flowLabel() {
        return "RMS inventory transaction feed";
    }

    public String connectorType() {
        return "DATABASE_POLLING";
    }

    public String runtimeMode() {
        return "POLLING";
    }

    public String moduleKey() {
        return "rms-poller";
    }

    public String businessObject() {
        return "INVENTORY_TRANSACTION";
    }

    public String mappingName() {
        return "rms-transaction-v1";
    }

    public String messageType() {
        return "TARGET_STATUS_POLL";
    }

    public String sourceSchemaKey() {
        return "rms-transaction-event";
    }

    public String targetSchemaKey() {
        return "integration-envelope-only";
    }

    public String mappingNotes() {
        return "RMS database inventory transaction feed publishes to the RMS inventory raw topic while journaling integration status messages in parallel";
    }
}
