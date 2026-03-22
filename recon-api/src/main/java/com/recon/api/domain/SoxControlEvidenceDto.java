package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoxControlEvidenceDto {
    private String controlId;
    private String controlTitle;
    private String controlOwner;
    private String status;
    private long evidenceCount;
    private String lastEvidenceAt;
    private String narrative;
    private String sampleReference;
}
