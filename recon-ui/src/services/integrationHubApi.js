import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/integration-hub'

export const integrationHubApi = {
    getHub: async () => {
        const res = await apiFetch(BASE)
        return parseResponse(res)
    },

    getMessages: async (params = {}) => {
        const query = new URLSearchParams()
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                query.set(key, String(value))
            }
        })
        const suffix = query.toString() ? `?${query.toString()}` : ''
        const res = await apiFetch(`${BASE}/messages${suffix}`)
        return parseResponse(res)
    },

    getMessageDetail: async (id) => {
        const res = await apiFetch(`${BASE}/messages/${id}`)
        return parseResponse(res)
    },

    createReplayRequest: async (payload) => {
        const res = await apiFetch(`${BASE}/replay-requests`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    executeReplayRequest: async (id) => {
        const res = await apiFetch(`${BASE}/replay-requests/${id}/execute`, {
            method: 'POST',
        })
        return parseResponse(res)
    },

    resolveError: async (id, payload = {}) => {
        const res = await apiFetch(`${BASE}/errors/${id}/resolve`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },
}
