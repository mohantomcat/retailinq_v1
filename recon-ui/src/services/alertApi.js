import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/alerts'

export const alertApi = {
    getAlerts: async (reconView = null) => {
        const params = new URLSearchParams()
        if (reconView) {
            params.append('reconView', reconView)
        }
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}${suffix}`)
        return parseResponse(res)
    },

    createRule: async (payload) => {
        const res = await apiFetch(`${BASE}/rules`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateRule: async (ruleId, payload) => {
        const res = await apiFetch(`${BASE}/rules/${ruleId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteRule: async (ruleId) => {
        const res = await apiFetch(`${BASE}/rules/${ruleId}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    updateEventStatus: async (eventId, status) => {
        const res = await apiFetch(`${BASE}/events/${eventId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({status}),
        })
        return parseResponse(res)
    },

    createSubscription: async (payload) => {
        const res = await apiFetch(`${BASE}/subscriptions`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateSubscription: async (subscriptionId, payload) => {
        const res = await apiFetch(`${BASE}/subscriptions/${subscriptionId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteSubscription: async (subscriptionId) => {
        const res = await apiFetch(`${BASE}/subscriptions/${subscriptionId}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    createWebhookSubscription: async (payload) => {
        const res = await apiFetch(`${BASE}/webhook-subscriptions`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateWebhookSubscription: async (subscriptionId, payload) => {
        const res = await apiFetch(`${BASE}/webhook-subscriptions/${subscriptionId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteWebhookSubscription: async (subscriptionId) => {
        const res = await apiFetch(`${BASE}/webhook-subscriptions/${subscriptionId}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    createEscalationPolicy: async (payload) => {
        const res = await apiFetch(`${BASE}/escalation-policies`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateEscalationPolicy: async (policyId, payload) => {
        const res = await apiFetch(`${BASE}/escalation-policies/${policyId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteEscalationPolicy: async (policyId) => {
        const res = await apiFetch(`${BASE}/escalation-policies/${policyId}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    createPersonalSubscription: async (payload) => {
        const res = await apiFetch(`${BASE}/personal-subscriptions`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updatePersonalSubscription: async (subscriptionId, payload) => {
        const res = await apiFetch(`${BASE}/personal-subscriptions/${subscriptionId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deletePersonalSubscription: async (subscriptionId) => {
        const res = await apiFetch(`${BASE}/personal-subscriptions/${subscriptionId}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    createDigestSubscription: async (payload) => {
        const res = await apiFetch(`${BASE}/digests`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateDigestSubscription: async (subscriptionId, payload) => {
        const res = await apiFetch(`${BASE}/digests/${subscriptionId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteDigestSubscription: async (subscriptionId) => {
        const res = await apiFetch(`${BASE}/digests/${subscriptionId}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    runDigestsNow: async (subscriptionId = null) => {
        const suffix = subscriptionId ? `?subscriptionId=${subscriptionId}` : ''
        const res = await apiFetch(`${BASE}/digests/run-now${suffix}`, {
            method: 'POST',
        })
        return parseResponse(res)
    },

    createAnomalyRule: async (payload) => {
        const res = await apiFetch(`${BASE}/anomaly-rules`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateAnomalyRule: async (ruleId, payload) => {
        const res = await apiFetch(`${BASE}/anomaly-rules/${ruleId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteAnomalyRule: async (ruleId) => {
        const res = await apiFetch(`${BASE}/anomaly-rules/${ruleId}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    runAnomalyDetectionNow: async () => {
        const res = await apiFetch(`${BASE}/anomaly-rules/run-now`, {
            method: 'POST',
        })
        return parseResponse(res)
    },

    createSmsSubscription: async (payload) => {
        const res = await apiFetch(`${BASE}/sms-subscriptions`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateSmsSubscription: async (subscriptionId, payload) => {
        const res = await apiFetch(`${BASE}/sms-subscriptions/${subscriptionId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteSmsSubscription: async (subscriptionId) => {
        const res = await apiFetch(`${BASE}/sms-subscriptions/${subscriptionId}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },
}
