export const SIOCS_MFCS_SCOPE_MODE = {
    MIXED: 'mixed',
    RESOLVED: 'resolved',
}

const PHASE_LABELS = {
    SHIPMENT: 'Shipment',
    RECEIPT: 'Receipt',
    RETURN: 'Return',
    ORDER: 'Order',
    UNKNOWN: 'Unknown',
}

// TODO: enable only when backend exposes reliable direction metadata that can
// safely distinguish origin/counterparty by family + phase for this lane.
const TRUSTED_DIRECTION_PROFILES = Object.freeze({})

function normalizedValue(value) {
    return String(value || '').trim().toUpperCase()
}

export function formatSiocsMfcsTransactionFamily(value, t) {
    switch (normalizedValue(value)) {
        case 'WHD':
            return t('WHD')
        case 'PO':
            return t('PO')
        case 'DSD':
            return t('DSD')
        case 'TRANSFER_SHIPMENT':
            return t('Transfer Shipment')
        case 'TRANSFER_RECEIPT':
            return t('Transfer Receipt')
        case 'RTW':
            return t('RTW')
        case 'RTV':
            return t('RTV')
        case 'UNKNOWN':
            return t('Unknown')
        default:
            return value ? String(value) : t('Unknown')
    }
}

export function formatSiocsMfcsTransactionPhase(value, t) {
    const normalized = normalizedValue(value)
    return t(PHASE_LABELS[normalized] || normalized || 'Unknown')
}

export function getSiocsMfcsPhaseOptions(t) {
    return Object.entries(PHASE_LABELS).map(([value, label]) => ({
        value,
        label: t(label),
    }))
}

export function buildSiocsMfcsDemoResolvedProfiles(t) {
    return {
        'WHD|SHIPMENT': {
            originSystem: 'MFCS',
            counterpartySystem: 'SIOCS',
            directionLabel: 'MFCS -> SIOCS',
            contextTitle: t('WHD / Shipment Advice'),
            description: t('Reconcile warehouse shipment advice sent from MFCS to SIOCS before store receipt.'),
            summaryLabels: {
                total: t('Total Shipment Advices'),
                matched: t('Matched'),
                missing: t('Missing in SIOCS'),
                pending: t('Processing Pending in SIOCS'),
                awaiting: t('Awaiting Store Receipt'),
                exceptions: t('Exceptions'),
            },
            summaryDefinitions: {
                total: t('All warehouse shipment advice transactions in scope for the selected filters.'),
                matched: t('Shipment advice records reconciled successfully between MFCS and SIOCS.'),
                missing: t('Shipment advice records expected in SIOCS but not found.'),
                pending: t('Shipment advice records received by SIOCS and still processing.'),
                awaiting: t('Shipment advice records waiting on the store receipt business event.'),
                exceptions: t('Shipment advice transactions requiring review after excluding matched, missing, pending, and awaiting outcomes.'),
            },
            volumeLabels: {
                title: t('Counts and Quantities'),
                coverage: t('Shipment Advices with Quantity Metrics'),
                leftQuantity: t('MFCS Quantity'),
                rightQuantity: t('SIOCS Quantity'),
                variance: t('Quantity Variance'),
                note: t('Quantity metrics are available for sample shipment advice payloads. Value metrics remain deferred until backend enrichment is available.'),
            },
            exceptionLabels: {
                pending: t('Delayed SIOCS Update'),
                awaiting: t('Awaiting Store Receipt'),
                duplicate: t('Duplicate Shipment Advice'),
                itemMissing: t('Item Missing'),
                quantityMismatch: t('Quantity Mismatch'),
                statusConflict: t('Status Conflict'),
            },
        },
        'WHD|RECEIPT': {
            originSystem: 'SIOCS',
            counterpartySystem: 'MFCS',
            directionLabel: 'SIOCS -> MFCS',
            contextTitle: t('WHD / Receipt Update'),
            description: t('Reconcile store receipt confirmation sent from SIOCS to MFCS after receipt posting.'),
            summaryLabels: {
                total: t('Total Receipt Updates'),
                matched: t('Matched'),
                missing: t('Missing in MFCS'),
                pending: t('Processing Pending in MFCS'),
                awaiting: t('Awaiting MFCS Update'),
                exceptions: t('Exceptions'),
            },
            summaryDefinitions: {
                total: t('All store receipt update transactions in scope for the selected filters.'),
                matched: t('Receipt update records reconciled successfully between SIOCS and MFCS.'),
                missing: t('Receipt update records expected in MFCS but not found.'),
                pending: t('Receipt update records received by MFCS and still processing.'),
                awaiting: t('Receipt update records waiting on the MFCS update business event.'),
                exceptions: t('Receipt update transactions requiring review after excluding matched, missing, pending, and awaiting outcomes.'),
            },
            volumeLabels: {
                title: t('Counts and Quantities'),
                coverage: t('Receipt Updates with Quantity Metrics'),
                leftQuantity: t('SIOCS Quantity'),
                rightQuantity: t('MFCS Quantity'),
                variance: t('Quantity Variance'),
                note: t('Quantity metrics are available for sample receipt update payloads. Value metrics remain deferred until backend enrichment is available.'),
            },
            exceptionLabels: {
                pending: t('Delayed MFCS Update'),
                awaiting: t('Awaiting MFCS Update'),
                duplicate: t('Duplicate Receipt Update'),
                itemMissing: t('Item Missing'),
                quantityMismatch: t('Quantity Mismatch'),
                statusConflict: t('Status Conflict'),
            },
        },
    }
}

function resolvedProfileKey(family, phase) {
    const normalizedFamily = normalizedValue(family)
    const normalizedPhase = normalizedValue(phase)
    if (!normalizedFamily || !normalizedPhase) {
        return null
    }
    return `${normalizedFamily}|${normalizedPhase}`
}

function buildMixedScopePresentation(selectedFamilies, selectedPhases, t) {
    const familySummary = selectedFamilies.length === 1
        ? formatSiocsMfcsTransactionFamily(selectedFamilies[0], t)
        : selectedFamilies.length > 1
            ? t('{count} families selected', {count: selectedFamilies.length})
            : t('All transaction families')

    const phaseSummary = selectedPhases.length === 1
        ? formatSiocsMfcsTransactionPhase(selectedPhases[0], t)
        : selectedPhases.length > 1
            ? t('{count} transaction types selected', {count: selectedPhases.length})
            : t('All transaction types')

    return {
        scopeMode: SIOCS_MFCS_SCOPE_MODE.MIXED,
        laneTitle: t('SIOCS \u2194 MFCS'),
        familySummary,
        phaseSummary,
        originSystem: null,
        counterpartySystem: null,
        directionLabel: t('Direction depends on selected transaction family and transaction type.'),
        banner: {
            eyebrow: null,
            title: t('Mixed transaction scope'),
            directionText: t('Direction depends on selected transaction family and transaction type.'),
            description: t('Narrow filters to a single family and transaction type to see direction-specific reconciliation wording when the backend provides enough context.'),
        },
        summaryLabels: {
            total: t('Total Transactions'),
            matched: t('Matched'),
            missing: t('Missing in Counterparty System'),
            pending: t('Processing Pending in Counterparty System'),
            awaiting: t('Awaiting Next Business Event'),
            exceptions: t('Exceptions'),
        },
        summaryDefinitions: {
            total: t('All SIOCS and MFCS transactions in scope for the selected filters.'),
            matched: t('Transactions reconciled successfully across the lane.'),
            missing: t('Transactions expected in the counterparty system but not found.'),
            pending: t('Transactions where the counterparty update is still processing.'),
            awaiting: t('Transactions waiting for the next business event needed to complete reconciliation.'),
            exceptions: t('Transactions requiring review after excluding matched, missing, pending, and awaiting outcomes.'),
        },
        volumeLabels: {
            title: t('Counts and Quantities'),
            coverage: t('Transactions with Quantity Metrics'),
            leftQuantity: t('SIOCS Quantity'),
            rightQuantity: t('MFCS Quantity'),
            variance: t('Quantity Variance'),
            note: t('Quantity metrics are available when the live payload supports them. Value metrics remain deferred until backend enrichment is available.'),
        },
        exceptionLabels: {
            pending: t('Delayed Update'),
            awaiting: t('Awaiting Next Business Event'),
            duplicate: t('Duplicate Transactions'),
            itemMissing: t('Item Missing'),
            quantityMismatch: t('Quantity Mismatch'),
            statusConflict: t('Status Conflict'),
        },
    }
}

function buildResolvedScopePresentation(profile, family, phase, t) {
    const directionText = t('Direction: {direction}', {direction: profile.directionLabel})
    return {
        scopeMode: SIOCS_MFCS_SCOPE_MODE.RESOLVED,
        laneTitle: t('SIOCS \u2194 MFCS'),
        familySummary: formatSiocsMfcsTransactionFamily(family, t),
        phaseSummary: formatSiocsMfcsTransactionPhase(phase, t),
        originSystem: profile.originSystem,
        counterpartySystem: profile.counterpartySystem,
        directionLabel: profile.directionLabel,
        banner: {
            eyebrow: directionText,
            title: profile.contextTitle,
            directionText: null,
            description: profile.description,
        },
        summaryLabels: profile.summaryLabels,
        summaryDefinitions: profile.summaryDefinitions,
        volumeLabels: profile.volumeLabels,
        exceptionLabels: profile.exceptionLabels,
    }
}

export function buildSiocsMfcsPresentation({
    selectedFamilies = [],
    selectedPhases = [],
    resolvedProfiles = TRUSTED_DIRECTION_PROFILES,
    t,
}) {
    const key = selectedFamilies.length === 1 && selectedPhases.length === 1
        ? resolvedProfileKey(selectedFamilies[0], selectedPhases[0])
        : null
    const resolvedProfile = key ? resolvedProfiles[key] : null

    if (resolvedProfile) {
        return buildResolvedScopePresentation(resolvedProfile, selectedFamilies[0], selectedPhases[0], t)
    }

    return buildMixedScopePresentation(selectedFamilies, selectedPhases, t)
}

export function getSiocsMfcsStatusPresentation(status, presentation, t) {
    const normalized = normalizedValue(status)
    const laneTitle = presentation?.laneTitle || t('reconciliation lane')

    if (presentation?.scopeMode === SIOCS_MFCS_SCOPE_MODE.RESOLVED) {
        const counterpartySystem = presentation.counterpartySystem || t('Counterparty System')
        const originSystem = presentation.originSystem || t('Origin System')
        switch (normalized) {
            case 'MATCHED':
                return {
                    label: presentation.summaryLabels.matched,
                    issueType: t('No exception'),
                    explanation: t('This transaction reconciled successfully between {origin} and {counterparty}.', {
                        origin: originSystem,
                        counterparty: counterpartySystem,
                    }),
                    nextAction: t('No action required unless the case is reopened.'),
                }
            case 'QUANTITY_MISMATCH':
                return {
                    label: presentation.exceptionLabels.quantityMismatch,
                    issueType: presentation.exceptionLabels.quantityMismatch,
                    explanation: t('The transaction matched at a business level, but the quantities do not align between {origin} and {counterparty}.', {
                        origin: originSystem,
                        counterparty: counterpartySystem,
                    }),
                    nextAction: t('Review the family and phase-specific quantity details before escalating.'),
                }
            case 'ITEM_MISSING':
                return {
                    label: presentation.exceptionLabels.itemMissing,
                    issueType: presentation.exceptionLabels.itemMissing,
                    explanation: t('One or more expected items are missing across the compared transaction payloads.'),
                    nextAction: t('Inspect the line details and verify item-level completeness.'),
                }
            default:
                break
        }
    }

    switch (normalized) {
        case 'MATCHED':
            return {
                label: presentation.summaryLabels.matched,
                issueType: t('No exception'),
                explanation: t('This transaction reconciled successfully across the {lane}.', {lane: laneTitle}),
                nextAction: t('No action required unless the case is reopened.'),
            }
        case 'QUANTITY_MISMATCH':
            return {
                label: presentation.exceptionLabels.quantityMismatch,
                issueType: presentation.exceptionLabels.quantityMismatch,
                explanation: t('Quantities do not align between the two systems for this transaction family scope.'),
                nextAction: t('Review the quantity details and investigate the variance pattern.'),
            }
        case 'ITEM_MISSING':
            return {
                label: presentation.exceptionLabels.itemMissing,
                issueType: presentation.exceptionLabels.itemMissing,
                explanation: t('One or more expected items are missing across the compared payloads.'),
                nextAction: t('Inspect the line details and verify item-level completeness.'),
            }
        case 'TOTAL_MISMATCH':
            return {
                label: presentation.exceptionLabels.statusConflict,
                issueType: presentation.exceptionLabels.statusConflict,
                explanation: t('The reconciliation state changed in a way that requires review before closing the case.'),
                nextAction: t('Review the latest processing history and confirm the correct business outcome.'),
            }
        default:
            break
    }

    if (normalized.startsWith('REVERTED_IN_') || normalized.startsWith('CORRECTED_IN_')) {
        return {
            label: presentation.exceptionLabels.statusConflict,
            issueType: presentation.exceptionLabels.statusConflict,
            explanation: t('The reconciliation state changed in a way that requires review before closing the case.'),
            nextAction: t('Review the latest processing history and confirm the correct business outcome.'),
        }
    }

    if (normalized.startsWith('MISSING_IN_')) {
        return {
            label: presentation.summaryLabels.missing,
            issueType: t('Missing Transaction'),
            explanation: t('The transaction is expected in the counterparty system but was not found.'),
            nextAction: t('Investigate the handoff and downstream processing path.'),
        }
    }
    if (normalized.startsWith('PROCESSING_PENDING_IN_')) {
        return {
            label: presentation.summaryLabels.pending,
            issueType: presentation.exceptionLabels.pending,
            explanation: t('The counterparty update was received and is still processing.'),
            nextAction: t('Monitor until the counterparty update completes and escalate if it breaches SLA.'),
        }
    }
    if (normalized.startsWith('AWAITING_')) {
        return {
            label: presentation.summaryLabels.awaiting,
            issueType: presentation.exceptionLabels.awaiting,
            explanation: t('The lane is waiting for the next business event needed to complete reconciliation.'),
            nextAction: t('Continue monitoring until the next business event is received.'),
        }
    }
    if (normalized.startsWith('DUPLICATE_IN_')) {
        return {
            label: presentation.exceptionLabels.duplicate,
            issueType: presentation.exceptionLabels.duplicate,
            explanation: t('Duplicate postings were detected for this business transaction.'),
            nextAction: t('Review duplicate history and route the case for exception handling if required.'),
        }
    }
    if (normalized.startsWith('PROCESSING_FAILED_IN_') || normalized === 'PROCESSING_FAILED') {
        return {
            label: presentation.exceptionLabels.statusConflict,
            issueType: t('Processing failure'),
            explanation: t('The counterparty update failed and the transaction requires manual follow-up.'),
            nextAction: t('Investigate the failure reason and reprocess or escalate as needed.'),
        }
    }

    return {
        label: normalized || t('Requires review'),
        issueType: t('Requires review'),
        explanation: t('This transaction needs review based on its current reconciliation state.'),
        nextAction: t('Open drill-down and exception workflow to continue investigation.'),
    }
}

export function getSiocsMfcsReconStatuses(selectedKpi, presentation) {
    const counterpartySystem = presentation?.counterpartySystem

    switch (selectedKpi) {
        case 'matched':
            return ['MATCHED']
        case 'missingInSiocs':
            return counterpartySystem
                ? [`MISSING_IN_${counterpartySystem}`]
                : ['MISSING_IN_MFCS', 'MISSING_IN_SIOCS']
        case 'processingPending':
        case 'pendingTotal':
            return counterpartySystem
                ? [`PROCESSING_PENDING_IN_${counterpartySystem}`]
                : ['PROCESSING_PENDING_IN_MFCS', 'PROCESSING_PENDING_IN_SIOCS']
        case 'awaitingSim':
            return counterpartySystem
                ? [`AWAITING_${counterpartySystem}`]
                : ['AWAITING_MFCS', 'AWAITING_SIOCS']
        case 'duplicateTransactions':
            return counterpartySystem
                ? [`DUPLICATE_IN_${counterpartySystem}`]
                : ['DUPLICATE_IN_MFCS', 'DUPLICATE_IN_SIOCS']
        case 'quantityMismatch':
            return ['QUANTITY_MISMATCH']
        case 'totalMismatch':
            return ['TOTAL_MISMATCH']
        case 'itemMissing':
            return ['ITEM_MISSING']
        default:
            return []
    }
}

export function getSiocsMfcsKpiDefinitions(presentation) {
    return [
        {
            title: presentation.summaryLabels.total,
            description: presentation.summaryDefinitions.total,
        },
        {
            title: presentation.summaryLabels.matched,
            description: presentation.summaryDefinitions.matched,
        },
        {
            title: presentation.summaryLabels.missing,
            description: presentation.summaryDefinitions.missing,
        },
        {
            title: presentation.summaryLabels.pending,
            description: presentation.summaryDefinitions.pending,
        },
        {
            title: presentation.summaryLabels.awaiting,
            description: presentation.summaryDefinitions.awaiting,
        },
        {
            title: presentation.summaryLabels.exceptions,
            description: presentation.summaryDefinitions.exceptions,
        },
    ]
}
