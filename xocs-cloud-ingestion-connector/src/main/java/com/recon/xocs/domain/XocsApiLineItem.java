package com.recon.xocs.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class XocsApiLineItem {
    @JsonAlias({"rtransLineitmSeq", "rtrans_lineitm_seq", "RTRANS_LINEITM_SEQ"})
    private Long rtransLineitmSeq;

    @JsonAlias({"itemId", "item_id", "ITEM_ID"})
    private String itemId;

    @JsonAlias({"scannedItemId", "scanned_item_id", "SCANNED_ITEM_ID"})
    private String scannedItemId;

    @JsonAlias({"unitOfMeasure", "unit_of_measure", "UNIT_OF_MEASURE", "eftlinkUnitOfMeasureCode", "EFTLINK_UNIT_OF_MEASURE_CODE"})
    private String unitOfMeasure;

    @JsonAlias({"returnFlag", "return_flag", "RETURN_FLAG"})
    private Integer returnFlag;

    @JsonAlias({"voidFlag", "void_flag", "VOID_FLAG"})
    private Integer voidFlag;

    @JsonAlias({"lineBusinessType", "line_business_type", "LINE_BUSINESS_TYPE"})
    private String lineBusinessType;

    @JsonAlias({"rawQuantity", "raw_quantity", "RAW_QUANTITY", "quantity", "QUANTITY"})
    private BigDecimal rawQuantity;

    @JsonAlias({"normalizedQuantity", "normalized_quantity", "NORMALIZED_QUANTITY"})
    private BigDecimal normalizedQuantity;

    @JsonAlias({"unitPrice", "unit_price", "UNIT_PRICE"})
    private BigDecimal unitPrice;

    @JsonAlias({"rawExtendedAmt", "raw_extended_amt", "RAW_EXTENDED_AMT", "rawExtendedAmount", "RAW_EXTENDED_AMOUNT", "extendedAmt", "EXTENDED_AMT"})
    private BigDecimal rawExtendedAmt;

    @JsonAlias({"rawNetAmt", "raw_net_amt", "RAW_NET_AMT", "netAmt", "NET_AMT"})
    private BigDecimal rawNetAmt;

    @JsonAlias({"grossAmt", "gross_amt", "GROSS_AMT"})
    private BigDecimal grossAmt;

    @JsonAlias({"vatAmt", "vat_amt", "VAT_AMT"})
    private BigDecimal vatAmt;

    @JsonAlias({"saleLineitmTypcode", "sale_lineitm_typcode", "SALE_LINEITM_TYPCODE"})
    private String saleLineitmTypcode;

    @JsonAlias({"rtransLineitmTypcode", "rtrans_lineitm_typcode", "RTRANS_LINEITM_TYPCODE"})
    private String rtransLineitmTypcode;

    @JsonAlias({"rtransLineitmStatcode", "rtrans_lineitm_statcode", "RTRANS_LINEITM_STATCODE"})
    private String rtransLineitmStatcode;

    @JsonAlias({"inventoryActionCode", "inventory_action_code", "INVENTORY_ACTION_CODE"})
    private String inventoryActionCode;

    @JsonAlias({"serialNbr", "serial_nbr", "SERIAL_NBR"})
    private String serialNbr;

    @JsonAlias({"lineCreateDate", "line_create_date", "LINE_CREATE_DATE", "createDate", "create_date", "CREATE_DATE"})
    private Instant lineCreateDate;

    @JsonAlias({"lineUpdateDate", "line_update_date", "LINE_UPDATE_DATE", "updateDate", "update_date", "UPDATE_DATE"})
    private Instant lineUpdateDate;
}
