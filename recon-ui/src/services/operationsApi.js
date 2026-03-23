import {apiFetch} from './apiFetch'

const BASE = '/api/v1/operations'

export const operationsApi = {
    getOperations: async () => {
        const res = await apiFetch(BASE)
        const json = await res.json()
        return json.data
    },

    getJobsCenter: async () => {
        const res = await apiFetch(`${BASE}/jobs-center`)
        const json = await res.json()
        return json.data
    },

    createJob: async (payload) => {
        const res = await apiFetch(`${BASE}/jobs`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        const json = await res.json()
        return json.data
    },

    updateJob: async (jobId, payload) => {
        const res = await apiFetch(`${BASE}/jobs/${jobId}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        const json = await res.json()
        return json.data
    },

    runJobNow: async (jobId) => {
        const res = await apiFetch(`${BASE}/jobs/${jobId}/run`, {
            method: 'POST',
        })
        const json = await res.json()
        return json.data
    },

    executeAction: async (moduleId, actionKey) => {
        const res = await apiFetch(`${BASE}/${moduleId}/actions/${actionKey}`, {
            method: 'POST',
        })
        const json = await res.json()
        return json.data
    },

    resetCheckpoint: async (moduleId, payload) => {
        const res = await apiFetch(`${BASE}/${moduleId}/actions/reset-checkpoint`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        const json = await res.json()
        return json.data
    },

    replayWindow: async (moduleId, payload) => {
        const res = await apiFetch(`${BASE}/${moduleId}/actions/replay-window`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        const json = await res.json()
        return json.data
    },
}
