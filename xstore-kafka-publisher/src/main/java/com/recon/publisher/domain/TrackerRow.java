package com.recon.publisher.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackerRow {
    private String status;
    private int retryCount;
    private Timestamp poslogUpdateDate;
    private String payloadHash;
}