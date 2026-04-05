import {apiFetch} from './apiFetch'

const BASE = '/api/v1/operations'

function withReconView(path, reconView) {
    if (!reconView) {
        return path
    }
    const params = new URLSearchParams({reconView})
    return `${path}?${params.toString()}`
}

export const operationsApi = {
    getOperations: async (reconView = null) => {
        const res = await apiFetch(withReconView(BASE, reconView))
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

    executeAction: async (moduleId, actionKey, reconView = null) => {
        const res = await apiFetch(withReconView(`${BASE}/${moduleId}/actions/${actionKey}`, reconView), {
            method: 'POST',
        })
        const json = await res.json()
        return json.data
    },

    resetCheckpoint: async (moduleId, payload, reconView = null) => {
        const res = await apiFetch(withReconView(`${BASE}/${moduleId}/actions/reset-checkpoint`, reconView), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        const json = await res.json()
        return json.data
    },

    replayWindow: async (moduleId, payload, reconView = null) => {
        const res = await apiFetch(withReconView(`${BASE}/${moduleId}/actions/replay-window`, reconView), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
        })
        const json = await res.json()
        return json.data
    },
}
