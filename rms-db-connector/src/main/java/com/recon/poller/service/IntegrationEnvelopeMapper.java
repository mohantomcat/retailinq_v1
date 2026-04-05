package com.recon.rms.service;

import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.rms.domain.RmsTransactionEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class IntegrationEnvelopeMapper {

    public CanonicalIntegrationEnvelope map(RmsIntegrationContract contract,
                                            RmsTransactionEvent event) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("legacyEventType", safe(event.getEventType()));
        attributes.put("transactionType", event.getTransactionType() == null ? "" : String.valueOf(event.getTransactionType()));
        attributes.put("processingStatus", event.getProcessingStatus() == null ? "" : String.valueOf(event.getProcessingStatus()));
        attributes.put("storeId", safe(event.getStoreId()));
        attributes.put("duplicateFlag", String.valueOf(event.isDuplicateFlag()));
        attributes.put("duplicatePostingCount", String.valueOf(event.getDuplicatePostingCount()));
        attributes.put("requestId", event.getRequestId() == null ? "" : String.valueOf(event.getRequestId()));
        attributes.put("checksum", safe(event.getChecksum()));

        return CanonicalIntegrationEnvelope.builder()
                .messageId(UUID.randomUUID().toString())
                .flowId(contract.flowKey())
                .connectorId(contract.connectorKey())
                .tenantId(event.getTenantId())
                .sourceSystem(contract.sourceSystem())
                .targetSystem(contract.targetSystem())
                .messageType(contract.messageType())
                .businessKey(event.getTransactionKey())
                .documentId(event.getExternalId())
                .traceId(event.getEventId())
                .eventTime(valueOrNull(
                        event.getUpdateDateTime(),
                        event.getTransactionDateTime(),
                        event.getPublishedAt()))
                .ingestionTime(event.getPublishedAt())
                .payloadRef("rms-event:" + event.getTransactionKey())
                .retryCount(0)
                .status("PUBLISHED")
                .schemaKey("rms-transaction-event")
                .payloadVersion("1.0")
                .attributes(attributes)
                .payload(null)
                .build();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String valueOrNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
