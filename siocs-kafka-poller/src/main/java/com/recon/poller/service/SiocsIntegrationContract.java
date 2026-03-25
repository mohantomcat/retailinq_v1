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
        return "SIOCS";
    }

    @Override
    public String targetSystem() {
        return "RECON";
    }

    @Override
    public String flowKey() {
        return "sim-target-status-poll";
    }

    @Override
    public Set<String> supportedMessageTypes() {
        return Set.of(messageType());
    }

    public String flowLabel() {
        return "SIM target status poll";
    }

    public String connectorType() {
        return "API_POLLING";
    }

    public String runtimeMode() {
        return "POLLING";
    }

    public String moduleKey() {
        return "sim-poller";
    }

    public String businessObject() {
        return "POS_TRANSACTION_STATUS";
    }

    public String mappingName() {
        return "sim-status-v1";
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
        return "Target status poll events are journaled without canonical transaction payload transformation";
    }
}
