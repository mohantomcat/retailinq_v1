package com.recon.api.domain;

import lombok.Data;

import java.util.UUID;

@Data
public class SaveOrganizationUnitRequest {
    private String unitKey;
    private String unitName;
    private String unitType;
    private UUID parentUnitId;
    private String storeId;
    private Integer sortOrder;
    private Boolean active;
}
