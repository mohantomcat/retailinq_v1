import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/admin'
const TENANT = 'tenant-india'

export const adminApi = {
    // Users
    getUsers: async () => {
        const res = await apiFetch(
            `${BASE}/users?tenantId=${TENANT}`
        )
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

    // Roles
    getRoles: async () => {
        const res = await apiFetch(
            `${BASE}/roles?tenantId=${TENANT}`
        )
        const data = await parseResponse(res)
        return data || []
    },

    createRole: async (data) => {
        const res = await apiFetch(`${BASE}/roles`, {
            method: 'POST',
            body: JSON.stringify({
                ...data,
                tenantId: TENANT,
            }),
        })
        return await parseResponse(res)
    },

    updateRole: async (roleId, data) => {
        const res = await apiFetch(`${BASE}/roles/${roleId}`, {
            method: 'PUT',
            body: JSON.stringify({
                ...data,
                tenantId: TENANT,
            }),
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
}