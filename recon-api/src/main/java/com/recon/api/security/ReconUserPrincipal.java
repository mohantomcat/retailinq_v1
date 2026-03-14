package com.recon.api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public class ReconUserPrincipal {
    private final String userId;
    private final String tenantId;
    private final Set<String> permissions;
    private final Set<String> storeIds;

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasAnyStoreAccess() {
        return storeIds == null || storeIds.isEmpty();
    }

    public boolean canAccessStore(String storeId) {
        // empty storeIds = access to all stores (admin)
        if (storeIds == null || storeIds.isEmpty()) return true;
        return storeIds.contains(storeId);
    }
}