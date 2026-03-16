package com.recon.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationActionResponseDto {
    private String moduleId;
    private String actionKey;
    private String status;
    private String message;
    private Map<String, Object> rawResponse;
}
