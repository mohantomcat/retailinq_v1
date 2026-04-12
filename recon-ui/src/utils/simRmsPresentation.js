import {
    formatSiocsMfcsTransactionFamily,
    formatSiocsMfcsTransactionPhase,
    SIOCS_MFCS_SCOPE_MODE,
} from './siocsMfcsPresentation'

const FIXED_INVENTORY_LANES = Object.freeze({
    SIM_RMS: {
        laneTitle: 'SIM \u2194 RMS',
        originSystem: 'SIM',
        counterpartySystem: 'RMS',
        directionLabel: 'SIM -> RMS',
        description: 'Reconcile on-prem inventory transactions between SIM and RMS database connectors.',
    },
    SIM_MFCS: {
        laneTitle: 'SIM \u2194 MFCS',
        originSystem: 'SIM',
        counterpartySystem: 'MFCS',
        directionLabel: 'SIM -> MFCS',
        description: 'Reconcile inventory transactions between SIM database polling and MFCS RDS services.',
    },
    SIOCS_RMS: {
        laneTitle: 'SIOCS \u2194 RMS',
        originSystem: 'SIOCS',
        counterpartySystem: 'RMS',
        directionLabel: 'SIOCS -> RMS',
        description: 'Reconcile inventory transactions between SIOCS cloud services and RMS database connectors.',
    },
})

export const FIXED_INVENTORY_RECON_VIEWS = Object.freeze(Object.keys(FIXED_INVENTORY_LANES))

function familySummary(selectedFamilies, t) {
    return selectedFamilies.length === 1
        ? formatSiocsMfcsTransactionFamily(selectedFamilies[0], t)
        : selectedFamilies.length > 1
            ? t('{count} families selected', {count: selectedFamilies.length})
            : t('All transaction families')
}

function phaseSummary(selectedPhases, t) {
    return selectedPhases.length === 1
        ? formatSiocsMfcsTransactionPhase(selectedPhases[0], t)
        : selectedPhases.length > 1
            ? t('{count} transaction types selected', {count: selectedPhases.length})
            : t('All transaction types')
}

export function isFixedInventoryReconView(reconView) {
    return Boolean(FIXED_INVENTORY_LANES[String(reconView || '').trim().toUpperCase()])
}

export function buildFixedInventoryPresentation({
    reconView,
    selectedFamilies = [],
    selectedPhases = [],
    t,
}) {
    const config = FIXED_INVENTORY_LANES[String(reconView || '').trim().toUpperCase()]
    if (!config) {
        return null
    }

    return {
        scopeMode: SIOCS_MFCS_SCOPE_MODE.RESOLVED,
        laneTitle: t(config.laneTitle),
        familySummary: familySummary(selectedFamilies, t),
        phaseSummary: phaseSummary(selectedPhases, t),
        originSystem: config.originSystem,
        counterpartySystem: config.counterpartySystem,
        directionLabel: config.directionLabel,
        banner: {
            eyebrow: t('Direction: {direction}', {direction: config.directionLabel}),
            title: t('{lane} Inventory Reconciliation', {lane: config.laneTitle}),
            directionText: null,
            description: t(config.description),
        },
        summaryLabels: {
            total: t('Total Transactions'),
            matched: t('Matched'),
            missing: t('Missing in {system}', {system: config.counterpartySystem}),
            pending: t('Processing Pending in {system}', {system: config.counterpartySystem}),
            awaiting: t('Awaiting Next Business Event'),
            exceptions: t('Exceptions'),
        },
        summaryDefinitions: {
            total: t('All inventory transactions in scope for the selected filters.'),
            matched: t('Transactions reconciled successfully between {origin} and {counterparty}.', {
                origin: config.originSystem,
                counterparty: config.counterpartySystem,
            }),
            missing: t('Transactions expected in {system} but not found.', {system: config.counterpartySystem}),
            pending: t('Transactions received by {system} and still processing.', {system: config.counterpartySystem}),
            awaiting: t('Transactions waiting for the next business event needed to complete reconciliation.'),
            exceptions: t('Transactions requiring review after excluding matched, missing, pending, and awaiting outcomes.'),
        },
        volumeLabels: {
            title: t('Counts and Quantities'),
            coverage: t('Transactions with Quantity Metrics'),
            leftQuantity: t('{system} Quantity', {system: config.originSystem}),
            rightQuantity: t('{system} Quantity', {system: config.counterpartySystem}),
            variance: t('Quantity Variance'),
            note: t('Quantity metrics reflect the connector payload coverage for the selected lane.'),
        },
        exceptionLabels: {
            pending: t('Delayed {system} Update', {system: config.counterpartySystem}),
            awaiting: t('Awaiting Next Business Event'),
            duplicate: t('Duplicate Transactions'),
            itemMissing: t('Item Missing'),
            quantityMismatch: t('Quantity Mismatch'),
            statusConflict: t('Status Conflict'),
        },
    }
}

export function buildSimRmsPresentation(args) {
    return buildFixedInventoryPresentation({
        reconView: 'SIM_RMS',
        ...args,
    })
}

export function buildSimMfcsPresentation(args) {
    return buildFixedInventoryPresentation({
        reconView: 'SIM_MFCS',
        ...args,
    })
}

export function buildSiocsRmsPresentation(args) {
    return buildFixedInventoryPresentation({
        reconView: 'SIOCS_RMS',
        ...args,
    })
}
