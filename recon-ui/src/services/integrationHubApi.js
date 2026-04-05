import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/integration-hub'

function withReconView(path, reconView, params = null) {
    const query = params instanceof URLSearchParams ? params : new URLSearchParams(params || {})
    if (reconView) {
        query.set('reconView', reconView)
    }
    const suffix = query.toString() ? `?${query.toString()}` : ''
    return `${path}${suffix}`
}

export const integrationHubApi = {
    getHub: async (reconView = null) => {
        const res = await apiFetch(withReconView(BASE, reconView))
        return parseResponse(res)
    },

    getMessages: async (params = {}, reconView = null) => {
        const query = new URLSearchParams()
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                query.set(key, String(value))
            }
        })
        const res = await apiFetch(withReconView(`${BASE}/messages`, reconView, query))
        return parseResponse(res)
    },

    getMessageDetail: async (id, reconView = null) => {
        const res = await apiFetch(withReconView(`${BASE}/messages/${id}`, reconView))
        return parseResponse(res)
    },

    createReplayRequest: async (payload, reconView = null) => {
        const res = await apiFetch(withReconView(`${BASE}/replay-requests`, reconView), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    executeReplayRequest: async (id, reconView = null) => {
        const res = await apiFetch(withReconView(`${BASE}/replay-requests/${id}/execute`, reconView), {
            method: 'POST',
        })
        return parseResponse(res)
    },

    resolveError: async (id, payload = {}, reconView = null) => {
        const res = await apiFetch(withReconView(`${BASE}/errors/${id}/resolve`, reconView), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },
}
