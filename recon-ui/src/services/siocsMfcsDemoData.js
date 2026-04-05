const DEMO_STORAGE_KEY = 'siocs_mfcs_demo_mode'
const DEMO_QUERY_PARAM = 'siocsMfcsDemo'
const DEMO_RECON_VIEW = 'SIOCS_MFCS'
const DEMO_REFERENCE_NOW = '2026-04-04T18:00:00Z'
const FAMILY_SORT_ORDER = ['WHD', 'PO', 'DSD', 'TRANSFER_SHIPMENT', 'TRANSFER_RECEIPT', 'RTW', 'RTV']

function round(value, digits = 2) {
    const factor = 10 ** digits
    return Math.round((Number(value) || 0) * factor) / factor
}

function formatDateKey(value) {
    if (!value) {
        return ''
    }
    return String(value).slice(0, 10)
}

function toIsoTimestamp(dateKey, hourOffset = 10) {
    return `${dateKey}T${String(hourOffset).padStart(2, '0')}:00:00Z`
}

function addDays(dateKey, days) {
    const base = new Date(`${dateKey}T00:00:00Z`)
    base.setUTCDate(base.getUTCDate() + days)
    return base.toISOString().slice(0, 10)
}

function statusStartsWith(status, prefix) {
    return String(status || '').toUpperCase().startsWith(prefix)
}

function isMatched(status) {
    return String(status || '').toUpperCase() === 'MATCHED'
}

function isMissing(status) {
    return statusStartsWith(status, 'MISSING_IN_')
}

function isPending(status) {
    return statusStartsWith(status, 'PROCESSING_PENDING_IN_')
}

function isAwaiting(status) {
    return statusStartsWith(status, 'AWAITING_')
}

function isDuplicate(status) {
    return statusStartsWith(status, 'DUPLICATE_IN_')
}

function isProcessingFailed(status) {
    return statusStartsWith(status, 'PROCESSING_FAILED')
}

function isOpenCase(status) {
    return !isMatched(status)
}

function inferOriginSystem(seed) {
    if (seed.originSystem) {
        return seed.originSystem
    }
    if (seed.transactionFamily === 'WHD' && seed.transactionPhase === 'SHIPMENT') {
        return 'MFCS'
    }
    if (seed.transactionFamily === 'WHD' && seed.transactionPhase === 'RECEIPT') {
        return 'SIOCS'
    }
    return 'SIOCS'
}

function inferCounterpartySystem(seed) {
    const originSystem = inferOriginSystem(seed)
    return originSystem === 'MFCS' ? 'SIOCS' : 'MFCS'
}

function buildDiscrepancies(seed, derived) {
    const discrepancyBase = {
        itemId: `${seed.transactionFamily}-ITEM-01`,
        description: `${seed.transactionFamily} sample line`,
        lineType: 'Inventory',
        xstoreQuantity: derived.sourceQuantity,
        siocsQuantity: derived.targetQuantity,
        varianceQuantity: round(derived.sourceQuantity - derived.targetQuantity),
        variancePercent: derived.sourceQuantity
            ? round(((derived.sourceQuantity - derived.targetQuantity) / derived.sourceQuantity) * 100)
            : 0,
        varianceAmount: derived.amountVariance,
        varianceAmountPercent: derived.transactionAmount
            ? round((derived.amountVariance / derived.transactionAmount) * 100)
            : 0,
        severityBand: 'MEDIUM',
        withinTolerance: false,
    }

    if (isMatched(seed.reconStatus)) {
        return []
    }
    if (isMissing(seed.reconStatus)) {
        return [{
            ...discrepancyBase,
            type: 'Missing Transaction',
            siocsQuantity: 0,
            varianceQuantity: derived.sourceQuantity,
            severityBand: 'HIGH',
        }]
    }
    if (isPending(seed.reconStatus) || isAwaiting(seed.reconStatus)) {
        return [{
            ...discrepancyBase,
            type: 'In Flight',
            withinTolerance: true,
            severityBand: 'LOW',
        }]
    }
    if (isDuplicate(seed.reconStatus)) {
        return [{
            ...discrepancyBase,
            type: 'Duplicate Posting',
            varianceQuantity: 0,
            severityBand: 'HIGH',
        }]
    }
    if (String(seed.reconStatus).toUpperCase() === 'ITEM_MISSING') {
        return [{
            ...discrepancyBase,
            type: 'Item Missing',
            siocsQuantity: 0,
            varianceQuantity: derived.sourceQuantity,
            severityBand: 'HIGH',
        }]
    }
    if (String(seed.reconStatus).toUpperCase() === 'QUANTITY_MISMATCH') {
        return [{
            ...discrepancyBase,
            type: 'Quantity Mismatch',
            severityBand: 'HIGH',
        }]
    }
    return [{
        ...discrepancyBase,
        type: 'Requires Review',
    }]
}

function buildDemoTransaction(seed, index) {
    const sourceQuantity = seed.sourceQuantity ?? 0
    const targetQuantity = seed.targetQuantity ?? sourceQuantity
    const quantityVariance = round(sourceQuantity - targetQuantity)
    const transactionAmount = round(seed.transactionAmount ?? sourceQuantity * (seed.unitAmount ?? 24.75))
    const amountVariance = round(seed.amountVariance ?? quantityVariance * (seed.unitAmount ?? 24.75))
    const quantityImpact = round(seed.quantityImpact ?? Math.abs(quantityVariance || sourceQuantity))
    const affectedItemCount = seed.affectedItemCount ?? Math.max(1, Math.ceil(sourceQuantity / 4))
    const originSystem = inferOriginSystem(seed)
    const counterpartySystem = inferCounterpartySystem(seed)
    const updatedAt = seed.updatedAt || toIsoTimestamp(seed.businessDate, 9 + (index % 8))
    const reconciledAt = seed.reconciledAt || (isMatched(seed.reconStatus) ? toIsoTimestamp(seed.businessDate, 16) : null)
    const derived = {
        sourceQuantity,
        targetQuantity,
        transactionAmount,
        amountVariance,
    }
    const discrepancies = seed.discrepancies || buildDiscrepancies(seed, derived)
    const quantityVariancePercent = sourceQuantity
        ? round((Math.abs(quantityVariance) / sourceQuantity) * 100)
        : 0
    const amountVariancePercent = transactionAmount
        ? round((Math.abs(amountVariance) / transactionAmount) * 100)
        : 0
    const matchBand = seed.matchBand || (
        isMatched(seed.reconStatus)
            ? 'High'
            : (String(seed.reconStatus).toUpperCase() === 'QUANTITY_MISMATCH' ? 'Medium' : 'Low')
    )
    const matchScore = seed.matchScore ?? (
        isMatched(seed.reconStatus)
            ? 99
            : (String(seed.reconStatus).toUpperCase() === 'QUANTITY_MISMATCH' ? 86 : 63)
    )

    return {
        transactionKey: seed.transactionKey,
        externalId: seed.externalId || `${seed.storeId}-${seed.businessDate}-${seed.transactionKey}`,
        reconView: DEMO_RECON_VIEW,
        simSource: originSystem,
        originSystem,
        counterpartySystem,
        storeId: String(seed.storeId),
        wkstnId: seed.wkstnId || `${seed.transactionFamily}-01`,
        businessDate: seed.businessDate,
        businessDateDisplay: seed.businessDate,
        transactionType: seed.transactionType,
        transactionFamily: seed.transactionFamily,
        transactionPhase: seed.transactionPhase,
        reconStatus: seed.reconStatus,
        updatedAt,
        reconciledAt,
        transactionAmount,
        amountVariance,
        quantityImpact,
        affectedItemCount,
        matchBand,
        matchScore,
        sourceQuantity,
        targetQuantity,
        quantityVariance,
        toleranceProfile: seed.toleranceProfile || 'Inventory sync standard',
        matchRule: seed.matchRule || `${seed.transactionFamily} ${seed.transactionPhase} quantity alignment`,
        matchSummary: seed.matchSummary || `${originSystem} to ${counterpartySystem} sample reconciliation`,
        xstoreChecksum: seed.xstoreChecksum || `${seed.transactionKey}-SRC`,
        siocsChecksum: seed.siocsChecksum || `${seed.transactionKey}-CTR`,
        checksumMatch: seed.checksumMatch ?? (isMatched(seed.reconStatus) || isPending(seed.reconStatus) || isAwaiting(seed.reconStatus)),
        toleranceApplied: seed.toleranceApplied ?? false,
        quantityVariancePercent,
        amountVariancePercent,
        matchedLineCount: seed.matchedLineCount ?? Math.max(affectedItemCount - discrepancies.length, 0),
        discrepantLineCount: seed.discrepantLineCount ?? discrepancies.length,
        toleratedDiscrepancyCount: seed.toleratedDiscrepancyCount ?? 0,
        materialDiscrepancyCount: seed.materialDiscrepancyCount ?? discrepancies.length,
        discrepancies,
        slaState: seed.slaState || (isMatched(seed.reconStatus) ? null : 'WITHIN_SLA'),
    }
}

const DEMO_TRANSACTION_SEEDS = [
    {
        transactionKey: 'WHD-SHIP-1001',
        storeId: '1101',
        wkstnId: 'WHD-A1',
        businessDate: '2026-03-29',
        transactionType: 'ASNIN',
        transactionFamily: 'WHD',
        transactionPhase: 'SHIPMENT',
        reconStatus: 'MATCHED',
        sourceQuantity: 42,
        targetQuantity: 42,
        transactionAmount: 1041.5,
    },
    {
        transactionKey: 'WHD-SHIP-1002',
        storeId: '1101',
        wkstnId: 'WHD-A1',
        businessDate: '2026-03-30',
        transactionType: 'ASNIN',
        transactionFamily: 'WHD',
        transactionPhase: 'SHIPMENT',
        reconStatus: 'MISSING_IN_SIOCS',
        sourceQuantity: 48,
        targetQuantity: 0,
        transactionAmount: 1188,
        slaState: 'BREACHED',
    },
    {
        transactionKey: 'WHD-SHIP-1003',
        storeId: '1101',
        wkstnId: 'WHD-A2',
        businessDate: '2026-03-31',
        transactionType: 'ASNIN',
        transactionFamily: 'WHD',
        transactionPhase: 'SHIPMENT',
        reconStatus: 'PROCESSING_PENDING_IN_SIOCS',
        sourceQuantity: 52,
        targetQuantity: 52,
        transactionAmount: 1287,
        slaState: 'DUE_SOON',
    },
    {
        transactionKey: 'WHD-SHIP-1004',
        storeId: '1102',
        wkstnId: 'WHD-B1',
        businessDate: '2026-04-01',
        transactionType: 'ASNIN',
        transactionFamily: 'WHD',
        transactionPhase: 'SHIPMENT',
        reconStatus: 'AWAITING_SIOCS',
        sourceQuantity: 36,
        targetQuantity: 36,
        transactionAmount: 891,
        slaState: 'WITHIN_SLA',
    },
    {
        transactionKey: 'WHD-SHIP-1005',
        storeId: '1102',
        wkstnId: 'WHD-B1',
        businessDate: '2026-04-02',
        transactionType: 'ASNIN',
        transactionFamily: 'WHD',
        transactionPhase: 'SHIPMENT',
        reconStatus: 'QUANTITY_MISMATCH',
        sourceQuantity: 60,
        targetQuantity: 58,
        transactionAmount: 1485,
        amountVariance: 49.5,
        slaState: 'WITHIN_SLA',
    },
    {
        transactionKey: 'WHD-REC-2001',
        storeId: '1101',
        wkstnId: 'WHD-A3',
        businessDate: '2026-04-01',
        transactionType: 'RCVUPD',
        transactionFamily: 'WHD',
        transactionPhase: 'RECEIPT',
        reconStatus: 'MATCHED',
        sourceQuantity: 40,
        targetQuantity: 40,
        transactionAmount: 990,
    },
    {
        transactionKey: 'WHD-REC-2002',
        storeId: '1101',
        wkstnId: 'WHD-A3',
        businessDate: '2026-04-02',
        transactionType: 'RCVUPD',
        transactionFamily: 'WHD',
        transactionPhase: 'RECEIPT',
        reconStatus: 'MISSING_IN_MFCS',
        sourceQuantity: 26,
        targetQuantity: 0,
        transactionAmount: 643.5,
        originSystem: 'SIOCS',
        slaState: 'BREACHED',
    },
    {
        transactionKey: 'WHD-REC-2003',
        storeId: '1102',
        wkstnId: 'WHD-B2',
        businessDate: '2026-04-03',
        transactionType: 'RCVUPD',
        transactionFamily: 'WHD',
        transactionPhase: 'RECEIPT',
        reconStatus: 'PROCESSING_PENDING_IN_MFCS',
        sourceQuantity: 31,
        targetQuantity: 31,
        transactionAmount: 767.25,
        originSystem: 'SIOCS',
        slaState: 'DUE_SOON',
    },
    {
        transactionKey: 'WHD-REC-2004',
        storeId: '1102',
        wkstnId: 'WHD-B2',
        businessDate: '2026-04-03',
        transactionType: 'RCVUPD',
        transactionFamily: 'WHD',
        transactionPhase: 'RECEIPT',
        reconStatus: 'AWAITING_MFCS',
        sourceQuantity: 18,
        targetQuantity: 18,
        transactionAmount: 445.5,
        originSystem: 'SIOCS',
        slaState: 'WITHIN_SLA',
    },
    {
        transactionKey: 'WHD-REC-2005',
        storeId: '1102',
        wkstnId: 'WHD-B2',
        businessDate: '2026-04-04',
        transactionType: 'RCVUPD',
        transactionFamily: 'WHD',
        transactionPhase: 'RECEIPT',
        reconStatus: 'DUPLICATE_IN_MFCS',
        sourceQuantity: 22,
        targetQuantity: 22,
        transactionAmount: 544.5,
        originSystem: 'SIOCS',
        slaState: 'WITHIN_SLA',
    },
    {
        transactionKey: 'PO-ORD-3001',
        storeId: '1201',
        wkstnId: 'PO-01',
        businessDate: '2026-03-28',
        transactionType: 'ORDHDR',
        transactionFamily: 'PO',
        transactionPhase: 'ORDER',
        reconStatus: 'MATCHED',
        sourceQuantity: 18,
        targetQuantity: 18,
        transactionAmount: 486,
        originSystem: 'MFCS',
    },
    {
        transactionKey: 'PO-ORD-3002',
        storeId: '1201',
        wkstnId: 'PO-01',
        businessDate: '2026-03-30',
        transactionType: 'ORDHDR',
        transactionFamily: 'PO',
        transactionPhase: 'ORDER',
        reconStatus: 'ITEM_MISSING',
        sourceQuantity: 24,
        targetQuantity: 20,
        transactionAmount: 648,
        originSystem: 'MFCS',
        slaState: 'WITHIN_SLA',
    },
    {
        transactionKey: 'DSD-REC-4001',
        storeId: '1201',
        wkstnId: 'DSD-01',
        businessDate: '2026-04-01',
        transactionType: 'DSDREC',
        transactionFamily: 'DSD',
        transactionPhase: 'RECEIPT',
        reconStatus: 'MATCHED',
        sourceQuantity: 14,
        targetQuantity: 14,
        transactionAmount: 350,
    },
    {
        transactionKey: 'RTV-RET-5001',
        storeId: '1305',
        wkstnId: 'RTV-01',
        businessDate: '2026-04-02',
        transactionType: 'RTVCRE',
        transactionFamily: 'RTV',
        transactionPhase: 'RETURN',
        reconStatus: 'QUANTITY_MISMATCH',
        sourceQuantity: 16,
        targetQuantity: 13,
        transactionAmount: 396,
        amountVariance: 74.25,
        originSystem: 'SIOCS',
        slaState: 'WITHIN_SLA',
    },
    {
        transactionKey: 'RTV-RET-5002',
        storeId: '1305',
        wkstnId: 'RTV-01',
        businessDate: '2026-04-04',
        transactionType: 'RTVACK',
        transactionFamily: 'RTV',
        transactionPhase: 'RETURN',
        reconStatus: 'PROCESSING_PENDING_IN_MFCS',
        sourceQuantity: 12,
        targetQuantity: 12,
        transactionAmount: 297,
        originSystem: 'SIOCS',
        slaState: 'DUE_SOON',
    },
    {
        transactionKey: 'TS-6001',
        storeId: '1305',
        wkstnId: 'TS-01',
        businessDate: '2026-04-03',
        transactionType: 'TSHIP',
        transactionFamily: 'TRANSFER_SHIPMENT',
        transactionPhase: 'SHIPMENT',
        reconStatus: 'MISSING_IN_MFCS',
        sourceQuantity: 20,
        targetQuantity: 0,
        transactionAmount: 495,
        originSystem: 'SIOCS',
        slaState: 'BREACHED',
    },
]

const DEMO_TRANSACTIONS = DEMO_TRANSACTION_SEEDS.map(buildDemoTransaction)

function getSearchParamValue(name) {
    if (typeof window === 'undefined') {
        return null
    }
    try {
        return new URLSearchParams(window.location.search).get(name)
    } catch {
        return null
    }
}

export function isSiocsMfcsDemoAvailable() {
    if (import.meta.env.VITE_SIOCS_MFCS_DEMO === 'true') {
        return true
    }
    return getSearchParamValue(DEMO_QUERY_PARAM) === '1'
}

export function isSiocsMfcsDemoEnabled() {
    if (isSiocsMfcsDemoAvailable()) {
        return true
    }
    if (typeof window === 'undefined') {
        return false
    }
    return window.sessionStorage.getItem(DEMO_STORAGE_KEY) === 'true'
}

export function setSiocsMfcsDemoEnabled(enabled) {
    if (typeof window === 'undefined') {
        return
    }
    if (enabled) {
        window.sessionStorage.setItem(DEMO_STORAGE_KEY, 'true')
    } else {
        window.sessionStorage.removeItem(DEMO_STORAGE_KEY)
    }
}

function filterTransactions({
    storeIds = [],
    transactionFamilies = [],
    transactionPhases = [],
    reconStatus = null,
    reconStatuses = [],
    fromBusinessDate = null,
    toBusinessDate = null,
} = {}) {
    const normalizedStores = new Set((storeIds || []).map((value) => String(value)))
    const normalizedFamilies = new Set((transactionFamilies || []).map((value) => String(value).toUpperCase()))
    const normalizedPhases = new Set((transactionPhases || []).map((value) => String(value).toUpperCase()))
    const normalizedStatus = reconStatus ? String(reconStatus).toUpperCase() : null
    const normalizedStatuses = new Set((reconStatuses || []).map((value) => String(value).toUpperCase()))
    const fromDate = formatDateKey(fromBusinessDate)
    const toDate = formatDateKey(toBusinessDate)

    return DEMO_TRANSACTIONS.filter((item) => {
        if (normalizedStores.size > 0 && !normalizedStores.has(String(item.storeId))) {
            return false
        }
        if (normalizedFamilies.size > 0 && !normalizedFamilies.has(String(item.transactionFamily).toUpperCase())) {
            return false
        }
        if (normalizedPhases.size > 0 && !normalizedPhases.has(String(item.transactionPhase).toUpperCase())) {
            return false
        }
        if (normalizedStatus && String(item.reconStatus).toUpperCase() !== normalizedStatus) {
            return false
        }
        if (normalizedStatuses.size > 0 && !normalizedStatuses.has(String(item.reconStatus).toUpperCase())) {
            return false
        }
        if (fromDate && formatDateKey(item.businessDate) < fromDate) {
            return false
        }
        if (toDate && formatDateKey(item.businessDate) > toDate) {
            return false
        }
        return true
    })
}

function countWhere(records, predicate) {
    return records.reduce((total, item) => total + (predicate(item) ? 1 : 0), 0)
}

function buildTransactionFamilyVolumes(records) {
    const grouped = new Map()

    records.forEach((item) => {
        const key = item.transactionFamily
        if (!grouped.has(key)) {
            grouped.set(key, {
                transactionFamily: key,
                transactionCount: 0,
                sourceQuantityTotal: 0,
                targetQuantityTotal: 0,
                quantityVarianceTotal: 0,
                quantityMetricsTransactionCount: 0,
            })
        }
        const entry = grouped.get(key)
        entry.transactionCount += 1
        entry.sourceQuantityTotal += Number(item.sourceQuantity || 0)
        entry.targetQuantityTotal += Number(item.targetQuantity || 0)
        entry.quantityVarianceTotal += Number(item.quantityVariance || 0)
        if (item.sourceQuantity !== null && item.sourceQuantity !== undefined && item.targetQuantity !== null && item.targetQuantity !== undefined) {
            entry.quantityMetricsTransactionCount += 1
        }
    })

    return [...grouped.values()].sort((left, right) => {
        const leftIndex = FAMILY_SORT_ORDER.indexOf(left.transactionFamily)
        const rightIndex = FAMILY_SORT_ORDER.indexOf(right.transactionFamily)
        return (leftIndex === -1 ? 999 : leftIndex) - (rightIndex === -1 ? 999 : rightIndex)
    })
}

function buildDashboardStats(records) {
    const totalTransactions = records.length
    const matched = countWhere(records, (item) => isMatched(item.reconStatus))
    const missingInSiocs = countWhere(records, (item) => isMissing(item.reconStatus))
    const quantityMismatch = countWhere(records, (item) => String(item.reconStatus).toUpperCase() === 'QUANTITY_MISMATCH')
    const transactionTotalMismatch = countWhere(records, (item) => String(item.reconStatus).toUpperCase() === 'TOTAL_MISMATCH')
    const itemMissing = countWhere(records, (item) => String(item.reconStatus).toUpperCase() === 'ITEM_MISSING')
    const duplicateTransactions = countWhere(records, (item) => isDuplicate(item.reconStatus))
    const processingPending = countWhere(records, (item) => isPending(item.reconStatus))
    const awaitingSim = countWhere(records, (item) => isAwaiting(item.reconStatus))
    const processingFailed = countWhere(records, (item) => isProcessingFailed(item.reconStatus))
    const sourceQuantityTotal = round(records.reduce((total, item) => total + Number(item.sourceQuantity || 0), 0))
    const targetQuantityTotal = round(records.reduce((total, item) => total + Number(item.targetQuantity || 0), 0))
    const quantityVarianceTotal = round(records.reduce((total, item) => total + Number(item.quantityVariance || 0), 0))
    const quantityMetricsTransactionCount = countWhere(
        records,
        (item) => item.sourceQuantity !== null && item.sourceQuantity !== undefined && item.targetQuantity !== null && item.targetQuantity !== undefined
    )

    return {
        totalTransactions,
        matched,
        missingInSiocs,
        quantityMismatch,
        transactionTotalMismatch,
        itemMissing,
        duplicateTransactions,
        processingPending,
        awaitingSim,
        processingFailed,
        matchRate: totalTransactions ? round((matched / totalTransactions) * 100) : 0,
        asOf: DEMO_REFERENCE_NOW,
        byTransactionFamily: buildTransactionFamilyVolumes(records).reduce((acc, item) => {
            acc[item.transactionFamily] = item.transactionCount
            return acc
        }, {}),
        transactionFamilyVolumes: buildTransactionFamilyVolumes(records),
        sourceQuantityTotal,
        targetQuantityTotal,
        quantityVarianceTotal,
        quantityMetricsTransactionCount,
    }
}

function buildTrendSeries(records, days) {
    const endDate = DEMO_REFERENCE_NOW.slice(0, 10)

    return Array.from({length: days}, (_, index) => {
        const businessDate = addDays(endDate, index - (days - 1))
        const dayRecords = records.filter((item) => formatDateKey(item.businessDate) === businessDate)
        const totalTransactions = dayRecords.length
        const matched = countWhere(dayRecords, (item) => isMatched(item.reconStatus))
        const missing = countWhere(dayRecords, (item) => isMissing(item.reconStatus))
        const duplicates = countWhere(dayRecords, (item) => isDuplicate(item.reconStatus))
        const quantityMismatch = countWhere(dayRecords, (item) => String(item.reconStatus).toUpperCase() === 'QUANTITY_MISMATCH')
        const totalMismatch = countWhere(dayRecords, (item) => String(item.reconStatus).toUpperCase() === 'TOTAL_MISMATCH')
        const itemMissing = countWhere(dayRecords, (item) => String(item.reconStatus).toUpperCase() === 'ITEM_MISSING')
        return {
            businessDate,
            totalTransactions,
            matched,
            missing,
            duplicates,
            quantityMismatch,
            totalMismatch,
            itemMissing,
            matchRate: totalTransactions ? round((matched / totalTransactions) * 100) : 0,
        }
    })
}

function buildTopFailingStores(records) {
    const grouped = new Map()

    records.forEach((item) => {
        const key = String(item.storeId)
        if (!grouped.has(key)) {
            grouped.set(key, [])
        }
        grouped.get(key).push(item)
    })

    return [...grouped.entries()]
        .map(([key, entries]) => {
            const total = entries.length
            const matched = countWhere(entries, (item) => isMatched(item.reconStatus))
            const missing = countWhere(entries, (item) => isMissing(item.reconStatus))
            return {
                key,
                exceptionCount: total - matched,
                missing,
                matchRate: total ? round((matched / total) * 100) : 0,
            }
        })
        .sort((left, right) => right.exceptionCount - left.exceptionCount || right.missing - left.missing)
        .slice(0, 5)
}

function buildTopFailingFamilies(records) {
    const grouped = new Map()

    records.forEach((item) => {
        const key = item.transactionFamily
        if (!grouped.has(key)) {
            grouped.set(key, [])
        }
        grouped.get(key).push(item)
    })

    return [...grouped.entries()]
        .map(([key, entries]) => {
            const total = entries.length
            const matched = countWhere(entries, (item) => isMatched(item.reconStatus))
            const duplicates = countWhere(entries, (item) => isDuplicate(item.reconStatus))
            const exceptionCount = total - matched
            return {
                key,
                duplicates,
                exceptionCount,
                matchRate: total ? round((matched / total) * 100) : 0,
            }
        })
        .sort((left, right) => right.exceptionCount - left.exceptionCount || right.duplicates - left.duplicates)
        .slice(0, 5)
}

function buildExceptionAging(records) {
    const referenceTime = new Date(DEMO_REFERENCE_NOW).getTime()
    const buckets = [
        {label: '0-4h', test: (hours) => hours < 4},
        {label: '4-24h', test: (hours) => hours >= 4 && hours < 24},
        {label: '1-3d', test: (hours) => hours >= 24 && hours < 72},
        {label: '3d+', test: (hours) => hours >= 72},
    ]

    return buckets.map((bucket) => ({
        label: bucket.label,
        count: records.filter((item) => {
            if (!isOpenCase(item.reconStatus)) {
                return false
            }
            const updatedAt = new Date(item.updatedAt || item.businessDate).getTime()
            const ageHours = Math.max(0, Math.floor((referenceTime - updatedAt) / 3600000))
            return bucket.test(ageHours)
        }).length,
    }))
}

function buildSlaSummary(records) {
    const openRecords = records.filter((item) => isOpenCase(item.reconStatus))
    const activeCases = openRecords.length
    const breachedCases = countWhere(openRecords, (item) => item.slaState === 'BREACHED')
    const dueSoonCases = countWhere(openRecords, (item) => item.slaState === 'DUE_SOON')
    const withinSlaCases = countWhere(openRecords, (item) => item.slaState === 'WITHIN_SLA')

    return {
        activeCases,
        breachedCases,
        dueSoonCases,
        withinSlaCases,
        breachRate: activeCases ? round((breachedCases / activeCases) * 100) : 0,
    }
}

export function getSiocsMfcsDemoStores() {
    return [...new Set(DEMO_TRANSACTIONS.map((item) => String(item.storeId)))].sort()
}

export function getSiocsMfcsDemoTransactionFamilies(storeIds = []) {
    const records = filterTransactions({storeIds})
    return [...new Set(records.map((item) => item.transactionFamily))]
        .sort((left, right) => {
            const leftIndex = FAMILY_SORT_ORDER.indexOf(left)
            const rightIndex = FAMILY_SORT_ORDER.indexOf(right)
            return (leftIndex === -1 ? 999 : leftIndex) - (rightIndex === -1 ? 999 : rightIndex)
        })
}

export function getSiocsMfcsDemoDashboard(filters = {}) {
    return buildDashboardStats(filterTransactions(filters))
}

export function getSiocsMfcsDemoDashboardAnalytics(filters = {}) {
    const records = filterTransactions(filters)
    return {
        slaSummary: buildSlaSummary(records),
        last7Days: buildTrendSeries(records, 7),
        last30Days: buildTrendSeries(records, 30),
        topFailingStores: buildTopFailingStores(records),
        topFailingRegisters: buildTopFailingFamilies(records),
        exceptionAging: buildExceptionAging(records),
    }
}

export function getSiocsMfcsDemoTransactions({
    page = 0,
    size = 20,
    ...filters
} = {}) {
    const filtered = filterTransactions(filters)
        .sort((left, right) => String(right.updatedAt || '').localeCompare(String(left.updatedAt || '')))
    const start = page * size
    const content = filtered.slice(start, start + size)

    return {
        content,
        totalElements: filtered.length,
        totalPages: size > 0 ? Math.ceil(filtered.length / size) : 1,
        pageNumber: page,
        pageSize: size,
        first: page === 0,
        last: start + size >= filtered.length,
    }
}

export function getSiocsMfcsDemoTransaction(transactionKey) {
    const match = DEMO_TRANSACTIONS.find((item) => item.transactionKey === transactionKey)
    if (!match) {
        throw new Error('Demo transaction not found')
    }
    return match
}
