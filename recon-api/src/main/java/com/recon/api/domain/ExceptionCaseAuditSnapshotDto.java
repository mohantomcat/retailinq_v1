package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseAuditSnapshotDto {
    private UUID id;
    private String title;
    private String summary;
    private String actor;
    private String createdAt;
    private List<String> changedFields;
    private String beforeSnapshot;
    private String afterSnapshot;
}
