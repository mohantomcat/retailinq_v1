package com.recon.publisher.service;

import com.recon.integration.connector.IntegrationConnectorContract;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class XstoreIntegrationContract implements IntegrationConnectorContract {

    @Override
    public String connectorKey() {
        return "xstore-publisher";
    }

    @Override
    public String connectorLabel() {
        return "Xstore Publisher";
    }

    @Override
    public String sourceSystem() {
        return "XSTORE";
    }

    @Override
    public String targetSystem() {
        return "RECON";
    }

    @Override
    public String flowKey() {
        return "xstore-pos-transaction";
    }

    @Override
    public Set<String> supportedMessageTypes() {
        return Set.of(messageType());
    }

    public String flowLabel() {
        return "Xstore POS transaction feed";
    }

    public String connectorType() {
        return "DATABASE_PUBLISHER";
    }

    public String runtimeMode() {
        return "BATCH";
    }

    public String moduleKey() {
        return "xstore-publisher";
    }

    public String businessObject() {
        return "POS_TRANSACTION";
    }

    public String mappingName() {
        return "xstore-canonical-v1";
    }

    public String messageType() {
        return "POS_TRANSACTION";
    }

    public String sourceSchemaKey() {
        return "xstore-pos-transaction";
    }

    public String targetSchemaKey() {
        return "canonical-transaction";
    }

    public String mappingNotes() {
        return "Pilot canonical mapping for Xstore publisher";
    }
}
