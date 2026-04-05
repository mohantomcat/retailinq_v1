package com.recon.poller.service;

import com.recon.integration.connector.IntegrationConnectorContract;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SiocsIntegrationContract implements IntegrationConnectorContract {

    @Override
    public String connectorKey() {
        return "sim-poller";
    }

    @Override
    public String connectorLabel() {
        return "SIM Poller";
    }

    @Override
    public String sourceSystem() {
        return "SIM";
    }

    @Override
    public String targetSystem() {
        return "RECON";
    }

    @Override
    public String flowKey() {
        return "sim-transaction-poll";
    }

    @Override
    public Set<String> supportedMessageTypes() {
        return Set.of(messageType());
    }

    public String flowLabel() {
        return "SIM transaction feed";
    }

    public String connectorType() {
        return "DATABASE_POLLING";
    }

    public String runtimeMode() {
        return "POLLING";
    }

    public String moduleKey() {
        return "sim-poller";
    }

    public String businessObject() {
        return "RETAIL_TRANSACTION";
    }

    public String mappingName() {
        return "sim-transaction-v1";
    }

    public String messageType() {
        return "TARGET_STATUS_POLL";
    }

    public String sourceSchemaKey() {
        return "sim-transaction-event";
    }

    public String targetSchemaKey() {
        return "integration-envelope-only";
    }

    public String mappingNotes() {
        return "SIM database transaction feed publishes POS and inventory domains to dedicated raw topics while journaling integration status messages in parallel";
    }
}
