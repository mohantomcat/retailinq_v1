package com.recon.api.domain;

import lombok.Data;

import java.util.List;

@Data
public class SaveTenantSystemEndpointProfilesRequest {
    private List<SystemEndpointSelectionRequest> selections;
}
