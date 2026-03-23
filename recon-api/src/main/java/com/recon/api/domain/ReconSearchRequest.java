package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconSearchRequest {
    private List<String> storeIds;          // multi-select stores
    private List<String> wkstnIds;          // multi-select registers
    private List<String> transactionTypes;  // multi-select transaction types
    private String fromBusinessDate;        // date range from
    private String toBusinessDate;          // date range to
    private String reconStatus;
    private String transactionKey;
    private String externalId;
    private String reconView;
    private String fromDate;                // reconciledAt range from
    private String toDate;                  // reconciledAt range to
    private int page = 0;
    private int size = 20;
}
