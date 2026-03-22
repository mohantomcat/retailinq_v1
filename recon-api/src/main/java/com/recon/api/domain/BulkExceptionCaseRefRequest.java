package com.recon.api.domain;

import lombok.Data;

@Data
public class BulkExceptionCaseRefRequest {
    private String transactionKey;
    private String reconView;
}
