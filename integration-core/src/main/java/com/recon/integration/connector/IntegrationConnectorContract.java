package com.recon.integration.connector;

import java.util.Set;

public interface IntegrationConnectorContract {

    String connectorKey();

    String connectorLabel();

    String sourceSystem();

    String targetSystem();

    String flowKey();

    Set<String> supportedMessageTypes();

    String flowLabel();

    String connectorType();

    String runtimeMode();

    String moduleKey();

    String businessObject();

    String mappingName();

    String sourceSchemaKey();

    String targetSchemaKey();

    String mappingNotes();

    default String messageType() {
        return supportedMessageTypes().stream().findFirst().orElse("CANONICAL_TRANSACTION");
    }

    default String mappingRulesJson() {
        return """
                {"mode":"SYSTEM_MANAGED","source":"%s","target":"%s","messageType":"%s","notes":"%s"}
                """.formatted(
                escapeJson(sourceSystem()),
                escapeJson(targetSchemaKey()),
                escapeJson(messageType()),
                escapeJson(mappingNotes())
        );
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
