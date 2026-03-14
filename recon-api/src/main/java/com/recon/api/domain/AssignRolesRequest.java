package com.recon.api.domain;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class AssignRolesRequest {
    private Set<UUID> roleIds;
}