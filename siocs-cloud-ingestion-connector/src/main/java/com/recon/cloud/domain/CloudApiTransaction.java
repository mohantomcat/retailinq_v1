package com.recon.cloud.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudApiTransaction {
    @JsonAlias({"id", "ID"})
    private Long id;
    @JsonAlias({"sourceRecordKey", "source_record_key", "SOURCE_RECORD_KEY"})
    private String sourceRecordKey;
    @JsonAlias({"externalId", "external_id", "EXTERNAL_ID"})
    private String externalId;
    @JsonAlias({"requestId", "request_id", "REQUEST_ID"})
    private Long requestId;
    @JsonAlias({"storeId", "store_id", "STORE_ID"})
    private String storeId;
    @JsonAlias({"transactionDateTime", "transaction_date_time", "TRANSACTION_DATE_TIME"})
    private Instant transactionDateTime;
    @JsonAlias({"updateDateTime", "update_date_time", "UPDATE_DATE_TIME"})
    private Instant updateDateTime;
    @JsonAlias({"type", "TYPE"})
    private Integer type;
    @JsonAlias({"processingStatus", "processing_status", "PROCESSING_STATUS"})
    private Integer processingStatus;
    @Builder.Default
    private List<CloudApiLineItem> lineItems = new ArrayList<>();
}
