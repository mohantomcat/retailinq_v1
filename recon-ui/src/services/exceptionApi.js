import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/exceptions'

export const exceptionApi = {
    getCase: async ({transactionKey, reconView}) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(
            `${BASE}/cases/${encoded}?reconView=${encodeURIComponent(reconView)}`
        )
        return parseResponse(res)
    },

    saveCase: async ({
        transactionKey,
        reconView,
        caseStatus,
        reasonCode,
        assigneeUsername,
        notes,
    }) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(`${BASE}/cases/${encoded}`, {
            method: 'PUT',
            body: JSON.stringify({
                reconView,
                caseStatus,
                reasonCode,
                assigneeUsername,
                notes,
            }),
        })
        return parseResponse(res)
    },

    addComment: async ({transactionKey, reconView, commentText}) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(`${BASE}/cases/${encoded}/comments`, {
            method: 'POST',
            body: JSON.stringify({
                reconView,
                commentText,
            }),
        })
        return parseResponse(res)
    },
}
