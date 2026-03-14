package com.recon.poller.domain;

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
public class SiocsRawRow {
    private Long id;
    private String externalId;
    private Long requestId;
    private String storeId;
    private Timestamp transactionDateTime;
    private Timestamp updateDateTime;
    private Integer type;
    private Integer sourceType;
    private Integer processingStatus;
    private String itemId;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private String transactionExtendedId;
    private String uin;
    private String epc;
    private String dropShip;
    private String reason;
}