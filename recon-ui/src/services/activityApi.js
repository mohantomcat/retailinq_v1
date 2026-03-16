import {apiFetch} from './apiFetch'

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
        const json = await res.json()
        if (!json?.success) {
            throw new Error(json?.message || 'Failed to load activity feed')
        }
        return json.data
    },
}
