import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Divider,
    FormControlLabel,
    MenuItem,
    Paper,
    Stack,
    Switch,
    TextField,
    Typography,
} from '@mui/material'
import KnownIssueGuidanceCard from './KnownIssueGuidanceCard'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'

const CASE_STATUS_OPTIONS = ['OPEN', 'IN_REVIEW', 'RESOLVED', 'IGNORED']
const SEVERITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const ROOT_CAUSE_CATEGORY_OPTIONS = [
    'INTEGRATION_TIMING',
    'DUPLICATE_PROCESSING',
    'ITEM_SYNC_GAP',
    'RECONCILIATION_VARIANCE',
    'CONFIGURATION_ISSUE',
    'SOURCE_DATA_ISSUE',
    'MANUAL_REVIEW',
]
const REASON_CODE_OPTIONS = [
    'REPLICATION_LAG',
    'DUPLICATE_SUBMISSION',
    'ITEM_SYNC_GAP',
    'QUANTITY_VARIANCE',
    'TOTAL_CALCULATION_MISMATCH',
    'CONFIGURATION_ISSUE',
    'SOURCE_DATA_ISSUE',
    'MANUAL_REVIEW_REQUIRED',
]

function getThemeMode() {
    return document.documentElement.getAttribute('data-recon-theme') === 'dark'
        ? 'Dark'
        : 'Light'
}

function getPalette(themeMode) {
    const isDark = themeMode === 'Dark'

    return {
        isDark,
        paperBg: isDark ? '#0F172A' : '#FFFFFF',
        cardBg: isDark ? '#111827' : '#F8FAFC',
        border: isDark ? '#1E293B' : '#E5E7EB',
        borderSoft: isDark ? '#243041' : '#F1F5F9',
        text: isDark ? '#E2E8F0' : '#0F172A',
        textMuted: isDark ? '#94A3B8' : '#64748B',
        blueBg: isDark ? '#0F172A' : '#EFF6FF',
        blueText: '#2563EB',
        tealBg: isDark ? '#0F172A' : '#ECFEFF',
        tealText: '#0F7C86',
    }
}

function deriveCategoryFromReason(reasonCode) {
    switch (reasonCode) {
        case 'REPLICATION_LAG':
            return 'INTEGRATION_TIMING'
        case 'DUPLICATE_SUBMISSION':
            return 'DUPLICATE_PROCESSING'
        case 'ITEM_SYNC_GAP':
            return 'ITEM_SYNC_GAP'
        case 'QUANTITY_VARIANCE':
        case 'TOTAL_CALCULATION_MISMATCH':
            return 'RECONCILIATION_VARIANCE'
        case 'CONFIGURATION_ISSUE':
            return 'CONFIGURATION_ISSUE'
        case 'SOURCE_DATA_ISSUE':
            return 'SOURCE_DATA_ISSUE'
        case 'MANUAL_REVIEW_REQUIRED':
            return 'MANUAL_REVIEW'
        default:
            return ''
    }
}

function getSuggestedClassification(reconStatus) {
    switch ((reconStatus || '').toUpperCase()) {
        case 'MISSING_IN_SIM':
        case 'MISSING_IN_SIOCS':
        case 'MISSING_IN_XOCS':
        case 'PROCESSING_PENDING_IN_SIM':
        case 'PROCESSING_PENDING_IN_SIOCS':
            return {
                reasonCode: 'REPLICATION_LAG',
                rootCauseCategory: 'INTEGRATION_TIMING',
            }
        case 'DUPLICATE_IN_SIM':
        case 'DUPLICATE_IN_SIOCS':
        case 'DUPLICATE_IN_XOCS':
            return {
                reasonCode: 'DUPLICATE_SUBMISSION',
                rootCauseCategory: 'DUPLICATE_PROCESSING',
            }
        case 'ITEM_MISSING':
            return {
                reasonCode: 'ITEM_SYNC_GAP',
                rootCauseCategory: 'ITEM_SYNC_GAP',
            }
        case 'QUANTITY_MISMATCH':
            return {
                reasonCode: 'QUANTITY_VARIANCE',
                rootCauseCategory: 'RECONCILIATION_VARIANCE',
            }
        case 'TOTAL_MISMATCH':
            return {
                reasonCode: 'TOTAL_CALCULATION_MISMATCH',
                rootCauseCategory: 'RECONCILIATION_VARIANCE',
            }
        default:
            return {
                reasonCode: '',
                rootCauseCategory: '',
            }
    }
}

function formatCurrencyValue(amount, currencyCode) {
    if (amount === null || amount === undefined || amount === '') {
        return '-'
    }
    const numeric = Number(amount)
    if (!Number.isFinite(numeric)) {
        return `${currencyCode ? `${currencyCode} ` : ''}${amount}`
    }
    if (!currencyCode) {
        return numeric.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})
    }
    try {
        return new Intl.NumberFormat(undefined, {
            style: 'currency',
            currency: currencyCode,
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        }).format(numeric)
    } catch {
        return `${currencyCode} ${numeric.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}`
    }
}

function formatDateTimeInputValue(value) {
    if (!value) {
        return ''
    }
    return value.includes('T') ? value.slice(0, 16) : value
}

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

function formatPercentValue(value) {
    if (value === null || value === undefined || value === '') {
        return '-'
    }
    return `${formatMetricValue(value, 2)}%`
}

function formatJsonSnapshot(snapshot) {
    if (!snapshot) {
        return '-'
    }
    try {
        return JSON.stringify(JSON.parse(snapshot), null, 2)
    } catch {
        return snapshot
    }
}

function matchBandStyles(matchBand, palette) {
    switch ((matchBand || '').toUpperCase()) {
        case 'EXACT':
            return {backgroundColor: palette.tealBg, color: palette.tealText}
        case 'TOLERATED':
            return {backgroundColor: '#FEF3C7', color: '#B45309'}
        case 'PARTIAL':
            return {backgroundColor: palette.blueBg, color: palette.blueText}
        default:
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
    }
}

function discrepancySeverityStyles(severityBand, withinTolerance, palette) {
    if (withinTolerance) {
        return {backgroundColor: palette.tealBg, color: palette.tealText}
    }
    switch ((severityBand || '').toUpperCase()) {
        case 'HIGH':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'MEDIUM':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        default:
            return {backgroundColor: palette.blueBg, color: palette.blueText}
    }
}

function ownershipChipStyles(status, palette) {
    switch ((status || '').toUpperCase()) {
        case 'UNOWNED':
        case 'OWNERSHIP_GAP':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'NO_NEXT_ACTION':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        case 'ACTION_OVERDUE':
            return {backgroundColor: '#FEF3C7', color: '#B45309'}
        case 'ACTION_DUE_SOON':
            return {backgroundColor: palette.blueBg, color: palette.blueText}
        default:
            return {backgroundColor: palette.tealBg, color: palette.tealText}
    }
}

function ownershipLabel(status, t) {
    switch ((status || '').toUpperCase()) {
        case 'UNOWNED':
            return t('Unowned')
        case 'NO_NEXT_ACTION':
            return t('No Next Action')
        case 'ACTION_OVERDUE':
            return t('Action Overdue')
        case 'ACTION_DUE_SOON':
            return t('Action Due Soon')
        case 'OWNERSHIP_GAP':
            return t('Ownership Gap')
        default:
            return t('On Track')
    }
}

export default function ExceptionWorkbenchPanel({record, onCaseSaved, refreshToken = 0}) {
    const [themeMode, setThemeMode] = useState(getThemeMode())
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [knownIssueFeedbackSaving, setKnownIssueFeedbackSaving] = useState(false)
    const [runningActionStepId, setRunningActionStepId] = useState(null)
    const [ticketingSaving, setTicketingSaving] = useState(false)
    const [communicationSaving, setCommunicationSaving] = useState(false)
    const [error, setError] = useState('')
    const [actionFeedback, setActionFeedback] = useState(null)
    const [caseData, setCaseData] = useState(null)
    const [caseStatus, setCaseStatus] = useState('OPEN')
    const [reasonCode, setReasonCode] = useState('')
    const [rootCauseCategory, setRootCauseCategory] = useState('')
    const [severity, setSeverity] = useState('MEDIUM')
    const [assigneeUsername, setAssigneeUsername] = useState('')
    const [assignedRoleName, setAssignedRoleName] = useState('')
    const [nextAction, setNextAction] = useState('')
    const [nextActionDueAt, setNextActionDueAt] = useState('')
    const [handoffNote, setHandoffNote] = useState('')
    const [notes, setNotes] = useState('')
    const [closureComment, setClosureComment] = useState('')
    const [reopenReason, setReopenReason] = useState('')
    const [captureAuditSnapshot, setCaptureAuditSnapshot] = useState(false)
    const [commentDraft, setCommentDraft] = useState('')
    const [ticketChannelId, setTicketChannelId] = useState('')
    const [ticketSummary, setTicketSummary] = useState('')
    const [ticketDescription, setTicketDescription] = useState('')
    const [communicationChannelId, setCommunicationChannelId] = useState('')
    const [communicationSubject, setCommunicationSubject] = useState('')
    const [communicationMessage, setCommunicationMessage] = useState('')
    const [workflowMessage, setWorkflowMessage] = useState('')
    const [assignmentOptions, setAssignmentOptions] = useState({usernames: [], roleNames: []})
    const {hasPermission, user} = useAuth()
    const {t} = useI18n()

    useEffect(() => {
        const observer = new MutationObserver(() => {
            setThemeMode(getThemeMode())
        })

        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-recon-theme'],
        })

        return () => observer.disconnect()
    }, [])

    const palette = useMemo(() => getPalette(themeMode), [themeMode])
    const canView = hasPermission('EXCEPTION_VIEW')
    const canEdit = hasPermission('EXCEPTION_EDIT')
    const canExecutePlaybookAction = hasPermission('OPS_EXECUTE_SAFE')
    const effectiveReconStatus = caseData?.reconStatus || record?.reconStatus || ''
    const matchDiscrepancies = Array.isArray(record?.discrepancies) ? record.discrepancies : []
    const hasMatchIntelligence = [
        record?.matchScore,
        record?.matchBand,
        record?.matchRule,
        record?.matchSummary,
        record?.toleranceProfile,
        record?.amountVariancePercent,
        record?.quantityVariancePercent,
    ].some((value) => value !== null && value !== undefined && value !== '')
        || matchDiscrepancies.length > 0
    const suggestedClassification = useMemo(
        () => getSuggestedClassification(effectiveReconStatus),
        [effectiveReconStatus]
    )

    const resetWorkbenchState = () => {
        setCaseData(null)
        setCaseStatus('OPEN')
        setReasonCode('')
        setRootCauseCategory('')
        setSeverity('MEDIUM')
        setAssigneeUsername('')
        setAssignedRoleName('')
        setNextAction('')
        setNextActionDueAt('')
        setHandoffNote('')
        setNotes('')
        setClosureComment('')
        setReopenReason('')
        setCaptureAuditSnapshot(false)
        setCommentDraft('')
        setTicketChannelId('')
        setTicketSummary('')
        setTicketDescription('')
        setCommunicationChannelId('')
        setCommunicationSubject('')
        setCommunicationMessage('')
        setWorkflowMessage('')
        setActionFeedback(null)
        setRunningActionStepId(null)
    }

    const applyCaseResponse = (response) => {
        setCaseData(response || null)
        setCaseStatus(response?.pendingApprovalRequest?.requestedCaseStatus || response?.caseStatus || 'OPEN')
        setReasonCode(response?.reasonCode || suggestedClassification.reasonCode || '')
        setRootCauseCategory(
            response?.rootCauseCategory
            || deriveCategoryFromReason(response?.reasonCode)
            || suggestedClassification.rootCauseCategory
            || ''
        )
        setSeverity(response?.severity || 'MEDIUM')
        setAssigneeUsername(response?.assigneeUsername || '')
        setAssignedRoleName(response?.assignedRoleName || '')
        setNextAction(response?.nextAction || '')
        setNextActionDueAt(formatDateTimeInputValue(response?.nextActionDueAt))
        setHandoffNote(response?.handoffNote || '')
        setNotes(response?.notes || '')
        setClosureComment('')
        setReopenReason('')
        setCaptureAuditSnapshot(false)
        setWorkflowMessage(response?.workflowMessage || '')
    }

    const reloadCase = async () => {
        if (!record?.transactionKey || !record?.reconView) {
            return null
        }
        const response = await exceptionApi.getCase({
            transactionKey: record.transactionKey,
            reconView: record.reconView,
        })
        applyCaseResponse(response)
        return response
    }

    useEffect(() => {
        if (!record?.transactionKey || !record?.reconView || !canView) {
            resetWorkbenchState()
            return
        }

        const load = async () => {
            setLoading(true)
            setError('')
            setActionFeedback(null)
            try {
                await reloadCase()
            } catch (err) {
                setError(err.message || t('Failed to load exception case'))
            } finally {
                setLoading(false)
            }
        }

        load()
    }, [record?.transactionKey, record?.reconView, canView, t, suggestedClassification, refreshToken])

    useEffect(() => {
        const ticketChannels = caseData?.ticketChannels || []
        const communicationChannels = caseData?.communicationChannels || []
        if (!ticketChannels.some((channel) => channel.id === ticketChannelId)) {
            setTicketChannelId(ticketChannels[0]?.id || '')
        }
        if (!communicationChannels.some((channel) => channel.id === communicationChannelId)) {
            setCommunicationChannelId(communicationChannels[0]?.id || '')
        }
    }, [caseData, ticketChannelId, communicationChannelId])

    useEffect(() => {
        if (!canView) {
            return
        }
        exceptionApi.getAssignmentOptions()
            .then((response) => setAssignmentOptions(response || {usernames: [], roleNames: []}))
            .catch(() => setAssignmentOptions({usernames: [], roleNames: []}))
    }, [canView])

    if (!record || !canView) {
        return null
    }

    const handleSave = async () => {
        setSaving(true)
        setError('')
        setActionFeedback(null)
        try {
            const response = await exceptionApi.saveCase({
                transactionKey: record.transactionKey,
                reconView: record.reconView,
                reconStatus: effectiveReconStatus,
                caseStatus,
                reasonCode,
                rootCauseCategory: rootCauseCategory || deriveCategoryFromReason(reasonCode) || suggestedClassification.rootCauseCategory,
                severity,
                assigneeUsername,
                assignedRoleName,
                nextAction,
                nextActionDueAt,
                handoffNote,
                storeId: record.storeId || '',
                wkstnId: record.wkstnId || '',
                businessDate: record.businessDate || '',
                notes,
                closureComment,
                reopenReason,
                captureAuditSnapshot,
            })
            applyCaseResponse(response)
            setClosureComment('')
            setReopenReason('')
            setCaptureAuditSnapshot(false)
            if (onCaseSaved) {
                await onCaseSaved(response)
            }
        } catch (err) {
            setError(err.message || t('Failed to save exception case'))
        } finally {
            setSaving(false)
        }
    }

    const reopeningCase = ['RESOLVED', 'IGNORED'].includes((caseData?.caseStatus || '').toUpperCase())
        && ['OPEN', 'IN_REVIEW'].includes((caseStatus || '').toUpperCase())

    const handleAddComment = async () => {
        if (!commentDraft.trim()) return
        setSaving(true)
        setError('')
        setActionFeedback(null)
        try {
            const response = await exceptionApi.addComment({
                transactionKey: record.transactionKey,
                reconView: record.reconView,
                commentText: commentDraft.trim(),
            })
            applyCaseResponse(response)
            setCommentDraft('')
            if (onCaseSaved) {
                await onCaseSaved(response)
            }
        } catch (err) {
            setError(err.message || t('Failed to add comment'))
        } finally {
            setSaving(false)
        }
    }

    const handleKnownIssueFeedback = async (helpful) => {
        if (!caseData?.matchedKnownIssue?.id) {
            return
        }
        setKnownIssueFeedbackSaving(true)
        setError('')
        setActionFeedback(null)
        try {
            const response = await exceptionApi.submitKnownIssueFeedback({
                knownIssueId: caseData.matchedKnownIssue.id,
                helpful,
                transactionKey: record.transactionKey,
                reconView: record.reconView,
                storeId: record.storeId || '',
                sourceView: 'WORKBENCH',
            })
            setCaseData((current) => current ? ({
                ...current,
                matchedKnownIssue: current.matchedKnownIssue ? {
                    ...current.matchedKnownIssue,
                    helpfulCount: response.helpfulCount,
                    notHelpfulCount: response.notHelpfulCount,
                } : current.matchedKnownIssue,
            }) : current)
            setActionFeedback({
                severity: 'success',
                message: response.message || t('Known issue feedback saved'),
            })
        } catch (err) {
            setError(err.message || t('Failed to save known issue feedback'))
        } finally {
            setKnownIssueFeedbackSaving(false)
        }
    }

    const handleExecutePlaybookStep = async (step) => {
        if (!step?.id || !record?.transactionKey || !record?.reconView) {
            return
        }
        setRunningActionStepId(step.id)
        setError('')
        setActionFeedback(null)
        try {
            const response = await exceptionApi.executePlaybookStep({
                transactionKey: record.transactionKey,
                reconView: record.reconView,
                stepId: step.id,
            })
            applyCaseResponse(response?.caseData || null)
            setActionFeedback({
                severity: response?.actionResult?.status === 'FAILED' ? 'error' : 'success',
                message: response?.actionResult?.message || t('Operation completed'),
            })
            if (onCaseSaved && response?.caseData) {
                await onCaseSaved(response.caseData)
            }
        } catch (err) {
            setError(err.message || t('Failed to execute playbook step'))
            try {
                const refreshed = await exceptionApi.getCase({
                    transactionKey: record.transactionKey,
                    reconView: record.reconView,
                })
                applyCaseResponse(refreshed)
            } catch {
                // Keep the original execution error surfaced.
            }
        } finally {
            setRunningActionStepId(null)
        }
    }

    const handleCreateExternalTicket = async () => {
        if (!ticketChannelId) {
            setError(t('Select a ticketing channel before creating an external ticket'))
            return
        }
        setTicketingSaving(true)
        setError('')
        setActionFeedback(null)
        try {
            const result = await exceptionApi.createCaseExternalTicket({
                transactionKey: record.transactionKey,
                reconView: record.reconView,
                channelId: ticketChannelId,
                ticketSummary: ticketSummary.trim(),
                ticketDescription: ticketDescription.trim(),
            })
            const refreshed = await reloadCase()
            setTicketSummary('')
            setTicketDescription('')
            setActionFeedback({
                severity: result?.deliveryStatus === 'FAILED' ? 'error' : 'success',
                message: result?.deliveryStatus === 'FAILED'
                    ? (result?.errorMessage || t('Ticket delivery failed'))
                    : (result?.externalReference ? `${t('External ticket created')}: ${result.externalReference}` : t('External ticket created')),
            })
            if (onCaseSaved && refreshed) {
                await onCaseSaved(refreshed)
            }
        } catch (err) {
            setError(err.message || t('Failed to create external ticket'))
        } finally {
            setTicketingSaving(false)
        }
    }

    const handleSendOperationalUpdate = async () => {
        if (!communicationChannelId) {
            setError(t('Select a communication channel before sending an update'))
            return
        }
        setCommunicationSaving(true)
        setError('')
        setActionFeedback(null)
        try {
            const result = await exceptionApi.sendCaseCommunication({
                transactionKey: record.transactionKey,
                reconView: record.reconView,
                channelId: communicationChannelId,
                subject: communicationSubject.trim(),
                messageBody: communicationMessage.trim(),
            })
            const refreshed = await reloadCase()
            setCommunicationSubject('')
            setCommunicationMessage('')
            setActionFeedback({
                severity: result?.deliveryStatus === 'FAILED' ? 'error' : 'success',
                message: result?.deliveryStatus === 'FAILED'
                    ? (result?.errorMessage || t('Operational update delivery failed'))
                    : t('Operational update sent'),
            })
            if (onCaseSaved && refreshed) {
                await onCaseSaved(refreshed)
            }
        } catch (err) {
            setError(err.message || t('Failed to send operational update'))
        } finally {
            setCommunicationSaving(false)
        }
    }

    return (
        <Paper
            elevation={0}
            sx={{
                mt: 2.5,
                p: 3,
                borderRadius: 4,
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.paperBg,
            }}
        >
            <Stack spacing={2.25}>
                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                        gap: 2,
                        flexWrap: 'wrap',
                    }}
                >
                    <Box>
                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                            {t('Exception Workbench')}
                        </Typography>
                        <Typography sx={{mt: 0.45, fontSize: '0.86rem', color: palette.textMuted}}>
                            {t('Manage ownership, severity, reason code, notes, and comments for the selected exception.')}
                        </Typography>
                    </Box>
                    <Stack direction="row" spacing={1}>
                        <Chip label={record.reconView} size="small" sx={{backgroundColor: palette.blueBg, color: palette.blueText, fontWeight: 700}} />
                        <Chip label={caseStatus} size="small" sx={{backgroundColor: palette.tealBg, color: palette.tealText, fontWeight: 700}} />
                        <Chip label={severity} size="small" sx={{backgroundColor: palette.cardBg, color: palette.text, fontWeight: 700, border: `1px solid ${palette.border}`}} />
                        {caseData?.ownershipStatus && (
                            <Chip
                                label={ownershipLabel(caseData.ownershipStatus, t)}
                                size="small"
                                sx={{fontWeight: 700, ...ownershipChipStyles(caseData.ownershipStatus, palette)}}
                            />
                        )}
                        {caseData?.approvalState === 'PENDING' && (
                            <Chip label={t('Pending Approval')} size="small" sx={{backgroundColor: '#FEF3C7', color: '#B45309', fontWeight: 700}} />
                        )}
                    </Stack>
                </Box>

                {error && <Alert severity="error" sx={{borderRadius: 3}}>{error}</Alert>}
                {!error && actionFeedback && (
                    <Alert severity={actionFeedback.severity} sx={{borderRadius: 3}}>
                        {actionFeedback.message}
                    </Alert>
                )}
                {!error && workflowMessage && <Alert severity="info" sx={{borderRadius: 3}}>{workflowMessage}</Alert>}

                <Paper
                    elevation={0}
                    sx={{
                        p: 2,
                        borderRadius: 3,
                        border: `1px solid ${palette.borderSoft}`,
                        backgroundColor: palette.cardBg,
                    }}
                >
                    <Stack spacing={0.6}>
                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                            {t('Selected record')}
                        </Typography>
                        <Typography sx={{fontSize: '0.9rem', color: palette.text}}>
                            {t('Transaction Key')}: {record.transactionKey}
                        </Typography>
                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                            {record.storeId || '-'} | {record.wkstnId || '-'} | {record.businessDateDisplay || record.businessDate || '-'}
                        </Typography>
                        {effectiveReconStatus && (
                            <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                {t('Reconciliation Status')}: {effectiveReconStatus}
                            </Typography>
                        )}
                        {(suggestedClassification.reasonCode || suggestedClassification.rootCauseCategory) && (
                            <Typography sx={{fontSize: '0.8rem', color: palette.blueText}}>
                                {t('Suggested classification')}: {suggestedClassification.rootCauseCategory || '-'} | {suggestedClassification.reasonCode || '-'}
                            </Typography>
                        )}
                        {(caseData?.routingRuleName || caseData?.playbookName) && (
                            <Typography sx={{fontSize: '0.8rem', color: palette.tealText}}>
                                {caseData?.routingRuleName
                                    ? `${t('Routing Rule')}: ${caseData.routingRuleName}`
                                    : `${t('Routing Rule')}: -`}
                                {' | '}
                                {caseData?.playbookName
                                    ? `${t('Playbook')}: ${caseData.playbookName}`
                                    : `${t('Playbook')}: -`}
                                {caseData?.autoAssigned ? ` | ${t('Auto-assigned')}` : ''}
                            </Typography>
                        )}
                        <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                            {caseData?.slaTargetMinutes
                                ? `${t('SLA')}: ${caseData.slaTargetMinutes} ${t('minutes')} | ${t('Status')}: ${caseData.slaStatus || '-'} | ${t('Due')}: ${caseData.dueAt || '-'}`
                                : t('SLA target will be applied when the case is first saved.')}
                        </Typography>
                        {caseData?.pendingApprovalRequest && (
                            <Typography sx={{fontSize: '0.8rem', color: '#B45309'}}>
                                {t('Pending approval')}: {caseData.pendingApprovalRequest.previousCaseStatus} → {caseData.pendingApprovalRequest.requestedCaseStatus} | {t('Approver Role')}: {caseData.pendingApprovalRequest.approverRoleName || '-'} | {t('Requested By')}: {caseData.pendingApprovalRequest.requestedBy || '-'}
                            </Typography>
                        )}
                    </Stack>
                </Paper>

                {hasMatchIntelligence && (
                    <Paper
                        elevation={0}
                        sx={{
                            p: 2,
                            borderRadius: 3,
                            border: `1px solid ${palette.borderSoft}`,
                            backgroundColor: palette.cardBg,
                        }}
                    >
                        <Stack spacing={1.25}>
                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, flexWrap: 'wrap', alignItems: 'center'}}>
                                <Box>
                                    <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                        {t('Match Intelligence')}
                                    </Typography>
                                    <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                        {t('Tolerance-aware scoring and variance breakdown from the reconciliation engine.')}
                                    </Typography>
                                </Box>
                                {record?.matchBand && (
                                    <Chip
                                        size="small"
                                        label={record.matchBand}
                                        sx={{fontWeight: 700, ...matchBandStyles(record.matchBand, palette)}}
                                    />
                                )}
                            </Box>

                            <Box
                                sx={{
                                    display: 'grid',
                                    gridTemplateColumns: {xs: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(4, minmax(0, 1fr))'},
                                    gap: 1.25,
                                }}
                            >
                                <Box>
                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                        {t('Match Score')}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.96rem', color: palette.text, fontWeight: 800}}>
                                        {record?.matchScore ?? '-'}
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                        {t('Match Rule')}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                        {record?.matchRule || '-'}
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                        {t('Tolerance Profile')}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                        {record?.toleranceProfile || '-'}
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                        {t('Tolerance Applied')}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                        {record?.toleranceApplied ? t('Yes') : t('No')}
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                        {t('Matched Lines')}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                        {record?.matchedLineCount ?? '-'}
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                        {t('Material Variances')}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                        {record?.materialDiscrepancyCount ?? '-'}
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                        {t('Quantity Variance')}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                        {formatPercentValue(record?.quantityVariancePercent)}
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                        {t('Amount Variance')}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                        {formatPercentValue(record?.amountVariancePercent)}
                                    </Typography>
                                </Box>
                            </Box>

                            {record?.matchSummary && (
                                <Typography sx={{fontSize: '0.82rem', color: palette.blueText, lineHeight: 1.55}}>
                                    {record.matchSummary}
                                </Typography>
                            )}

                            {matchDiscrepancies.length > 0 && (
                                <Stack spacing={1}>
                                    <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                        {t('Variance Drill-down')}
                                    </Typography>
                                    {matchDiscrepancies.map((discrepancy, index) => (
                                        <Paper
                                            key={`${discrepancy.itemId || discrepancy.type || 'variance'}-${index}`}
                                            elevation={0}
                                            sx={{
                                                p: 1.5,
                                                borderRadius: 3,
                                                border: `1px solid ${palette.borderSoft}`,
                                                backgroundColor: palette.paperBg,
                                            }}
                                        >
                                            <Stack spacing={0.8}>
                                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap', alignItems: 'center'}}>
                                                    <Typography sx={{fontSize: '0.88rem', color: palette.text, fontWeight: 700}}>
                                                        {discrepancy.itemId || discrepancy.lineType || t('Unmapped line')}
                                                    </Typography>
                                                    <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                        <Chip
                                                            size="small"
                                                            label={discrepancy.type || t('Variance')}
                                                            sx={{backgroundColor: palette.blueBg, color: palette.blueText, fontWeight: 700}}
                                                        />
                                                        <Chip
                                                            size="small"
                                                            label={discrepancy.withinTolerance ? t('Within Tolerance') : (discrepancy.severityBand || t('Material'))}
                                                            sx={{fontWeight: 700, ...discrepancySeverityStyles(discrepancy.severityBand, discrepancy.withinTolerance, palette)}}
                                                        />
                                                        {discrepancy.toleranceType && discrepancy.toleranceValue !== null && discrepancy.toleranceValue !== undefined && (
                                                            <Chip
                                                                size="small"
                                                                label={`${t('Tolerance')} ${discrepancy.toleranceType}: ${formatMetricValue(discrepancy.toleranceValue)}`}
                                                                sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}
                                                            />
                                                        )}
                                                    </Stack>
                                                </Box>
                                                <Typography sx={{fontSize: '0.8rem', color: palette.textMuted, lineHeight: 1.55}}>
                                                    {discrepancy.description || t('Variance detected during reconciliation comparison.')}
                                                </Typography>
                                                <Typography sx={{fontSize: '0.8rem', color: palette.text}}>
                                                    {t('Quantity')}: {formatMetricValue(discrepancy.xstoreQuantity)} / {formatMetricValue(discrepancy.siocsQuantity)}
                                                    {' | '}
                                                    {t('Variance')}: {formatMetricValue(discrepancy.varianceQuantity)}
                                                    {' | '}
                                                    {t('Variance %')}: {formatPercentValue(discrepancy.variancePercent)}
                                                </Typography>
                                                <Typography sx={{fontSize: '0.8rem', color: palette.text}}>
                                                    {t('Amount')}: {formatCurrencyValue(discrepancy.xstoreAmount, caseData?.businessValue?.currencyCode)} / {formatCurrencyValue(discrepancy.siocsAmount, caseData?.businessValue?.currencyCode)}
                                                    {' | '}
                                                    {t('Amount Variance')}: {formatCurrencyValue(discrepancy.varianceAmount, caseData?.businessValue?.currencyCode)}
                                                    {' | '}
                                                    {t('Amount Variance %')}: {formatPercentValue(discrepancy.varianceAmountPercent)}
                                                </Typography>
                                            </Stack>
                                        </Paper>
                                    ))}
                                </Stack>
                            )}
                        </Stack>
                    </Paper>
                )}

                <KnownIssueGuidanceCard
                    match={caseData?.matchedKnownIssue}
                    palette={palette}
                    t={t}
                    title={t('Guided Resolution')}
                    subtitle={t('Suggested cause and next step based on matching known-issue rules for this case.')}
                    onFeedback={handleKnownIssueFeedback}
                    feedbackSubmitting={knownIssueFeedbackSaving}
                />

                {caseData?.businessValue && (
                    <Paper
                        elevation={0}
                        sx={{
                            p: 2,
                            borderRadius: 3,
                            border: `1px solid ${palette.borderSoft}`,
                            backgroundColor: palette.cardBg,
                        }}
                    >
                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                            {t('Business Value')}
                        </Typography>
                        <Box
                            sx={{
                                mt: 1,
                                display: 'grid',
                                gridTemplateColumns: {xs: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(5, minmax(0, 1fr))'},
                                gap: 1.25,
                            }}
                        >
                            <Box>
                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                    {t('Value At Risk')}
                                </Typography>
                                <Typography sx={{fontSize: '0.94rem', color: palette.text, fontWeight: 800}}>
                                    {formatCurrencyValue(caseData.businessValue.valueAtRisk, caseData.businessValue.currencyCode)}
                                </Typography>
                            </Box>
                            <Box>
                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                    {t('Variance')}
                                </Typography>
                                <Typography sx={{fontSize: '0.94rem', color: palette.text, fontWeight: 800}}>
                                    {formatCurrencyValue(caseData.businessValue.amountVariance, caseData.businessValue.currencyCode)}
                                </Typography>
                            </Box>
                            <Box>
                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                    {t('Affected Items')}
                                </Typography>
                                <Typography sx={{fontSize: '0.94rem', color: palette.text, fontWeight: 800}}>
                                    {caseData.businessValue.affectedItemCount ?? '-'}
                                </Typography>
                            </Box>
                            <Box>
                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                    {t('Quantity Impact')}
                                </Typography>
                                <Typography sx={{fontSize: '0.94rem', color: palette.text, fontWeight: 800}}>
                                    {caseData.businessValue.quantityImpact ?? '-'}
                                </Typography>
                            </Box>
                            <Box>
                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                    {t('Customer Impact')}
                                </Typography>
                                <Typography sx={{fontSize: '0.94rem', color: palette.text, fontWeight: 800}}>
                                    {caseData.businessValue.customerImpact || '-'}
                                </Typography>
                            </Box>
                        </Box>
                        {caseData.businessValue.summary ? (
                            <Typography sx={{mt: 1.15, fontSize: '0.8rem', color: palette.blueText}}>
                                {caseData.businessValue.summary}
                            </Typography>
                        ) : null}
                    </Paper>
                )}

                <Paper
                    elevation={0}
                    sx={{
                        p: 2,
                        borderRadius: 3,
                        border: `1px solid ${palette.borderSoft}`,
                        backgroundColor: palette.cardBg,
                    }}
                >
                    <Stack spacing={1.5}>
                        <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                            <Box>
                                <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                    {t('Ticketing & Communication')}
                                </Typography>
                                <Typography sx={{mt: 0.4, fontSize: '0.82rem', color: palette.textMuted}}>
                                    {t('Create an external support ticket or send an operational update without leaving the case workbench.')}
                                </Typography>
                            </Box>
                            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                <Chip size="small" label={`${t('Tickets')}: ${(caseData?.externalTickets || []).length}`} sx={{backgroundColor: palette.blueBg, color: palette.blueText, fontWeight: 700}}/>
                                <Chip size="small" label={`${t('Updates')}: ${(caseData?.communications || []).length}`} sx={{backgroundColor: palette.tealBg, color: palette.tealText, fontWeight: 700}}/>
                            </Stack>
                        </Box>

                        {canEdit ? (
                            <Box
                                sx={{
                                    display: 'grid',
                                    gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'},
                                    gap: 1.5,
                                }}
                            >
                                <Paper
                                    elevation={0}
                                    sx={{
                                        p: 1.5,
                                        borderRadius: 3,
                                        border: `1px solid ${palette.border}`,
                                        backgroundColor: palette.paperBg,
                                    }}
                                >
                                    <Stack spacing={1.1}>
                                        <Typography sx={{fontSize: '0.88rem', fontWeight: 800, color: palette.text}}>
                                            {t('Create External Ticket')}
                                        </Typography>
                                        {(caseData?.ticketChannels || []).length > 0 ? (
                                            <>
                                                <TextField
                                                    select
                                                    size="small"
                                                    label={t('Ticket Channel')}
                                                    value={ticketChannelId}
                                                    onChange={(event) => setTicketChannelId(event.target.value)}
                                                    disabled={ticketingSaving || saving}
                                                    fullWidth
                                                >
                                                    {(caseData?.ticketChannels || []).map((channel) => (
                                                        <MenuItem key={channel.id} value={channel.id}>
                                                            {channel.channelName} | {channel.channelType}
                                                        </MenuItem>
                                                    ))}
                                                </TextField>
                                                <TextField
                                                    size="small"
                                                    label={t('Ticket Summary')}
                                                    value={ticketSummary}
                                                    onChange={(event) => setTicketSummary(event.target.value)}
                                                    disabled={ticketingSaving || saving}
                                                    placeholder={`${record.storeId || t('Store')} | ${effectiveReconStatus || t('Exception')} | ${record.transactionKey}`}
                                                    fullWidth
                                                />
                                                <TextField
                                                    size="small"
                                                    label={t('Ticket Description')}
                                                    value={ticketDescription}
                                                    onChange={(event) => setTicketDescription(event.target.value)}
                                                    disabled={ticketingSaving || saving}
                                                    multiline
                                                    minRows={3}
                                                    fullWidth
                                                    helperText={t('Leave the fields blank if you want the system to generate the ticket summary and description.')}
                                                />
                                                <Button
                                                    variant="contained"
                                                    onClick={handleCreateExternalTicket}
                                                    disabled={ticketingSaving || saving}
                                                    sx={{alignSelf: 'flex-start', textTransform: 'none', fontWeight: 700}}
                                                >
                                                    {ticketingSaving ? t('Creating...') : t('Create Ticket')}
                                                </Button>
                                            </>
                                        ) : (
                                            <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                                {t('No ticketing channels are configured for this module yet.')}
                                            </Typography>
                                        )}
                                    </Stack>
                                </Paper>

                                <Paper
                                    elevation={0}
                                    sx={{
                                        p: 1.5,
                                        borderRadius: 3,
                                        border: `1px solid ${palette.border}`,
                                        backgroundColor: palette.paperBg,
                                    }}
                                >
                                    <Stack spacing={1.1}>
                                        <Typography sx={{fontSize: '0.88rem', fontWeight: 800, color: palette.text}}>
                                            {t('Send Operational Update')}
                                        </Typography>
                                        {(caseData?.communicationChannels || []).length > 0 ? (
                                            <>
                                                <TextField
                                                    select
                                                    size="small"
                                                    label={t('Communication Channel')}
                                                    value={communicationChannelId}
                                                    onChange={(event) => setCommunicationChannelId(event.target.value)}
                                                    disabled={communicationSaving || saving}
                                                    fullWidth
                                                >
                                                    {(caseData?.communicationChannels || []).map((channel) => (
                                                        <MenuItem key={channel.id} value={channel.id}>
                                                            {channel.channelName} | {channel.channelType}
                                                        </MenuItem>
                                                    ))}
                                                </TextField>
                                                <TextField
                                                    size="small"
                                                    label={t('Subject')}
                                                    value={communicationSubject}
                                                    onChange={(event) => setCommunicationSubject(event.target.value)}
                                                    disabled={communicationSaving || saving}
                                                    placeholder={`${record.storeId || t('Store')} | ${record.reconView} | ${t('Operational update')}`}
                                                    fullWidth
                                                />
                                                <TextField
                                                    size="small"
                                                    label={t('Message')}
                                                    value={communicationMessage}
                                                    onChange={(event) => setCommunicationMessage(event.target.value)}
                                                    disabled={communicationSaving || saving}
                                                    multiline
                                                    minRows={3}
                                                    fullWidth
                                                    helperText={t('Leave the fields blank if you want the system to generate the default update content.')}
                                                />
                                                <Button
                                                    variant="contained"
                                                    onClick={handleSendOperationalUpdate}
                                                    disabled={communicationSaving || saving}
                                                    sx={{alignSelf: 'flex-start', textTransform: 'none', fontWeight: 700}}
                                                >
                                                    {communicationSaving ? t('Sending...') : t('Send Update')}
                                                </Button>
                                            </>
                                        ) : (
                                            <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                                {t('No communication channels are configured for this module yet.')}
                                            </Typography>
                                        )}
                                    </Stack>
                                </Paper>
                            </Box>
                        ) : (
                            <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                {t('You have view access to collaboration activity, but sending updates and creating tickets requires exception edit permission.')}
                            </Typography>
                        )}

                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'},
                                gap: 1.5,
                            }}
                        >
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 1.5,
                                    borderRadius: 3,
                                    border: `1px solid ${palette.border}`,
                                    backgroundColor: palette.paperBg,
                                }}
                            >
                                <Typography sx={{fontSize: '0.84rem', fontWeight: 800, color: palette.text}}>
                                    {t('External Ticket History')}
                                </Typography>
                                <Stack spacing={1} sx={{mt: 1.1}}>
                                    {(caseData?.externalTickets || []).length > 0 ? (caseData.externalTickets || []).map((ticket) => (
                                        <Paper key={ticket.id} elevation={0} sx={{p: 1.2, borderRadius: 2.5, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBg}}>
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 260px'}}>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text, fontWeight: 700}}>
                                                        {ticket.ticketSummary || t('Ticket created')}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.3, fontSize: '0.76rem', color: palette.textMuted}}>
                                                        {ticket.channelName} | {ticket.channelType}
                                                    </Typography>
                                                    {ticket.externalReference ? (
                                                        <Typography sx={{mt: 0.3, fontSize: '0.76rem', color: palette.blueText}}>
                                                            {t('Reference')}: {ticket.externalReference}
                                                        </Typography>
                                                    ) : null}
                                                    {ticket.externalStatus ? (
                                                        <Typography sx={{mt: 0.3, fontSize: '0.76rem', color: palette.text}}>
                                                            {t('External Status')}: {ticket.externalStatus}
                                                        </Typography>
                                                    ) : null}
                                                    {ticket.lastExternalComment ? (
                                                        <Typography sx={{mt: 0.3, fontSize: '0.74rem', color: palette.textMuted}}>
                                                            {ticket.lastExternalComment}
                                                        </Typography>
                                                    ) : null}
                                                    {ticket.errorMessage ? (
                                                        <Typography sx={{mt: 0.3, fontSize: '0.74rem', color: '#B91C1C'}}>
                                                            {ticket.errorMessage}
                                                        </Typography>
                                                    ) : null}
                                                </Box>
                                                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                    <Chip size="small" label={ticket.deliveryStatus || 'CREATED'} sx={{backgroundColor: ticket.deliveryStatus === 'FAILED' ? '#FEF2F2' : palette.blueBg, color: ticket.deliveryStatus === 'FAILED' ? '#B91C1C' : palette.blueText, fontWeight: 700}}/>
                                                    {ticket.externalUrl ? (
                                                        <Button size="small" href={ticket.externalUrl} target="_blank" rel="noreferrer" sx={{textTransform: 'none'}}>
                                                            {t('Open')}
                                                        </Button>
                                                    ) : null}
                                                </Stack>
                                            </Box>
                                            <Typography sx={{mt: 0.7, fontSize: '0.72rem', color: palette.textMuted}}>
                                                {ticket.createdBy || t('System')} | {formatDateTimeValue(ticket.createdAt)} | {t('Last Sync')}: {formatDateTimeValue(ticket.lastExternalUpdateAt || ticket.lastSyncedAt)}
                                            </Typography>
                                        </Paper>
                                    )) : (
                                        <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                            {t('No external tickets have been created for this case yet.')}
                                        </Typography>
                                    )}
                                </Stack>
                            </Paper>

                            <Paper
                                elevation={0}
                                sx={{
                                    p: 1.5,
                                    borderRadius: 3,
                                    border: `1px solid ${palette.border}`,
                                    backgroundColor: palette.paperBg,
                                }}
                            >
                                <Typography sx={{fontSize: '0.84rem', fontWeight: 800, color: palette.text}}>
                                    {t('Communication History')}
                                </Typography>
                                <Stack spacing={1} sx={{mt: 1.1}}>
                                    {(caseData?.communications || []).length > 0 ? (caseData.communications || []).map((entry) => (
                                        <Paper key={entry.id} elevation={0} sx={{p: 1.2, borderRadius: 2.5, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBg}}>
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 260px'}}>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text, fontWeight: 700}}>
                                                        {entry.subject || t('Operational update')}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.3, fontSize: '0.76rem', color: palette.textMuted}}>
                                                        {entry.channelName} | {entry.channelType}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.3, fontSize: '0.76rem', color: palette.text}}>
                                                        {entry.recipient || t('Webhook delivery')}
                                                    </Typography>
                                                    {entry.errorMessage ? (
                                                        <Typography sx={{mt: 0.3, fontSize: '0.74rem', color: '#B91C1C'}}>
                                                            {entry.errorMessage}
                                                        </Typography>
                                                    ) : null}
                                                </Box>
                                                <Chip size="small" label={entry.deliveryStatus || 'SENT'} sx={{backgroundColor: entry.deliveryStatus === 'FAILED' ? '#FEF2F2' : palette.tealBg, color: entry.deliveryStatus === 'FAILED' ? '#B91C1C' : palette.tealText, fontWeight: 700}}/>
                                            </Box>
                                            <Typography sx={{mt: 0.7, fontSize: '0.72rem', color: palette.textMuted}}>
                                                {entry.createdBy || t('System')} | {formatDateTimeValue(entry.createdAt)}
                                            </Typography>
                                        </Paper>
                                    )) : (
                                        <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                            {t('No operational updates have been sent for this case yet.')}
                                        </Typography>
                                    )}
                                </Stack>
                            </Paper>
                        </Box>
                    </Stack>
                </Paper>

                {loading ? (
                    <Box sx={{display: 'flex', justifyContent: 'center', py: 2}}>
                        <CircularProgress size={24} />
                    </Box>
                ) : (
                    <>
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(5, minmax(0, 1fr))'},
                                gap: 2,
                            }}
                        >
                            <TextField
                                select
                                size="small"
                                label={t('Case Status')}
                                value={caseStatus}
                                onChange={(e) => setCaseStatus(e.target.value)}
                                disabled={!canEdit || saving}
                            >
                                {CASE_STATUS_OPTIONS.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                select
                                size="small"
                                label={t('Root Cause Category')}
                                value={rootCauseCategory}
                                onChange={(e) => setRootCauseCategory(e.target.value)}
                                disabled={!canEdit || saving}
                            >
                                <MenuItem value="">{t('None')}</MenuItem>
                                {ROOT_CAUSE_CATEGORY_OPTIONS.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                select
                                size="small"
                                label={t('Reason Code')}
                                value={reasonCode}
                                onChange={(e) => {
                                    const nextReasonCode = e.target.value
                                    setReasonCode(nextReasonCode)
                                    setRootCauseCategory((current) => current || deriveCategoryFromReason(nextReasonCode))
                                }}
                                disabled={!canEdit || saving}
                            >
                                <MenuItem value="">{t('None')}</MenuItem>
                                {REASON_CODE_OPTIONS.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                select
                                size="small"
                                label={t('Severity')}
                                value={severity}
                                onChange={(e) => setSeverity(e.target.value)}
                                disabled={!canEdit || saving}
                            >
                                {SEVERITY_OPTIONS.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                select
                                size="small"
                                label={t('Assignee')}
                                value={assigneeUsername}
                                onChange={(e) => setAssigneeUsername(e.target.value)}
                                disabled={!canEdit || saving}
                            >
                                <MenuItem value="">{t('None')}</MenuItem>
                                {assignmentOptions.usernames.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                        </Box>

                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: {xs: '1fr', md: 'minmax(0, 1fr) minmax(0, 1.2fr)'},
                                gap: 2,
                            }}
                        >
                            <TextField
                                select
                                size="small"
                                label={t('Assigned Team')}
                                value={assignedRoleName}
                                onChange={(e) => setAssignedRoleName(e.target.value)}
                                disabled={!canEdit || saving}
                            >
                                <MenuItem value="">{t('None')}</MenuItem>
                                {assignmentOptions.roleNames.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 1.5,
                                    borderRadius: 3,
                                    border: `1px solid ${palette.borderSoft}`,
                                    backgroundColor: palette.cardBg,
                                }}
                            >
                                <Typography
                                    sx={{
                                        fontSize: '0.76rem',
                                        color: palette.textMuted,
                                        textTransform: 'uppercase',
                                        fontWeight: 700,
                                        letterSpacing: '0.04em',
                                    }}
                                >
                                    {t('Transaction Scope')}
                                </Typography>
                                <Box
                                    sx={{
                                        mt: 1,
                                        display: 'grid',
                                        gridTemplateColumns: {
                                            xs: '1fr',
                                            sm: 'repeat(3, minmax(0, 1fr))',
                                        },
                                        gap: 1,
                                    }}
                                >
                                    <Box>
                                        <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                            {t('Store')}
                                        </Typography>
                                        <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                            {record.storeId || '-'}
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                            {t('Register')}
                                        </Typography>
                                        <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                            {record.wkstnId || '-'}
                                        </Typography>
                                    </Box>
                                    <Box>
                                        <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                            {t('Business Date')}
                                        </Typography>
                                        <Typography sx={{fontSize: '0.9rem', color: palette.text, fontWeight: 700}}>
                                            {record.businessDateDisplay || record.businessDate || '-'}
                                        </Typography>
                                    </Box>
                                </Box>
                            </Paper>
                        </Box>

                        <Paper
                            elevation={0}
                            sx={{
                                p: 2,
                                borderRadius: 3,
                                border: `1px solid ${palette.borderSoft}`,
                                backgroundColor: palette.cardBg,
                            }}
                        >
                            <Stack spacing={1.5}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.25, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                    <Box>
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                            {t('Operational Ownership')}
                                        </Typography>
                                        <Typography sx={{mt: 0.4, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('Capture what the next shift or support team must do, by when, and any handoff context they need.')}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        size="small"
                                        label={ownershipLabel(caseData?.ownershipStatus, t)}
                                        sx={{fontWeight: 700, ...ownershipChipStyles(caseData?.ownershipStatus, palette)}}
                                    />
                                </Box>

                                <Box
                                    sx={{
                                        display: 'grid',
                                        gridTemplateColumns: {xs: '1fr', md: 'minmax(0, 1.5fr) minmax(0, 1fr)'},
                                        gap: 1.5,
                                    }}
                                >
                                    <TextField
                                        size="small"
                                        label={t('Next Action')}
                                        value={nextAction}
                                        onChange={(e) => setNextAction(e.target.value)}
                                        disabled={!canEdit || saving}
                                        fullWidth
                                    />
                                    <TextField
                                        size="small"
                                        label={t('Action Due By')}
                                        type="datetime-local"
                                        value={nextActionDueAt}
                                        onChange={(e) => setNextActionDueAt(e.target.value)}
                                        disabled={!canEdit || saving}
                                        fullWidth
                                        InputLabelProps={{shrink: true}}
                                    />
                                </Box>

                                <TextField
                                    size="small"
                                    label={t('Shift Handoff Note')}
                                    value={handoffNote}
                                    onChange={(e) => setHandoffNote(e.target.value)}
                                    disabled={!canEdit || saving}
                                    multiline
                                    minRows={2}
                                    fullWidth
                                    helperText={t('This is stored on the case and logged in the timeline when ownership details change.')}
                                />

                                <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                    {caseData?.lastHandoffAt
                                        ? `${t('Last handoff')}: ${caseData.lastHandoffBy || t('System')} | ${formatDateTimeValue(caseData.lastHandoffAt)}`
                                        : t('No shift handoff recorded yet.')}
                                </Typography>
                            </Stack>
                        </Paper>

                        <TextField
                            label={t('Resolution Notes')}
                            value={notes}
                            onChange={(e) => setNotes(e.target.value)}
                            disabled={!canEdit || saving}
                            multiline
                            minRows={3}
                            fullWidth
                        />

                        <TextField
                            label={t('Closure Comment')}
                            value={closureComment}
                            onChange={(e) => setClosureComment(e.target.value)}
                            disabled={!canEdit || saving}
                            multiline
                            minRows={2}
                            fullWidth
                            helperText={t('Use this when a workflow policy requires an approval or closure comment.')}
                        />

                        <Paper
                            elevation={0}
                            sx={{
                                p: 2,
                                borderRadius: 3,
                                border: `1px solid ${palette.borderSoft}`,
                                backgroundColor: palette.cardBg,
                            }}
                        >
                            <Stack spacing={1.5}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.25, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                    <Box>
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                            {t('Escalation And Reopen')}
                                        </Typography>
                                        <Typography sx={{mt: 0.4, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('Capture explicit reopen context and see whether the case is currently escalated by policy.')}
                                        </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                        <Chip
                                            size="small"
                                            label={caseData?.escalationState || t('NONE')}
                                            sx={{fontWeight: 700, ...(caseData?.escalationState === 'ESCALATED' ? {backgroundColor: '#FEF2F2', color: '#B91C1C'} : {backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`})}}
                                        />
                                        {caseData?.reopenCount ? (
                                            <Chip
                                                size="small"
                                                label={`${t('Reopened')}: ${caseData.reopenCount}`}
                                                sx={{backgroundColor: palette.blueBg, color: palette.blueText, fontWeight: 700}}
                                            />
                                        ) : null}
                                    </Stack>
                                </Box>

                                <Typography sx={{fontSize: '0.8rem', color: palette.text}}>
                                    {caseData?.escalationReason || t('No active escalation on this case.')}
                                </Typography>
                                {caseData?.escalationPolicyName ? (
                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                        {t('Escalation Policy')}: {caseData.escalationPolicyName}
                                    </Typography>
                                ) : null}
                                {(caseData?.lastEscalatedAt || caseData?.lastReopenedAt) && (
                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                        {caseData?.lastEscalatedAt ? `${t('Last escalated')}: ${caseData.lastEscalatedBy || t('System')} | ${formatDateTimeValue(caseData.lastEscalatedAt)}` : ''}
                                        {caseData?.lastEscalatedAt && caseData?.lastReopenedAt ? ' | ' : ''}
                                        {caseData?.lastReopenedAt ? `${t('Last reopened')}: ${caseData.lastReopenedBy || t('System')} | ${formatDateTimeValue(caseData.lastReopenedAt)}` : ''}
                                    </Typography>
                                )}
                                {caseData?.reopenReason ? (
                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                        {t('Latest Reopen Reason')}: {caseData.reopenReason}
                                    </Typography>
                                ) : null}

                                {reopeningCase && (
                                    <TextField
                                        label={t('Reopen Reason')}
                                        value={reopenReason}
                                        onChange={(e) => setReopenReason(e.target.value)}
                                        disabled={!canEdit || saving}
                                        multiline
                                        minRows={2}
                                        fullWidth
                                        helperText={t('Required when moving a resolved or ignored case back into active work.')}
                                    />
                                )}

                                <FormControlLabel
                                    control={<Switch checked={captureAuditSnapshot} onChange={(event) => setCaptureAuditSnapshot(event.target.checked)} disabled={!canEdit || saving}/>}
                                    label={t('Capture before/after audit snapshot for sensitive field changes')}
                                />
                            </Stack>
                        </Paper>

                        {caseData?.recommendedPlaybook?.steps?.length > 0 && (
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 2,
                                    borderRadius: 3,
                                    border: `1px solid ${palette.borderSoft}`,
                                    backgroundColor: palette.cardBg,
                                }}
                            >
                                <Stack spacing={1.2}>
                                    <Box>
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                            {t('Recommended Playbook')}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.95rem', color: palette.text, fontWeight: 800}}>
                                            {caseData.recommendedPlaybook.playbookName}
                                        </Typography>
                                        {caseData.recommendedPlaybook.description && (
                                            <Typography sx={{mt: 0.4, fontSize: '0.82rem', color: palette.textMuted}}>
                                                {caseData.recommendedPlaybook.description}
                                            </Typography>
                                        )}
                                    </Box>
                                    <Stack spacing={1}>
                                        {caseData.recommendedPlaybook.steps.map((step) => (
                                            <Paper
                                                key={step.id || `${step.stepOrder}-${step.stepTitle}`}
                                                elevation={0}
                                                sx={{
                                                    p: 1.35,
                                                    borderRadius: 2.5,
                                                    border: `1px solid ${palette.border}`,
                                                    backgroundColor: palette.paperBg,
                                                }}
                                            >
                                                <Typography sx={{fontSize: '0.84rem', color: palette.text, fontWeight: 700}}>
                                                    {step.stepOrder}. {step.stepTitle}
                                                </Typography>
                                                {step.stepDetail && (
                                                    <Typography sx={{mt: 0.35, fontSize: '0.8rem', color: palette.textMuted, lineHeight: 1.5}}>
                                                        {step.stepDetail}
                                                    </Typography>
                                                )}
                                                {step.actionConfigured && (
                                                    <Stack
                                                        direction={{xs: 'column', md: 'row'}}
                                                        justifyContent="space-between"
                                                        alignItems={{xs: 'flex-start', md: 'center'}}
                                                        spacing={1.25}
                                                        sx={{mt: 1.1}}
                                                    >
                                                        <Box>
                                                            <Typography sx={{fontSize: '0.76rem', color: palette.blueText, fontWeight: 700}}>
                                                                {t('Linked operation')}: {step.operationModuleId || '-'} / {step.operationActionKey || '-'}
                                                            </Typography>
                                                            {step.actionSupportMessage && (
                                                                <Typography sx={{mt: 0.25, fontSize: '0.74rem', color: palette.textMuted}}>
                                                                    {step.actionSupportMessage}
                                                                </Typography>
                                                            )}
                                                        </Box>
                                                        {step.actionExecutable ? (
                                                            canExecutePlaybookAction ? (
                                                                <Button
                                                                    size="small"
                                                                    variant="contained"
                                                                    onClick={() => handleExecutePlaybookStep(step)}
                                                                    disabled={saving || runningActionStepId === step.id}
                                                                    sx={{textTransform: 'none', fontWeight: 700}}
                                                                >
                                                                    {runningActionStepId === step.id ? t('Running...') : t('Run Action')}
                                                                </Button>
                                                            ) : (
                                                                <Chip
                                                                    size="small"
                                                                    label={t('Requires OPS_EXECUTE_SAFE')}
                                                                    sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}
                                                                />
                                                            )
                                                        ) : (
                                                            <Chip
                                                                size="small"
                                                                label={step.actionExecutionMode === 'ADVANCED' ? t('Use Operations Console') : t('Not Executable')}
                                                                sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}
                                                            />
                                                        )}
                                                    </Stack>
                                                )}
                                            </Paper>
                                        ))}
                                    </Stack>
                                </Stack>
                            </Paper>
                        )}

                        <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={2}>
                            <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                {caseData?.updatedBy
                                    ? `${t('Last updated by')} ${caseData.updatedBy} ${t('at')} ${caseData.updatedAt || '-'}`
                                    : t('No case metadata saved yet')}
                            </Typography>
                            {canEdit && (
                                <Button variant="contained" onClick={handleSave} disabled={saving} sx={{textTransform: 'none', fontWeight: 700}}>
                                    {saving ? t('Saving...') : caseData?.approvalState === 'PENDING' ? t('Update Approval Request') : t('Save Case')}
                                </Button>
                            )}
                        </Stack>

                        <Divider />

                        <Stack spacing={1.25}>
                            <Typography sx={{fontSize: '0.95rem', fontWeight: 800, color: palette.text}}>
                                {t('Sensitive Audit Snapshots')}
                            </Typography>

                            {(caseData?.auditSnapshots || []).length > 0 ? (
                                <Stack spacing={1}>
                                    {caseData.auditSnapshots.map((snapshot) => (
                                        <Paper
                                            key={snapshot.id}
                                            elevation={0}
                                            sx={{
                                                p: 1.5,
                                                borderRadius: 3,
                                                border: `1px solid ${palette.borderSoft}`,
                                                backgroundColor: palette.cardBg,
                                            }}
                                        >
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.25, flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 280px'}}>
                                                    <Typography sx={{fontSize: '0.88rem', color: palette.text, fontWeight: 700}}>
                                                        {snapshot.title}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.8rem', color: palette.textMuted}}>
                                                        {snapshot.summary}
                                                    </Typography>
                                                </Box>
                                                <Chip
                                                    size="small"
                                                    label={(snapshot.changedFields || []).join(', ') || t('Sensitive fields')}
                                                    sx={{backgroundColor: palette.blueBg, color: palette.blueText, fontWeight: 700}}
                                                />
                                            </Box>
                                            <Box sx={{mt: 1.1, display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.25}}>
                                                <Box>
                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                                        {t('Before')}
                                                    </Typography>
                                                    <Box component="pre" sx={{mt: 0.55, mb: 0, p: 1.1, borderRadius: 2.5, overflowX: 'auto', fontSize: '0.74rem', lineHeight: 1.5, backgroundColor: palette.paperBg, color: palette.text}}>
                                                        {formatJsonSnapshot(snapshot.beforeSnapshot)}
                                                    </Box>
                                                </Box>
                                                <Box>
                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                                        {t('After')}
                                                    </Typography>
                                                    <Box component="pre" sx={{mt: 0.55, mb: 0, p: 1.1, borderRadius: 2.5, overflowX: 'auto', fontSize: '0.74rem', lineHeight: 1.5, backgroundColor: palette.paperBg, color: palette.text}}>
                                                        {formatJsonSnapshot(snapshot.afterSnapshot)}
                                                    </Box>
                                                </Box>
                                            </Box>
                                            <Typography sx={{mt: 0.85, fontSize: '0.76rem', color: palette.textMuted}}>
                                                {(snapshot.actor || t('System'))} | {snapshot.createdAt || '-'}
                                            </Typography>
                                        </Paper>
                                    ))}
                                </Stack>
                            ) : (
                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                    {t('No audit snapshots have been captured for this case yet.')}
                                </Typography>
                            )}
                        </Stack>

                        <Divider />

                        <Stack spacing={1.25}>
                            <Typography sx={{fontSize: '0.95rem', fontWeight: 800, color: palette.text}}>
                                {t('Case Timeline')}
                            </Typography>

                            {(caseData?.timeline || []).length > 0 ? (
                                <Stack spacing={1}>
                                    {caseData.timeline.map((event, index) => (
                                        <Paper
                                            key={`${event.eventType || 'event'}-${event.eventAt || 'time'}-${index}`}
                                            elevation={0}
                                            sx={{
                                                p: 1.5,
                                                borderRadius: 3,
                                                border: `1px solid ${palette.borderSoft}`,
                                                backgroundColor: palette.cardBg,
                                            }}
                                        >
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 320px'}}>
                                                    <Typography sx={{fontSize: '0.88rem', color: palette.text, fontWeight: 700}}>
                                                        {event.title}
                                                    </Typography>
                                                    {event.summary && (
                                                        <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.text, lineHeight: 1.55}}>
                                                            {event.summary}
                                                        </Typography>
                                                    )}
                                                </Box>
                                                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                    {event.sourceType && (
                                                        <Chip
                                                            size="small"
                                                            label={event.sourceType}
                                                            sx={{backgroundColor: palette.blueBg, color: palette.blueText, fontWeight: 700}}
                                                        />
                                                    )}
                                                    {event.status && (
                                                        <Chip
                                                            size="small"
                                                            label={event.status}
                                                            sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}
                                                        />
                                                    )}
                                                </Stack>
                                            </Box>
                                            <Typography sx={{mt: 0.85, fontSize: '0.76rem', color: palette.textMuted}}>
                                                {(event.actor || t('System'))} | {event.eventAt || '-'}
                                            </Typography>
                                        </Paper>
                                    ))}
                                </Stack>
                            ) : (
                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                    {t('No case timeline activity yet.')}
                                </Typography>
                            )}
                        </Stack>

                        <Divider />

                        <Stack spacing={1.25}>
                            <Typography sx={{fontSize: '0.95rem', fontWeight: 800, color: palette.text}}>
                                {t('Comments')}
                            </Typography>

                            {(caseData?.comments || []).length > 0 ? (
                                <Stack spacing={1}>
                                    {caseData.comments.map((comment) => (
                                        <Paper
                                            key={comment.id}
                                            elevation={0}
                                            sx={{
                                                p: 1.5,
                                                borderRadius: 3,
                                                border: `1px solid ${palette.borderSoft}`,
                                                backgroundColor: palette.cardBg,
                                            }}
                                        >
                                            <Typography sx={{fontSize: '0.88rem', color: palette.text, lineHeight: 1.55}}>
                                                {comment.commentText}
                                            </Typography>
                                            <Typography sx={{mt: 0.75, fontSize: '0.76rem', color: palette.textMuted}}>
                                                {comment.createdBy} | {comment.createdAt}
                                            </Typography>
                                        </Paper>
                                    ))}
                                </Stack>
                            ) : (
                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                    {t('No comments added yet.')}
                                </Typography>
                            )}

                            {canEdit && (
                                <Stack spacing={1}>
                                    <TextField
                                        label={t('Add Comment')}
                                        value={commentDraft}
                                        onChange={(e) => setCommentDraft(e.target.value)}
                                        multiline
                                        minRows={2}
                                        disabled={saving}
                                    />
                                    <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap'}}>
                                        <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                            {t('Commenting as')} {user?.username || '-'}
                                        </Typography>
                                        <Button variant="outlined" onClick={handleAddComment} disabled={saving || !commentDraft.trim()} sx={{textTransform: 'none', fontWeight: 700}}>
                                            {t('Add Comment')}
                                        </Button>
                                    </Box>
                                </Stack>
                            )}
                        </Stack>
                    </>
                )}
            </Stack>
        </Paper>
    )
}
