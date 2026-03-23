import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1'

export const brandingApi = {
    getCurrent: async (tenantId) => {
        const params = new URLSearchParams()
        if (tenantId) {
            params.set('tenantId', tenantId)
        }

        const response = await fetch(
            `${BASE}/tenant-branding/current${
                params.toString() ? `?${params.toString()}` : ''
            }`
        )
        const json = await response.json()
        if (!response.ok) {
            throw new Error(json?.message || 'Failed to load branding')
        }
        return json?.data || null
    },

    getAdminBranding: async () => {
        const response = await apiFetch(`${BASE}/admin/tenant-branding`)
        return await parseResponse(response)
    },

    saveTenantBranding: async (data) => {
        const response = await apiFetch(`${BASE}/admin/tenant-branding`, {
            method: 'PUT',
            body: JSON.stringify(data),
        })
        return await parseResponse(response)
    },
}
