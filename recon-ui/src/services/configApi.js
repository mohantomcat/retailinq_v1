import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/configurations'

export const configApi = {
    getCatalog: async () => {
        const res = await apiFetch(`${BASE}/catalog`)
        return parseResponse(res)
    },
    saveOverride: async (configKey, value) => {
        const res = await apiFetch(`${BASE}/overrides/${configKey}`, {
            method: 'PUT',
            body: JSON.stringify({value}),
        })
        return parseResponse(res)
    },
    clearOverride: async (configKey) => {
        const res = await apiFetch(`${BASE}/overrides/${configKey}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },
}
