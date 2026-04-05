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
    private List<String> storeIds;
    private List<String> wkstnIds;
    private List<String> transactionTypes;
    private List<String> transactionFamilies;
    private String fromBusinessDate;
    private String toBusinessDate;
    private String reconStatus;
    private List<String> reconStatuses;
    private String transactionKey;
    private String externalId;
    private String reconView;
    private List<String> reconViews;
    private String fromDate;
    private String toDate;
    private int page = 0;
    private int size = 20;
}
