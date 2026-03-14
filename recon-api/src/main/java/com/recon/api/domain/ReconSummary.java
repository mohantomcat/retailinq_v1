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
public class ReconSummary {
    private String transactionKey;
    private String externalId;
    private String reconView;
    private String simSource;
    private String storeId;
    private String wkstnId;
    private String businessDate;        // always yyyy-MM-dd — never mutated
    private String businessDateDisplay; // locale display — dd-MMM-yyyy etc.
    private String transactionType;
    private String reconStatus;
    private Integer processingStatus;
    private String xstoreChecksum;
    private String siocsChecksum;
    private boolean checksumMatch;
    private List<ItemDiscrepancy> discrepancies;
    private boolean duplicateFlag;
    private int duplicatePostingCount;
    private String reconciledAt;
    private String updatedAt;
}
