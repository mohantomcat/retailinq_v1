package com.recon.publisher.parser;

import com.recon.publisher.config.PublisherConfig;
import com.recon.publisher.domain.LineItem;
import com.recon.publisher.domain.ParseResult;
import com.recon.publisher.domain.PosTransactionEvent;
import com.recon.publisher.util.ChecksumUtil;
import com.recon.publisher.util.ExternalIdBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PoslogStaxParser {

    private static final String DTV_NS =
            "http://www.datavantagecorp.com/xstore/";
    private static final String RECONCILABLE_TRANSACTION_TYPE =
            "RETAIL_SALE";
    private static final Set<String> SKIP = Set.of(
            "NoSale", "Training", "Suspended", "PaidIn", "PaidOut",
            "OpenDrawer", "CloseDrawer", "TillAudit", "TillCount",
            "SESSION_CONTROL", "TENDER_CONTROL", "POST_VOID",
            "NO_SALE", "TRAINING", "SUSPENDED", "TILL_AUDIT",
            "OPEN_DRAWER", "CLOSE_DRAWER", "PAID_IN", "PAID_OUT",
            "WORKSTATION_OPEN", "WORKSTATION_CLOSE");
    private static final Set<String> SKIP_LINE_TYPES = Set.of(
            "Tender", "Tax", "PreviousCustomerOrder");

    private final PublisherConfig config;
    private final PoslogStreamFactory streamFactory;
    private final ExternalIdBuilder externalIdBuilder;
    private final TimestampNormalizer normalizer;

    public ParseResult parse(long orgId, long storeId,
                             LocalDate businessDate, long wkstnId,
                             long transSeq, byte[] poslogBytes) {
        boolean compressed = streamFactory.isGzipped(poslogBytes);
        try {
            XMLStreamReader reader = streamFactory.createReader(poslogBytes);

            String txnType = null;
            String beginDt = null;
            String endDt = null;
            String operatorId = null;
            String totalAmt = null;
            List<LineItem> items = new ArrayList<>();
            LineItem current = null;
            boolean inLineItem = false;
            boolean inSaleOrReturn = false;
            boolean currentLineVoided = false;
            boolean currentLineStock = false;
            String elem = null;
            int lineSeq = 0;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    elem = reader.getLocalName();

                    if ("Transaction".equals(elem)) {
                        txnType = reader.getAttributeValue(
                                DTV_NS, "TransactionType");
                        if (txnType == null) {
                            txnType = reader.getAttributeValue(
                                    null, "TransactionType");
                        }
                        log.debug("Transaction dtv:TransactionType={} store={} seq={}",
                                txnType, storeId, transSeq);
                    }

                    if ("RetailTransaction".equals(elem)) {
                        String txnStatus =
                                reader.getAttributeValue(null, "TransactionStatus");
                        if ("dtv:CancelOrphaned".equals(txnStatus)) {
                            log.debug(
                                    "RetailTransaction CancelOrphaned - skipping store={} seq={}",
                                    storeId, transSeq);
                            reader.close();
                            return ParseResult.skipped("CANCEL_ORPHANED");
                        }
                    }

                    if ("LineItem".equals(elem)) {
                        inLineItem = true;
                        inSaleOrReturn = false;
                        currentLineVoided = Boolean.parseBoolean(
                                Objects.toString(
                                        reader.getAttributeValue(null, "VoidFlag"),
                                        "false"));
                        currentLineStock = false;
                        current = new LineItem();
                        current.setLineSeq(++lineSeq);
                    }

                    if (inLineItem && current != null) {
                        if ("Sale".equals(elem)) {
                            currentLineStock = isStockItem(reader);
                            current.setLineType(currentLineVoided
                                    ? "VoidSale"
                                    : "Sale");
                            inSaleOrReturn = currentLineStock;
                            log.debug("LineItem type={} store={} seq={}",
                                    current.getLineType(), storeId, transSeq);
                        } else if ("Return".equals(elem)) {
                            currentLineStock = isStockItem(reader);
                            current.setLineType(currentLineVoided
                                    ? "VoidReturn"
                                    : "Return");
                            inSaleOrReturn = currentLineStock;
                            log.debug("LineItem type={} store={} seq={}",
                                    current.getLineType(), storeId, transSeq);
                        } else if (SKIP_LINE_TYPES.contains(elem)) {
                            current.setLineType(elem);
                            inSaleOrReturn = false;
                        }
                    }

                } else if (event == XMLStreamConstants.CHARACTERS) {
                    String text = reader.getText().trim();
                    if (!text.isEmpty()) {
                        if (inLineItem && inSaleOrReturn && current != null) {
                            applyLineItemField(current, elem, text);
                        } else if (!inLineItem) {
                            switch (Objects.toString(elem, "")) {
                                case "BeginDateTime" -> beginDt = text;
                                case "EndDateTime" -> endDt = text;
                                case "OperatorID" -> operatorId = text;
                                case "Total" -> totalAmt = text;
                                default -> {
                                }
                            }
                        }
                    }

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String endElem = reader.getLocalName();

                    if (("Sale".equals(endElem) || "Return".equals(endElem))
                            && inLineItem) {
                        inSaleOrReturn = false;
                    }

                    if ("LineItem".equals(endElem) && inLineItem
                            && current != null) {
                        if (isReconcilableLineType(current.getLineType())
                                && currentLineStock) {
                            items.add(current);
                            log.debug("LineItem captured type={} itemId={} qty={} seq={}",
                                    current.getLineType(),
                                    current.getItemId(),
                                    current.getQuantity(),
                                    transSeq);
                        } else {
                            log.debug("LineItem skipped type={} seq={}",
                                    current.getLineType(), transSeq);
                        }
                        inLineItem = false;
                        inSaleOrReturn = false;
                        currentLineVoided = false;
                        currentLineStock = false;
                        current = null;
                    }
                }
            }
            reader.close();

            if (txnType == null || SKIP.contains(txnType)) {
                log.debug("Skipping txnType={} store={} seq={}",
                        txnType, storeId, transSeq);
                return ParseResult.skipped(txnType);
            }
            if (!RECONCILABLE_TRANSACTION_TYPE.equals(txnType)) {
                log.warn("Unknown txnType={} store={} seq={} - skipping",
                        txnType, storeId, transSeq);
                return ParseResult.skipped(txnType);
            }

            TimestampNormalizer.NormalizedTimestamp ts =
                    normalizer.normalizeWithTimezone(
                            beginDt, storeId, config.getTenantTimezone());

            String externalId = externalIdBuilder.build(
                    storeId, wkstnId, transSeq, businessDate);
            String txnKey = externalIdBuilder
                    .buildTransactionKey(orgId, externalId);
            String checksum = ChecksumUtil.compute(items);

            PosTransactionEvent event2 = PosTransactionEvent.builder()
                    .eventType("POS_TRANSACTION")
                    .eventId(UUID.randomUUID().toString())
                    .schemaVersion(1)
                    .source("XSTORE")
                    .publishedAt(Instant.now().toString())
                    .organizationId(orgId)
                    .storeId(String.valueOf(storeId))
                    .businessDate(businessDate.toString())
                    .wkstnId(wkstnId)
                    .transSeq(transSeq)
                    .externalId(externalId)
                    .transactionKey(txnKey)
                    .transactionType(txnType)
                    .beginDatetime(ts.getTimestamp().toString())
                    .endDatetime(endDt)
                    .operatorId(operatorId)
                    .totalAmount(totalAmt != null
                            ? new BigDecimal(totalAmt)
                            : null)
                    .lineItems(items)
                    .checksum(checksum)
                    .compressed(compressed)
                    .clockDriftDetected(ts.isWasDrifted())
                    .build();

            return ParseResult.success(event2, compressed);

        } catch (Exception e) {
            log.error("Parse failed store={} seq={} compressed={}: {}",
                    storeId, transSeq, compressed, e.getMessage());
            return ParseResult.failed(e.getMessage());
        }
    }

    private void applyLineItemField(LineItem item,
                                    String elem, String text) {
        switch (Objects.toString(elem, "")) {
            case "ItemID" -> item.setItemId(text);
            case "Quantity" -> {
                try {
                    item.setQuantity(new BigDecimal(text));
                } catch (NumberFormatException e) {
                    log.warn("Invalid quantity value={}", text);
                }
            }
            case "UnitOfMeasureCode" -> item.setUnitOfMeasure(text);
            case "RegularSalesUnitPrice" -> {
                try {
                    item.setUnitPrice(new BigDecimal(text));
                } catch (NumberFormatException e) {
                    log.warn("Invalid price value={}", text);
                }
            }
            case "ExtendedAmount" -> {
                try {
                    item.setExtendedAmount(new BigDecimal(text));
                } catch (NumberFormatException e) {
                    log.warn("Invalid amount value={}", text);
                }
            }
            default -> {
            }
        }
    }

    private boolean isStockItem(XMLStreamReader reader) {
        return "Stock".equalsIgnoreCase(
                reader.getAttributeValue(null, "ItemType"));
    }

    private boolean isReconcilableLineType(String lineType) {
        return "Sale".equals(lineType)
                || "Return".equals(lineType)
                || "VoidSale".equals(lineType)
                || "VoidReturn".equals(lineType);
    }
}
