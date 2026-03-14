package com.recon.api.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class RoleDto {
    private UUID id;
    private String name;
    private String description;
    private Set<String> permissionCodes;
}