package com.recon.api.domain;

import lombok.Data;

import java.util.List;

@Data
public class SaveOidcGroupRoleMappingsRequest {
    private List<OidcGroupRoleMappingAssignmentRequest> mappings;
}
