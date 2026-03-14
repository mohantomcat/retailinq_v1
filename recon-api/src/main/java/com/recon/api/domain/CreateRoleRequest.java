package com.recon.api.domain;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class CreateRoleRequest {
    private String name;
    private String description;
    private String tenantId;
    private Set<UUID> permissionIds;
}