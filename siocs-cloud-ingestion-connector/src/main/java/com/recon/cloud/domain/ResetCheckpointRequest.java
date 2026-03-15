package com.recon.cloud.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetCheckpointRequest {
    @NotBlank
    private String lastSuccessTimestamp;
    private Long lastCursorId = 0L;
}
