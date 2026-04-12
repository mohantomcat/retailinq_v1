import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material'
import ReadMoreOutlinedIcon from '@mui/icons-material/ReadMoreOutlined'
import {reconApi} from '../services/reconApi'
import {useAuth} from '../context/AuthContext'
import {exceptionApi} from '../services/exceptionApi'
import {
    buildSiocsMfcsPresentation,
    formatSiocsMfcsTransactionFamily,
    formatSiocsMfcsTransactionPhase,
    SIOCS_MFCS_SCOPE_MODE,
} from '../utils/siocsMfcsPresentation'
import {buildFixedInventoryPresentation, isFixedInventoryReconView} from '../utils/simRmsPresentation'
import {
    EXCEPTION_QUEUE_PREFILL_EVENT,
    EXCEPTION_QUEUE_PREFILL_KEY,
    TRANSACTION_DRILLDOWN_PREFILL_EVENT,
    TRANSACTION_DRILLDOWN_PREFILL_KEY,
} from '../constants/uiStateKeys'

function formatDateTimeValue(value) {
    if (!value) {
        return '-'
    }
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) {
        return value.replace('T', ' ')
    }
    return parsed.toLocaleString()
}

function formatMetricValue(value, digits = 2) {
    if (value === null || value === undefined || value === '') {
        return '-'
    }
    const numeric = Number(value)
    if (!Number.isFinite(numeric)) {
        return String(value)
    }
    return numeric.toLocaleString(undefined, {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
    })
}

function formatCurrencyValue(amount) {
    if (amount === null || amount === undefined || amount === '') {
        return '-'
    }
    const numeric = Number(amount)
    if (!Number.isFinite(numeric)) {
        return String(amount)
    }
    return numeric.toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    })
}

function statusChipStyles(status, palette) {
    switch ((status || '').toUpperCase()) {
        case 'MATCHED':
            return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
        case 'QUANTITY_MISMATCH':
        case 'TOTAL_MISMATCH':
        case 'ITEM_MISSING':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        default:
            return {backgroundColor: palette.blueChipBg, color: palette.blueChipText}
    }
}

function discrepancyChipStyles(item, palette) {
    if (item?.withinTolerance) {
        return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
    }
    switch ((item?.severityBand || '').toUpperCase()) {
        case 'HIGH':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'MEDIUM':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        default:
            return {backgroundColor: palette.blueChipBg, color: palette.blueChipText}
    }
}

function getSessionReconModules() {
    if (typeof window === 'undefined') {
        return []
    }
    try {
        const storedUser = window.sessionStorage.getItem('recon_user')
        const user = storedUser ? JSON.parse(storedUser) : null
        return Array.isArray(user?.accessibleModules) ? user.accessibleModules : []
    } catch {
        return []
    }
}

function getReconTabId(reconView) {
    return (
        getSessionReconModules().find((module) => module?.reconView === reconView)
            ?.tabId || null
    )
}

function getTargetSystemLabel(reconView) {
    return (
        getSessionReconModules().find((module) => module?.reconView === reconView)
            ?.targetSystem || 'Target'
    )
}

function getReconViewLabel(reconView) {
    return (
        getSessionReconModules().find((module) => module?.reconView === reconView)
            ?.label || reconView
    )
}

export default function TransactionDrillDown({palette, t, onOpenTab}) {
    const {hasPermission} = useAuth()
    const canViewExceptionQueue = hasPermission('EXCEPTION_QUEUE_VIEW')
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [selection, setSelection] = useState(null)
    const [transaction, setTransaction] = useState(null)
    const [exceptionCase, setExceptionCase] = useState(null)
    const [exceptionCaseAvailable, setExceptionCaseAvailable] = useState(false)

    const loadTransaction = async (prefill) => {
        if (!prefill?.transactionKey) {
            setSelection(null)
            setTransaction(null)
            setExceptionCase(null)
            setExceptionCaseAvailable(false)
            return
        }
        try {
            setLoading(true)
            setError('')
            setSelection(prefill)
            const [transactionResponse, caseResponse] = await Promise.all([
                reconApi.getTransaction(prefill.transactionKey, prefill.reconView || null),
                prefill.reconView
                    ? exceptionApi.getCase({
                        transactionKey: prefill.transactionKey,
                        reconView: prefill.reconView,
                    }).catch(() => null)
                    : Promise.resolve(null),
            ])
            setTransaction(transactionResponse)
            setExceptionCase(caseResponse)
            setExceptionCaseAvailable(!!caseResponse)
        } catch (err) {
            setError(err.message || 'Failed to load transaction drill-down')
            setTransaction(null)
            setExceptionCase(null)
            setExceptionCaseAvailable(false)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        const stored = sessionStorage.getItem(TRANSACTION_DRILLDOWN_PREFILL_KEY)
        if (stored) {
            try {
                loadTransaction(JSON.parse(stored))
            } catch {
                sessionStorage.removeItem(TRANSACTION_DRILLDOWN_PREFILL_KEY)
            }
        }

        const handlePrefill = (event) => {
            if (event.detail) {
                sessionStorage.setItem(TRANSACTION_DRILLDOWN_PREFILL_KEY, JSON.stringify(event.detail))
                loadTransaction(event.detail)
            }
        }

        window.addEventListener(TRANSACTION_DRILLDOWN_PREFILL_EVENT, handlePrefill)
        return () => window.removeEventListener(TRANSACTION_DRILLDOWN_PREFILL_EVENT, handlePrefill)
    }, [])

    const effectiveReconView = transaction?.reconView || selection?.reconView || ''
    const effectiveReconTabId = getReconTabId(effectiveReconView)
    const targetSystemLabel = getTargetSystemLabel(effectiveReconView)
    const isSiocsMfcsLane = effectiveReconView === 'SIOCS_MFCS'
    const isInventoryLane = isSiocsMfcsLane || isFixedInventoryReconView(effectiveReconView)
    const showInventoryValueMetrics = !isInventoryLane
    const metrics = useMemo(() => {
        if (!transaction) {
            return []
        }
        const next = [
            {label: t('Match Score'), value: transaction.matchScore ?? '-'},
            {label: t('Match Band'), value: transaction.matchBand || '-'},
            {label: t('Quantity Impact'), value: formatMetricValue(transaction.quantityImpact)},
            {label: t('Affected Items'), value: transaction.affectedItemCount ?? '-'},
        ]
        if (showInventoryValueMetrics) {
            next.splice(2, 0,
                {label: t('Transaction Amount'), value: formatCurrencyValue(transaction.transactionAmount)},
                {label: t('Amount Variance'), value: formatCurrencyValue(transaction.amountVariance)},
            )
        }
        return next
    }, [showInventoryValueMetrics, t, transaction])
    const inventoryPresentation = useMemo(() => {
        if (isSiocsMfcsLane) {
            return buildSiocsMfcsPresentation({
                selectedFamilies: transaction?.transactionFamily ? [transaction.transactionFamily] : [],
                t,
            })
        }
        if (isInventoryLane) {
            return buildFixedInventoryPresentation({
                reconView: effectiveReconView,
                selectedFamilies: transaction?.transactionFamily ? [transaction.transactionFamily] : [],
                selectedPhases: transaction?.transactionPhase ? [transaction.transactionPhase] : [],
                t,
            })
        }
        return null
    }, [
        isSiocsMfcsLane,
        isInventoryLane,
        effectiveReconView,
        t,
        transaction?.transactionFamily,
        transaction?.transactionPhase,
    ])
    const leftSystemLabel = isInventoryLane
        ? inventoryPresentation?.scopeMode === SIOCS_MFCS_SCOPE_MODE.RESOLVED
            ? inventoryPresentation.originSystem || t('Origin System')
            : t('SIOCS')
        : t('Xstore')
    const rightSystemLabel = isInventoryLane
        ? inventoryPresentation?.scopeMode === SIOCS_MFCS_SCOPE_MODE.RESOLVED
            ? inventoryPresentation.counterpartySystem || t('Counterparty System')
            : t('MFCS')
        : targetSystemLabel
    const transactionContextItems = useMemo(() => {
        if (!transaction) {
            return []
        }
        const items = [
            {label: t('Recon View'), value: getReconViewLabel(transaction.reconView)},
            {
                label: isInventoryLane ? t('Origin System') : t('Source'),
                value: isInventoryLane
                    ? (transaction.originSystem || transaction.simSource || '-')
                    : (transaction.simSource || '-'),
            },
            {label: t('Store'), value: transaction.storeId},
        ]

        if (isInventoryLane) {
            items.push(
                {
                    label: t('Transaction Family'),
                    value: transaction.transactionFamily
                        ? formatSiocsMfcsTransactionFamily(transaction.transactionFamily, t)
                        : '-',
                },
                {
                    label: t('Transaction Phase'),
                    value: transaction.transactionPhase
                        ? formatSiocsMfcsTransactionPhase(transaction.transactionPhase, t)
                        : '-',
                },
                {label: t('Transaction Type'), value: transaction.transactionType || '-'},
            )
        } else {
            items.push({label: t('Register'), value: transaction.wkstnId || '-'})
        }

        items.push(
            {label: t('Business Date'), value: transaction.businessDateDisplay || transaction.businessDate},
            {label: t('Reconciled At'), value: formatDateTimeValue(transaction.reconciledAt)},
            {label: t('Updated At'), value: formatDateTimeValue(transaction.updatedAt)},
            {label: t('Tolerance Profile'), value: transaction.toleranceProfile || '-'},
            {label: t('Match Rule'), value: transaction.matchRule || '-'},
            {label: t('Match Summary'), value: transaction.matchSummary || '-'},
            {
                label: t('{system} Checksum', {system: leftSystemLabel}),
                value: transaction.xstoreChecksum || '-',
            },
            {
                label: t('{system} Checksum', {system: rightSystemLabel}),
                value: transaction.siocsChecksum || '-',
            },
        )

        return items
    }, [isInventoryLane, leftSystemLabel, rightSystemLabel, t, transaction])
    const discrepancyLeftQuantityLabel = isInventoryLane
        ? t('{system} Qty', {system: leftSystemLabel})
        : t('Xstore Qty')
    const discrepancyRightQuantityLabel = isInventoryLane
        ? t('{system} Qty', {system: rightSystemLabel})
        : t('Target Qty')

    const openReconLane = () => {
        if (effectiveReconTabId) {
            onOpenTab?.(effectiveReconTabId)
        }
    }

    const openInExceptionQueue = () => {
        const effective = transaction || selection
        if (!effective || !canViewExceptionQueue) {
            return
        }
        const prefill = {
            reconView: effective.reconView || '',
            transactionKey: effective.transactionKey || '',
            search: effective.transactionKey || '',
        }
        sessionStorage.setItem(EXCEPTION_QUEUE_PREFILL_KEY, JSON.stringify(prefill))
        window.dispatchEvent(new CustomEvent(EXCEPTION_QUEUE_PREFILL_EVENT, {detail: prefill}))
        onOpenTab?.('exception-queues')
    }

    if (!selection && !transaction) {
        return (
            <Box sx={{px: 4, py: 3}} data-testid="transaction-drilldown-page">
                <Paper elevation={0} sx={{p: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                    <Typography sx={{fontSize: '1.2rem', fontWeight: 800, color: palette.text}}>
                        {t('Transaction Drill-down')}
                    </Typography>
                    <Typography sx={{mt: 0.7, fontSize: '0.9rem', color: palette.textMuted, maxWidth: 820}}>
                        {t('Select a KPI row and use "Open Transaction Drill-down" to inspect a single transaction without loading the full exception workflow inside the analytics screen.')}
                    </Typography>
                </Paper>
            </Box>
        )
    }

    return (
        <Box sx={{px: 4, py: 3, maxWidth: 1480}} data-testid="transaction-drilldown-page">
            <Paper elevation={0} sx={{p: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap'}}>
                    <Box>
                        <Stack direction="row" spacing={1.1} alignItems="center" sx={{mb: 0.8}} flexWrap="wrap">
                            <Box sx={{width: 42, height: 42, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.blueChipBg, color: palette.blueChipText}}>
                                <ReadMoreOutlinedIcon sx={{fontSize: 22}}/>
                            </Box>
                            <Typography sx={{fontSize: '1.25rem', fontWeight: 800, color: palette.text}}>
                                {t('Transaction Drill-down')}
                            </Typography>
                            {transaction?.reconStatus ? (
                                <Chip size="small" label={transaction.reconStatus} sx={{fontWeight: 700, ...statusChipStyles(transaction.reconStatus, palette)}}/>
                            ) : null}
                        </Stack>
                        <Typography sx={{fontSize: '0.9rem', color: palette.textMuted}}>
                            {transaction?.transactionKey || selection?.transactionKey || '-'}
                        </Typography>
                        <Typography sx={{mt: 0.4, fontSize: '0.8rem', color: palette.textMuted}}>
                            {(transaction?.storeId || '-') + ' | ' + (transaction?.businessDateDisplay || transaction?.businessDate || '-') + ' | ' + (transaction?.transactionType || '-')}
                        </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                            <Button variant="outlined" onClick={() => loadTransaction(selection)} disabled={loading}>
                                {t('Refresh')}
                            </Button>
                        {effectiveReconTabId ? (
                            <Button variant="outlined" data-testid="transaction-drilldown-open-recon-lane" onClick={openReconLane}>
                                {t('Open Recon Lane')}
                            </Button>
                        ) : null}
                        {canViewExceptionQueue ? (
                            <Button variant="outlined" data-testid="transaction-drilldown-open-exception-queue" onClick={openInExceptionQueue}>
                                {t('Open in Exception Queues')}
                            </Button>
                        ) : null}
                    </Stack>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mt: 2}}>{error}</Alert> : null}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : transaction ? (
                <>
                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))'}, gap: 1.5, mt: 3}}>
                        {metrics.map((item) => (
                            <Paper key={item.label} elevation={0} sx={{p: 2, borderRadius: '18px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted, fontWeight: 700}}>
                                    {item.label}
                                </Typography>
                                <Typography sx={{mt: 0.55, fontSize: '1.28rem', fontWeight: 800, color: palette.text}}>
                                    {item.value}
                                </Typography>
                            </Paper>
                        ))}
                    </Box>

                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', lg: '1.1fr 1fr'}, gap: 2, mt: 3}}>
                        <Paper elevation={0} sx={{p: 2.25, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 1.3}}>
                                {t('Transaction Context')}
                            </Typography>
                            <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.15}}>
                                {transactionContextItems.map((item) => (
                                    <Box key={item.label} sx={{p: 1.1, borderRadius: '14px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                        <Typography sx={{fontSize: '0.73rem', color: palette.textMuted, fontWeight: 700}}>
                                            {item.label}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.83rem', color: palette.text, fontWeight: 700, wordBreak: 'break-word'}}>
                                            {item.value || '-'}
                                        </Typography>
                                    </Box>
                                ))}
                            </Box>
                        </Paper>

                        <Paper elevation={0} sx={{p: 2.25, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 1.3}}>
                                {t('Related Exception Case')}
                            </Typography>

                            {exceptionCaseAvailable && exceptionCase ? (
                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.15}}>
                                    {[
                                        {label: t('Case Status'), value: exceptionCase.caseStatus || '-'},
                                        {label: t('Severity'), value: exceptionCase.severity || '-'},
                                        {label: t('Reason Code'), value: exceptionCase.reasonCode || '-'},
                                        {label: t('Assignee'), value: exceptionCase.assigneeUsername || '-'},
                                        {label: t('Assigned Team'), value: exceptionCase.assignedRoleName || '-'},
                                        {label: t('SLA Status'), value: exceptionCase.slaStatus || '-'},
                                        {label: t('Next Action'), value: exceptionCase.nextAction || '-'},
                                        {label: t('Playbook'), value: exceptionCase.playbookName || '-'},
                                    ].map((item) => (
                                        <Box key={item.label} sx={{p: 1.1, borderRadius: '14px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                            <Typography sx={{fontSize: '0.73rem', color: palette.textMuted, fontWeight: 700}}>
                                                {item.label}
                                            </Typography>
                                            <Typography sx={{mt: 0.45, fontSize: '0.84rem', color: palette.text, fontWeight: 700, wordBreak: 'break-word'}}>
                                                {item.value}
                                            </Typography>
                                        </Box>
                                    ))}
                                </Box>
                            ) : (
                                <Alert severity="info" sx={{borderRadius: 2}}>
                                    {t('No exception case exists yet for this transaction in the selected reconciliation lane.')}
                                </Alert>
                            )}
                        </Paper>

                        <Paper elevation={0} sx={{p: 2.25, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 1.3}}>
                                {t('Variance Signals')}
                            </Typography>
                            <Stack spacing={1}>
                                {[
                                    {label: t('Checksum Match'), value: transaction.checksumMatch ? t('Yes') : t('No')},
                                    {label: t('Tolerance Applied'), value: transaction.toleranceApplied ? t('Yes') : t('No')},
                                    {label: t('Quantity Variance %'), value: transaction.quantityVariancePercent !== null && transaction.quantityVariancePercent !== undefined ? `${formatMetricValue(transaction.quantityVariancePercent)}%` : '-'},
                                    {label: t('Matched Lines'), value: transaction.matchedLineCount ?? '-'},
                                    {label: t('Discrepant Lines'), value: transaction.discrepantLineCount ?? '-'},
                                    {label: t('Tolerated Discrepancies'), value: transaction.toleratedDiscrepancyCount ?? '-'},
                                    {label: t('Material Discrepancies'), value: transaction.materialDiscrepancyCount ?? '-'},
                                    ...(showInventoryValueMetrics ? [{
                                        label: t('Amount Variance %'),
                                        value: transaction.amountVariancePercent !== null && transaction.amountVariancePercent !== undefined
                                            ? `${formatMetricValue(transaction.amountVariancePercent)}%`
                                            : '-',
                                    }] : []),
                                ].map((item) => (
                                    <Box key={item.label} sx={{display: 'flex', justifyContent: 'space-between', gap: 2, p: 1.1, borderRadius: '14px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                        <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>{item.label}</Typography>
                                        <Typography sx={{fontSize: '0.82rem', color: palette.text, fontWeight: 700, textAlign: 'right'}}>{item.value}</Typography>
                                    </Box>
                                ))}
                            </Stack>
                        </Paper>
                    </Box>

                    <Paper elevation={0} sx={{mt: 3, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                        <Box sx={{px: 2.25, py: 1.8}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                {t('Line Discrepancies')}
                            </Typography>
                        </Box>
                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>{t('Item')}</TableCell>
                                        <TableCell>{t('Type')}</TableCell>
                                        <TableCell>{discrepancyLeftQuantityLabel}</TableCell>
                                        <TableCell>{discrepancyRightQuantityLabel}</TableCell>
                                        <TableCell>{t('Variance')}</TableCell>
                                        {showInventoryValueMetrics ? <TableCell>{t('Amount')}</TableCell> : null}
                                        <TableCell>{t('Severity')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {(transaction.discrepancies || []).length ? transaction.discrepancies.map((item, index) => (
                                        <TableRow key={`${item.itemId || 'item'}-${index}`} hover>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>
                                                    {item.itemId || '-'}
                                                </Typography>
                                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                                    {item.description || item.lineType || '-'}
                                                </Typography>
                                            </TableCell>
                                            <TableCell>{item.type || '-'}</TableCell>
                                            <TableCell>{formatMetricValue(item.xstoreQuantity)}</TableCell>
                                            <TableCell>{formatMetricValue(item.siocsQuantity)}</TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.8rem', color: palette.text}}>
                                                    {formatMetricValue(item.varianceQuantity)}
                                                </Typography>
                                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted}}>
                                                    {item.variancePercent !== null && item.variancePercent !== undefined ? `${formatMetricValue(item.variancePercent)}%` : '-'}
                                                </Typography>
                                            </TableCell>
                                            {showInventoryValueMetrics ? (
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.8rem', color: palette.text}}>
                                                        {formatCurrencyValue(item.varianceAmount)}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted}}>
                                                        {item.varianceAmountPercent !== null && item.varianceAmountPercent !== undefined ? `${formatMetricValue(item.varianceAmountPercent)}%` : '-'}
                                                    </Typography>
                                                </TableCell>
                                            ) : null}
                                            <TableCell>
                                                <Chip
                                                    size="small"
                                                    label={item.withinTolerance ? t('Within Tolerance') : (item.severityBand || t('Review'))}
                                                    sx={{fontWeight: 700, ...discrepancyChipStyles(item, palette)}}
                                                />
                                            </TableCell>
                                        </TableRow>
                                    )) : (
                                        <TableRow>
                                            <TableCell colSpan={showInventoryValueMetrics ? 7 : 6}>
                                                <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>
                                                    {t('No line discrepancies were captured for this transaction.')}
                                                </Typography>
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </Paper>
                </>
            ) : null}
        </Box>
    )
}
