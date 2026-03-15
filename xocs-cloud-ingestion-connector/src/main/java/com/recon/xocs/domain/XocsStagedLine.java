package com.recon.xocs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XocsStagedLine {
    private Long id;
    private Long transactionId;
    private long organizationId;
    private long rtlLocId;
    private java.time.LocalDate businessDate;
    private long wkstnId;
    private long transSeq;
    private String transactionKey;
    private Long rtransLineitmSeq;
    private String transactionLineKey;
    private String itemId;
    private String scannedItemId;
    private String unitOfMeasure;
    private Integer returnFlag;
    private Integer voidFlag;
    private String lineBusinessType;
    private BigDecimal rawQuantity;
    private BigDecimal normalizedQuantity;
    private BigDecimal unitPrice;
    private BigDecimal rawExtendedAmt;
    private BigDecimal rawNetAmt;
    private BigDecimal grossAmt;
    private BigDecimal vatAmt;
    private String saleLineitmTypcode;
    private String rtransLineitmTypcode;
    private String rtransLineitmStatcode;
    private String inventoryActionCode;
    private String serialNbr;
    private Timestamp sourceUpdateDate;
}
