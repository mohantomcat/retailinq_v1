import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/sla'

export const slaApi = {
    getManagement: async (reconView = null) => {
        const params = new URLSearchParams()
        if (reconView) {
            params.append('reconView', reconView)
        }
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}${suffix}`)
        return parseResponse(res)
    },

    saveRule: async ({reconView, severity, targetMinutes, description}) => {
        const res = await apiFetch(
            `${BASE}/rules/${encodeURIComponent(reconView)}/${encodeURIComponent(severity)}`,
            {
                method: 'PUT',
                body: JSON.stringify({
                    targetMinutes: Number(targetMinutes),
                    description,
                }),
            }
        )
        return parseResponse(res)
    },

    saveOperatingModel: async (payload) => {
        const res = await apiFetch(`${BASE}/operating-model`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },
}
