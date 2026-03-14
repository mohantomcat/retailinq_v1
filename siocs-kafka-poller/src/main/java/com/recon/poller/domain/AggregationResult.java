package com.recon.poller.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregationResult {

    private List<SiocsTransactionRow> transactions;
    private String excludedExternalId;
    private boolean pageWasFull;
    private boolean singleTransactionPage;

    public static AggregationResult empty() {
        return AggregationResult.builder()
                .transactions(Collections.emptyList())
                .pageWasFull(false)
                .singleTransactionPage(false)
                .build();
    }

    public static AggregationResult singleTransactionPage(
            String externalId) {
        return AggregationResult.builder()
                .transactions(Collections.emptyList())
                .excludedExternalId(externalId)
                .pageWasFull(true)
                .singleTransactionPage(true)
                .build();
    }
}