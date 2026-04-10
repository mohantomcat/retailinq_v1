import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/admin'

export const adminApi = {
    // Users
    getUsers: async () => {
        const res = await apiFetch(`${BASE}/users`)
        const data = await parseResponse(res)

        return (data || []).map((user) => ({
            ...user,
            lastLogin:
                user.lastLogin ??
                user.lastLoginAt ??
                user.lastLoginTs ??
                user.lastLoginTime ??
                user.lastLoginDateTime ??
                null,
        }))
    },

    createUser: async (data) => {
        const res = await apiFetch(`${BASE}/users`, {
            method: 'POST',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    updateUser: async (id, data) => {
        const res = await apiFetch(`${BASE}/users/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    deactivateUser: async (id) => {
        const res = await apiFetch(`${BASE}/users/${id}`, {
            method: 'DELETE',
        })
        return await parseResponse(res)
    },

    activateUser: async (id) => {
        const res = await apiFetch(`${BASE}/users/${id}/activate`, {
            method: 'POST',
        })
        return await parseResponse(res)
    },

    deleteUser: async (id) => {
        const res = await apiFetch(`${BASE}/users/${id}/permanent`, {
            method: 'DELETE',
        })
        return await parseResponse(res)
    },

    resetPassword: async (id, newPassword) => {
        const res = await apiFetch(
            `${BASE}/users/${id}/reset-password`,
            {
                method: 'POST',
                body: JSON.stringify({newPassword}),
            }
        )
        return await parseResponse(res)
    },

    assignRoles: async (userId, roleIds) => {
        const res = await apiFetch(`${BASE}/users/${userId}/roles`, {
            method: 'POST',
            body: JSON.stringify({roleIds}),
        })
        return await parseResponse(res)
    },

    assignStores: async (userId, storeIds) => {
        const res = await apiFetch(`${BASE}/users/${userId}/stores`, {
            method: 'POST',
            body: JSON.stringify({
                storeIds: Array.from(storeIds),
            }),
        })
        return await parseResponse(res)
    },

    reviewUserAccess: async (userId, data) => {
        const res = await apiFetch(`${BASE}/users/${userId}/access-review`, {
            method: 'POST',
            body: JSON.stringify(data || {}),
        })
        return await parseResponse(res)
    },

    // Roles
    getRoles: async () => {
        const res = await apiFetch(`${BASE}/roles`)
        const data = await parseResponse(res)
        return data || []
    },

    createRole: async (data) => {
        const res = await apiFetch(`${BASE}/roles`, {
            method: 'POST',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    updateRole: async (roleId, data) => {
        const res = await apiFetch(`${BASE}/roles/${roleId}`, {
            method: 'PUT',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    assignPermissions: async (roleId, permissionIds) => {
        const res = await apiFetch(
            `${BASE}/roles/${roleId}/permissions`,
            {
                method: 'POST',
                body: JSON.stringify(permissionIds),
            }
        )
        return await parseResponse(res)
    },

    deleteRole: async (roleId) => {
        const res = await apiFetch(`${BASE}/roles/${roleId}`, {
            method: 'DELETE',
        })
        return await parseResponse(res)
    },

    // Permissions
    getPermissions: async () => {
        const res = await apiFetch(`${BASE}/permissions`)
        const data = await parseResponse(res)
        return data || []
    },

    getOrganizationUnits: async () => {
        const res = await apiFetch(`${BASE}/org-units`)
        return await parseResponse(res)
    },

    saveOrganizationUnit: async (id, data) => {
        const res = await apiFetch(
            id ? `${BASE}/org-units/${id}` : `${BASE}/org-units`,
            {
                method: id ? 'PUT' : 'POST',
                body: JSON.stringify(data),
            }
        )
        return await parseResponse(res)
    },

    assignOrganizationScopes: async (userId, organizationUnitIds, includeDescendants = true) => {
        const res = await apiFetch(`${BASE}/users/${userId}/org-scopes`, {
            method: 'POST',
            body: JSON.stringify({
                organizationUnitIds,
                includeDescendants,
            }),
        })
        return await parseResponse(res)
    },

    getTenantAccessCenter: async () => {
        const res = await apiFetch(`${BASE}/tenant-access-center`)
        return await parseResponse(res)
    },

    getTenantBranding: async () => {
        const res = await apiFetch(`${BASE}/tenant-branding`)
        return await parseResponse(res)
    },

    saveTenantBranding: async (data) => {
        const res = await apiFetch(`${BASE}/tenant-branding`, {
            method: 'PUT',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    saveTenantAuthConfig: async (data) => {
        const res = await apiFetch(`${BASE}/tenant-auth-config`, {
            method: 'PUT',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    saveTenantReconGroupSelections: async (data) => {
        const res = await apiFetch(`${BASE}/recon-group-selections`, {
            method: 'PUT',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    saveOidcGroupRoleMappings: async (data) => {
        const res = await apiFetch(`${BASE}/oidc-group-role-mappings`, {
            method: 'PUT',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    saveTenantSystemEndpointProfiles: async (data) => {
        const res = await apiFetch(`${BASE}/system-endpoint-profiles`, {
            method: 'PUT',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    createTenantApiKey: async (data) => {
        const res = await apiFetch(`${BASE}/api-keys`, {
            method: 'POST',
            body: JSON.stringify(data),
        })
        return await parseResponse(res)
    },

    deactivateTenantApiKey: async (id) => {
        const res = await apiFetch(`${BASE}/api-keys/${id}/deactivate`, {
            method: 'POST',
        })
        return await parseResponse(res)
    },
}
