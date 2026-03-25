package com.recon.publisher.service;

import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.integration.model.CanonicalTransaction;
import com.recon.integration.model.CanonicalTransactionLine;
import com.recon.publisher.config.PublisherConfig;
import com.recon.publisher.domain.LineItem;
import com.recon.publisher.domain.PosTransactionEvent;
import com.recon.publisher.domain.PoslogRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CanonicalEnvelopeMapper {

    private final PublisherConfig publisherConfig;

    public CanonicalEnvelopeMapper(PublisherConfig publisherConfig) {
        this.publisherConfig = publisherConfig;
    }

    public CanonicalIntegrationEnvelope map(XstoreIntegrationContract contract,
                                            PoslogRecord record,
                                            PosTransactionEvent event,
                                            boolean compressed) {
        CanonicalTransaction payload = CanonicalTransaction.builder()
                .transactionId(event.getTransactionKey())
                .transactionType("POS_TRANSACTION")
                .transactionSubtype(event.getTransactionType())
                .documentId(event.getExternalId())
                .businessDate(event.getBusinessDate())
                .sourceSystem(contract.sourceSystem())
                .targetSystem(contract.targetSystem())
                .sourceTimestamp(valueOrNull(
                        event.getEndDatetime(),
                        event.getBeginDatetime(),
                        formatTimestamp(record.getUpdateDate()),
                        formatTimestamp(record.getCreateDate())))
                .targetTimestamp(event.getPublishedAt())
                .locationTo(event.getStoreId())
                .locationToType("STORE")
                .storeId(event.getStoreId())
                .totalQuantity(sumQuantities(event.getLineItems()))
                .totalAmount(event.getTotalAmount())
                .lineCount(event.getLineItems() == null ? 0 : event.getLineItems().size())
                .lineDetails(mapLines(event))
                .referenceAttributes(Map.of(
                        "connectorKey", contract.connectorKey(),
                        "organizationId", String.valueOf(event.getOrganizationId()),
                        "wkstnId", String.valueOf(event.getWkstnId()),
                        "transSeq", String.valueOf(event.getTransSeq()),
                        "checksum", safe(event.getChecksum()),
                        "compressed", String.valueOf(compressed),
                        "clockDriftDetected", String.valueOf(event.isClockDriftDetected())
                ))
                .build();

        return CanonicalIntegrationEnvelope.builder()
                .messageId(UUID.randomUUID().toString())
                .flowId(contract.flowKey())
                .connectorId(contract.connectorKey())
                .tenantId(publisherConfig.getTenantId())
                .sourceSystem(contract.sourceSystem())
                .targetSystem(contract.targetSystem())
                .messageType(contract.messageType())
                .businessKey(event.getTransactionKey())
                .documentId(event.getExternalId())
                .traceId(event.getEventId())
                .eventTime(valueOrNull(
                        event.getEndDatetime(),
                        event.getBeginDatetime(),
                        formatTimestamp(record.getUpdateDate()),
                        event.getPublishedAt()))
                .ingestionTime(event.getPublishedAt())
                .payloadRef("xstore-tracker:" + event.getTransactionKey())
                .retryCount(0)
                .status("PUBLISHED")
                .schemaKey("canonical-transaction")
                .payloadVersion("1.0")
                .attributes(Map.of(
                        "legacyEventType", safe(event.getEventType()),
                        "transactionType", safe(event.getTransactionType()),
                        "storeId", safe(event.getStoreId())
                ))
                .payload(payload)
                .build();
    }

    private List<CanonicalTransactionLine> mapLines(PosTransactionEvent event) {
        if (event.getLineItems() == null || event.getLineItems().isEmpty()) {
            return List.of();
        }
        return event.getLineItems().stream()
                .map(line -> CanonicalTransactionLine.builder()
                        .lineId(event.getTransactionKey() + "|" + line.getLineSeq())
                        .itemId(line.getItemId())
                        .unitOfMeasure(line.getUnitOfMeasure())
                        .quantity(line.getQuantity())
                        .amount(line.getExtendedAmount())
                        .lineType(line.getLineType())
                        .attributes(Map.of(
                                "unitPrice", line.getUnitPrice() == null ? "" : line.getUnitPrice().toPlainString(),
                                "inventoryModifierCount", String.valueOf(line.getInventoryModifiers() == null ? 0 : line.getInventoryModifiers().size())
                        ))
                        .build())
                .toList();
    }

    private BigDecimal sumQuantities(List<LineItem> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        return lines.stream()
                .map(LineItem::getQuantity)
                .filter(quantity -> quantity != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatTimestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
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
