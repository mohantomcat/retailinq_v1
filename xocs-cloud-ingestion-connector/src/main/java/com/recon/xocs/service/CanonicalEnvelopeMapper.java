package com.recon.xocs.service;

import com.recon.integration.model.CanonicalIntegrationEnvelope;
import com.recon.integration.model.CanonicalTransaction;
import com.recon.integration.model.CanonicalTransactionLine;
import com.recon.publisher.domain.PosTransactionEvent;
import com.recon.xocs.domain.XocsStagedLine;
import com.recon.xocs.domain.XocsStagedTransaction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CanonicalEnvelopeMapper {

    public CanonicalIntegrationEnvelope map(XocsIntegrationContract contract,
                                            XocsStagedTransaction transaction,
                                            PosTransactionEvent event) {
        CanonicalTransaction payload = CanonicalTransaction.builder()
                .transactionId(event.getTransactionKey())
                .transactionType("POS_TRANSACTION")
                .transactionSubtype(event.getTransactionType())
                .documentId(event.getExternalId())
                .businessDate(event.getBusinessDate())
                .sourceSystem(contract.sourceSystem())
                .targetSystem(contract.targetSystem())
                .sourceTimestamp(valueOrNull(formatTimestamp(transaction.getSourceUpdateDate()), event.getEndDatetime(), event.getBeginDatetime()))
                .targetTimestamp(event.getPublishedAt())
                .transactionStatus(transaction.getTransStatcode())
                .locationTo(String.valueOf(transaction.getRtlLocId()))
                .locationToType("STORE")
                .storeId(String.valueOf(transaction.getRtlLocId()))
                .totalQuantity(transaction.getTotalItemQty())
                .totalAmount(transaction.getTransTotal())
                .lineCount(transaction.getLineCount() != null ? transaction.getLineCount() : transaction.getLineItems().size())
                .lineDetails(mapLines(transaction.getLineItems()))
                .referenceAttributes(Map.of(
                        "connectorKey", contract.connectorKey(),
                        "organizationId", String.valueOf(transaction.getOrganizationId()),
                        "wkstnId", String.valueOf(transaction.getWkstnId()),
                        "transSeq", String.valueOf(transaction.getTransSeq()),
                        "checksum", safe(event.getChecksum())
                ))
                .build();

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
                .eventTime(valueOrNull(formatTimestamp(transaction.getSourceUpdateDate()), event.getEndDatetime(), event.getPublishedAt()))
                .ingestionTime(event.getPublishedAt())
                .payloadRef(transaction.getRawPayloadId() == null ? null : "xocs-raw:" + transaction.getRawPayloadId())
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

    private List<CanonicalTransactionLine> mapLines(List<XocsStagedLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .map(line -> CanonicalTransactionLine.builder()
                        .lineId(line.getTransactionLineKey())
                        .itemId(line.getItemId())
                        .unitOfMeasure(line.getUnitOfMeasure())
                        .quantity(line.getNormalizedQuantity())
                        .amount(line.getRawExtendedAmt())
                        .cost(line.getRawNetAmt())
                        .lineStatus(line.getRtransLineitmStatcode())
                        .lineType(line.getLineBusinessType())
                        .attributes(Map.of(
                                "scannedItemId", safe(line.getScannedItemId()),
                                "inventoryActionCode", safe(line.getInventoryActionCode()),
                                "serialNbr", safe(line.getSerialNbr()),
                                "returnFlag", line.getReturnFlag() == null ? "" : String.valueOf(line.getReturnFlag()),
                                "voidFlag", line.getVoidFlag() == null ? "" : String.valueOf(line.getVoidFlag())
                        ))
                        .build())
                .toList();
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
