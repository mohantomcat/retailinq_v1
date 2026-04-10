import {apiFetch, parseResponse} from './apiFetch'

export const activityApi = {
    async getActivity(filters = {}) {
        const params = new URLSearchParams()
        Object.entries(filters).forEach(([key, value]) => {
            if (value !== undefined && value !== null && String(value).trim() !== '') {
                params.set(key, value)
            }
        })
        const query = params.toString()
        const res = await apiFetch(`/api/v1/activity${query ? `?${query}` : ''}`)
        return parseResponse(res)
    },

    async getRetentionCenter() {
        const res = await apiFetch('/api/v1/activity/retention-center')
        return parseResponse(res)
    },

    async saveRetentionPolicy(payload) {
        const res = await apiFetch('/api/v1/activity/retention-policy', {
            method: 'PUT',
            body: JSON.stringify(payload || {}),
        })
        return parseResponse(res)
    },

    async runArchive(payload) {
        const res = await apiFetch('/api/v1/activity/archive', {
            method: 'POST',
            body: JSON.stringify(payload || {}),
        })
        return parseResponse(res)
    },

    async getSoxReport(filters = {}) {
        const params = new URLSearchParams()
        Object.entries(filters).forEach(([key, value]) => {
            if (value !== undefined && value !== null && String(value).trim() !== '') {
                params.set(key, value)
            }
        })
        const query = params.toString()
        const res = await apiFetch(`/api/v1/activity/sox-report${query ? `?${query}` : ''}`)
        return parseResponse(res)
    },

    async exportActivity(filters = {}) {
        const params = new URLSearchParams()
        Object.entries(filters).forEach(([key, value]) => {
            if (value !== undefined && value !== null && String(value).trim() !== '') {
                params.set(key, value)
            }
        })
        const query = params.toString()
        const res = await apiFetch(`/api/v1/activity/export${query ? `?${query}` : ''}`, {
            headers: {
                Accept: 'text/csv,application/json',
            },
        })
        if (!res.ok) {
            throw new Error('Failed to export audit evidence')
        }
        const blob = await res.blob()
        const disposition = res.headers.get('content-disposition') || ''
        const match = disposition.match(/filename="?([^"]+)"?/)
        const filename = match?.[1] || 'audit-export.csv'
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
        return filename
    },
}
