package com.recon.api.domain;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class CreateUserRequest {
    private String username;
    private String email;
    private String password;
    private String fullName;
    private String tenantId;
    private String identityProvider;
    private String externalSubject;
    private String directoryExternalId;
    private UUID managerUserId;
    private Boolean emailVerified;
    private Set<UUID> roleIds;
    private Set<String> storeIds;
}
