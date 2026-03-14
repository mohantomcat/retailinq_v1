package com.recon.publisher.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoslogRecord {
    private long organizationId;
    private long rtlLocId;
    private LocalDate businessDate;
    private long wkstnId;
    private long transSeq;
    private byte[] poslogBytes;
    private Timestamp createDate;
    private Timestamp updateDate;
    private String transTypcode;
}