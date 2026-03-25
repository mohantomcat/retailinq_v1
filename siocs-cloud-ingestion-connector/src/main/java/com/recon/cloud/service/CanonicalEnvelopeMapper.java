package com.recon.cloud.service;

import com.recon.cloud.domain.CloudLineItem;
import com.recon.cloud.domain.CloudStagedTransaction;
import com.recon.cloud.domain.CloudTransactionEvent;
import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.integration.model.CanonicalTransaction;
import com.recon.integration.model.CanonicalTransactionLine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CanonicalEnvelopeMapper {

    public CanonicalIntegrationEnvelope map(SiocsIntegrationContract contract,
                                            CloudStagedTransaction transaction,
                                            CloudTransactionEvent legacyEvent) {
        CanonicalTransaction payload = CanonicalTransaction.builder()
                .transactionId(legacyEvent.getTransactionKey())
                .transactionType(resolveTransactionType(transaction.getType()))
                .transactionSubtype(resolveTransactionSubtype(transaction.getType()))
                .documentId(transaction.getExternalId())
                .referenceDocumentId(transaction.getTransactionDateTime() != null
                        ? transaction.getTransactionDateTime().toInstant().toString()
                        : null)
                .businessDate(legacyEvent.getBusinessDate())
                .sourceSystem(contract.sourceSystem())
                .targetSystem(contract.targetSystem())
                .sourceTimestamp(legacyEvent.getUpdateDateTime())
                .targetTimestamp(legacyEvent.getPublishedAt())
                .transactionStatus(legacyEvent.getProcessingStatusDesc())
                .locationTo(transaction.getStoreId())
                .locationToType(transaction.getStoreId() != null ? "STORE" : null)
                .storeId(transaction.getStoreId())
                .totalQuantity(transaction.getTotalQuantity())
                .lineCount(transaction.getLineItemCount())
                .lineDetails(mapLines(transaction.getLineItems()))
                .referenceAttributes(Map.of(
                        "externalId", valueOrBlank(transaction.getExternalId()),
                        "requestId", transaction.getRequestId() == null ? "" : String.valueOf(transaction.getRequestId()),
                        "connectorKey", contract.connectorKey(),
                        "duplicateFlag", String.valueOf(transaction.isDuplicateFlag()),
                        "postingCount", String.valueOf(transaction.getPostingCount())
                ))
                .build();

        return CanonicalIntegrationEnvelope.builder()
                .messageId(UUID.randomUUID().toString())
                .flowId(contract.flowKey())
                .connectorId(contract.connectorKey())
                .tenantId(legacyEvent.getTenantId())
                .sourceSystem(contract.sourceSystem())
                .targetSystem(contract.targetSystem())
                .messageType("CANONICAL_TRANSACTION")
                .businessKey(legacyEvent.getTransactionKey())
                .documentId(transaction.getExternalId())
                .traceId(legacyEvent.getEventId())
                .eventTime(legacyEvent.getUpdateDateTime() != null ? legacyEvent.getUpdateDateTime() : legacyEvent.getPublishedAt())
                .ingestionTime(legacyEvent.getPublishedAt())
                .payloadRef(transaction.getRawPayloadId() == null ? null : "siocs-raw:" + transaction.getRawPayloadId())
                .retryCount(0)
                .status("PUBLISHED")
                .schemaKey("canonical-transaction")
                .payloadVersion("1.0")
                .attributes(Map.of(
                        "legacyEventType", valueOrBlank(legacyEvent.getEventType()),
                        "legacyTransactionType", valueOrBlank(legacyEvent.getTransactionTypeDesc()),
                        "storeId", valueOrBlank(transaction.getStoreId())
                ))
                .payload(payload)
                .build();
    }

    private List<CanonicalTransactionLine> mapLines(List<CloudLineItem> lineItems) {
        if (lineItems == null) {
            return List.of();
        }
        return lineItems.stream()
                .map(lineItem -> CanonicalTransactionLine.builder()
                        .lineId(lineItem.getId() == null ? null : String.valueOf(lineItem.getId()))
                        .itemId(lineItem.getItemId())
                        .unitOfMeasure(lineItem.getUnitOfMeasure())
                        .quantity(lineItem.getQuantity())
                        .lineStatus(lineItem.getProcessingStatus() == null ? null : String.valueOf(lineItem.getProcessingStatus()))
                        .lineType(lineItem.getType() == null ? null : String.valueOf(lineItem.getType()))
                        .attributes(Map.of(
                                "transactionExtendedId", valueOrBlank(lineItem.getTransactionExtendedId())
                        ))
                        .build())
                .toList();
    }

    private String resolveTransactionType(Integer type) {
        if (type == null) {
            return "UNKNOWN";
        }
        return switch (type) {
            case 10, 11, 12, 80 -> "TRANSFER";
            case 20, 21, 22, 70 -> "RECEIVING";
            case 30 -> "DSD";
            case 40 -> "INVENTORY_ADJUSTMENT";
            case 50 -> "PURCHASE_ORDER";
            case 60 -> "RTV";
            default -> "TRANSACTION_" + type;
        };
    }

    private String resolveTransactionSubtype(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case 11 -> "STORE_TO_STORE";
            case 12 -> "STORE_TO_WAREHOUSE";
            case 21 -> "STORE_TO_STORE";
            case 22, 70 -> "WAREHOUSE_DELIVERY";
            case 30 -> "DIRECT_STORE_DELIVERY";
            case 40 -> "ADJUSTMENT";
            case 50 -> "STANDARD";
            case 60 -> "RETURN_TO_VENDOR";
            default -> null;
        };
    }

    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
