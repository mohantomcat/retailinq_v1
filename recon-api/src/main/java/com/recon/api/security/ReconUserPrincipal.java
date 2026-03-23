package com.recon.api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public class ReconUserPrincipal {
    private final String userId;
    private final String username;
    private final String tenantId;
    private final Set<String> permissions;
    private final Set<String> storeIds;
    private final boolean allStoreAccess;
    private final String authMode;

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasAnyStoreAccess() {
        return allStoreAccess || storeIds == null || !storeIds.isEmpty();
    }

    public boolean canAccessStore(String storeId) {
        if (allStoreAccess) return true;
        return storeIds.contains(storeId);
    }

    public Set<String> getEffectiveStoreIds() {
        return storeIds;
    }
}
