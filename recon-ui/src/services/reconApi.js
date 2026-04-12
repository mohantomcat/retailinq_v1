import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/recon'

const validateIsoDate = (date) => {
    if (!date) return undefined
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
        console.error(
            `Invalid date format: ${date}. Must be yyyy-MM-dd`)
        return undefined
    }
    return date
}

export const reconApi = {

    getDashboard: async ({
                             storeIds = [],
                             transactionFamilies = [],
                             reconView = null,
                             fromBusinessDate = null,
                             toBusinessDate = null,
                         } = {}) => {
        const params = new URLSearchParams()
        if (storeIds?.length)
            storeIds.forEach(s =>
                params.append('storeIds', s))
        if (transactionFamilies?.length)
            transactionFamilies.forEach(f =>
                params.append('transactionFamilies', f))
        if (reconView)
            params.append('reconView', reconView)
        if (validateIsoDate(fromBusinessDate))
            params.append('fromBusinessDate', fromBusinessDate)
        if (validateIsoDate(toBusinessDate))
            params.append('toBusinessDate', toBusinessDate)
        const res = await apiFetch(
            `${BASE}/dashboard?${params}`)
        const json = await res.json()
        return json.data
    },

    getDashboardAnalytics: async ({
                                      storeIds = [],
                                      wkstnIds = [],
                                      transactionTypes = [],
                                      transactionFamilies = [],
                                      reconView = null,
                                  } = {}) => {
        const params = new URLSearchParams()
        if (storeIds?.length) {
            storeIds.forEach((s) => params.append('storeIds', s))
        }
        if (wkstnIds?.length) {
            wkstnIds.forEach((w) => params.append('wkstnIds', w))
        }
        if (transactionTypes?.length) {
            transactionTypes.forEach((type) => params.append('transactionTypes', type))
        }
        if (transactionFamilies?.length) {
            transactionFamilies.forEach((family) => params.append('transactionFamilies', family))
        }
        if (reconView) {
            params.append('reconView', reconView)
        }
        const res = await apiFetch(`${BASE}/dashboard/analytics?${params}`)
        const json = await res.json()
        return json.data
    },

    getScorecards: async ({
                              storeIds = [],
                              transactionFamilies = [],
                              reconView = null,
                              fromBusinessDate = null,
                              toBusinessDate = null,
                          } = {}) => {
        const params = new URLSearchParams()
        if (storeIds?.length) {
            storeIds.forEach((s) => params.append('storeIds', s))
        }
        if (reconView) {
            params.append('reconView', reconView)
        }
        if (transactionFamilies?.length) {
            transactionFamilies.forEach((family) => params.append('transactionFamilies', family))
        }
        if (validateIsoDate(fromBusinessDate)) {
            params.append('fromBusinessDate', fromBusinessDate)
        }
        if (validateIsoDate(toBusinessDate)) {
            params.append('toBusinessDate', toBusinessDate)
        }
        const res = await apiFetch(`${BASE}/scorecards?${params}`)
        return parseResponse(res)
    },

    getTransactions: async ({
                                storeIds, wkstnIds, transactionTypes, transactionFamilies,
                                reconView,
                                fromBusinessDate, toBusinessDate,
                                reconStatus, fromDate, toDate,
                                page = 0, size = 20,
                            } = {}) => {
        const params = new URLSearchParams({page, size})
        if (storeIds?.length)
            storeIds.forEach(s =>
                params.append('storeIds', s))
        if (wkstnIds?.length)
            wkstnIds.forEach(w =>
                params.append('wkstnIds', w))
        if (transactionTypes?.length)
            transactionTypes.forEach(type =>
                params.append('transactionTypes', type))
        if (transactionFamilies?.length)
            transactionFamilies.forEach(family =>
                params.append('transactionFamilies', family))
        if (reconView)
            params.append('reconView', reconView)
        if (validateIsoDate(fromBusinessDate))
            params.append('fromBusinessDate', fromBusinessDate)
        if (validateIsoDate(toBusinessDate))
            params.append('toBusinessDate', toBusinessDate)
        if (reconStatus)
            params.append('reconStatus', reconStatus)
        if (fromDate) params.append('fromDate', fromDate)
        if (toDate) params.append('toDate', toDate)
        const res = await apiFetch(
            `${BASE}/transactions?${params}`)
        const json = await res.json()
        return json.data
    },

    getTransaction: async (transactionKey, reconView = null) => {
        const encoded = encodeURIComponent(transactionKey)
        const params = new URLSearchParams()
        if (reconView) {
            params.append('reconView', reconView)
        }
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/transactions/${encoded}${suffix}`)
        const json = await res.json()
        return json.data
    },

    getMismatches: async ({
                              storeIds = [], fromBusinessDate,
                              toBusinessDate, page = 0, size = 50,
                          } = {}) => {
        const params = new URLSearchParams({page, size})
        if (storeIds?.length)
            storeIds.forEach(s =>
                params.append('storeIds', s))
        if (validateIsoDate(fromBusinessDate))
            params.append('fromBusinessDate', fromBusinessDate)
        if (validateIsoDate(toBusinessDate))
            params.append('toBusinessDate', toBusinessDate)
        const res = await apiFetch(
            `${BASE}/mismatches?${params}`)
        const json = await res.json()
        return json.data
    },

    getMissing: async ({
                           storeIds = [], fromBusinessDate,
                           toBusinessDate, page = 0, size = 20,
                       } = {}) => {
        const params = new URLSearchParams({page, size})
        if (storeIds?.length)
            storeIds.forEach(s =>
                params.append('storeIds', s))
        if (validateIsoDate(fromBusinessDate))
            params.append('fromBusinessDate', fromBusinessDate)
        if (validateIsoDate(toBusinessDate))
            params.append('toBusinessDate', toBusinessDate)
        const res = await apiFetch(
            `${BASE}/missing?${params}`)
        const json = await res.json()
        return json.data
    },

    getStores: async (reconView = null) => {
        const params = new URLSearchParams()
        if (reconView)
            params.append('reconView', reconView)
        const res = await apiFetch(
            `${BASE}/stores?${params}`)
        const json = await res.json()
        return json.data || []
    },

    getRegisters: async (storeIds, reconView = null) => {
        const params = new URLSearchParams()
        if (storeIds?.length)
            storeIds.forEach(s =>
                params.append('storeIds', s))
        if (reconView)
            params.append('reconView', reconView)
        const res = await apiFetch(
            `${BASE}/registers?${params}`)
        const json = await res.json()
        return json.data || []
    },

    getTransactionTypes: async (storeIds, reconView = null) => {
        const params = new URLSearchParams()
        if (storeIds?.length)
            storeIds.forEach(s =>
                params.append('storeIds', s))
        if (reconView)
            params.append('reconView', reconView)
        const res = await apiFetch(
            `${BASE}/transaction-types?${params}`)
        const json = await res.json()
        return json.data || []
    },

    getTransactionFamilies: async (storeIds, reconView = null) => {
        const params = new URLSearchParams()
        if (storeIds?.length)
            storeIds.forEach(s =>
                params.append('storeIds', s))
        if (reconView)
            params.append('reconView', reconView)
        const res = await apiFetch(
            `${BASE}/transaction-families?${params}`)
        const json = await res.json()
        return json.data || []
    },
}
