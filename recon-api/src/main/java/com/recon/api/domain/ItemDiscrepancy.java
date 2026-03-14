package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDiscrepancy {
    private String itemId;
    private String type;
    private BigDecimal xstoreQuantity;
    private BigDecimal siocsQuantity;
    private String xstoreUom;
    private String siocsUom;
    private String description;
}