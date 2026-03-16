import {apiFetch} from './apiFetch'

const BASE = '/api/v1/alerts'

export const alertApi = {
    getAlerts: async (reconView = null) => {
        const params = new URLSearchParams()
        if (reconView) {
            params.append('reconView', reconView)
        }
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}${suffix}`)
        const json = await res.json()
        return json.data
    },

    createRule: async (payload) => {
        const res = await apiFetch(`${BASE}/rules`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        const json = await res.json()
        return json.data
    },

    updateRule: async (ruleId, payload) => {
        const res = await apiFetch(`${BASE}/rules/${ruleId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        const json = await res.json()
        return json.data
    },

    deleteRule: async (ruleId) => {
        const res = await apiFetch(`${BASE}/rules/${ruleId}`, {
            method: 'DELETE',
        })
        const json = await res.json()
        return json.data
    },

    updateEventStatus: async (eventId, status) => {
        const res = await apiFetch(`${BASE}/events/${eventId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({status}),
        })
        const json = await res.json()
        return json.data
    },
}
