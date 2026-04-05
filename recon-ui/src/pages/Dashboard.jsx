import {Component, Suspense, useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    FormControl,
    Grid,
    InputLabel,
    MenuItem,
    OutlinedInput,
    Paper,
    Select,
    Tab,
    Tabs,
    Typography,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded'
import DashboardCustomizeRoundedIcon from '@mui/icons-material/DashboardCustomizeRounded'
import AdminPanelSettingsRoundedIcon from '@mui/icons-material/AdminPanelSettingsRounded'
import CompareArrowsRoundedIcon from '@mui/icons-material/CompareArrowsRounded'
import KPI from '../components/KPI'
import DetailTable from '../components/DetailTable'
import LineChartComponent from '../components/LineChartComponent'
import Alerts from './Alerts'
import ApprovalCenter from './ApprovalCenter'
import Activity from './Activity'
import Configurations from './Configurations'
import ExecutiveScorecards from './ExecutiveScorecards'
import ExceptionQueues from './ExceptionQueues'
import KnownIssues from './KnownIssues'
import NoiseSuppression from './NoiseSuppression'
import Operations from './Operations'
import ReconciliationJobs from './ReconciliationJobs'
import IntegrationHub from './IntegrationHub'
import OperationsCommandCenter from './OperationsCommandCenter'
import RegionalIncidentBoard from './RegionalIncidentBoard'
import RecurrenceAnalytics from './RecurrenceAnalytics'
import RootCauseAnalytics from './RootCauseAnalytics'
import RoutingPlaybooks from './RoutingPlaybooks'
import SlaManagement from './SlaManagement'
import StoreManagerLite from './StoreManagerLite'
import StoreScorecards from './StoreScorecards'
import TicketingCommunications from './TicketingCommunications'
import ManageUsers from './admin/ManageUsers'
import ManageRoles from './admin/ManageRoles'
import ManagePermissions from './admin/ManagePermissions'
import OrganizationHierarchy from './admin/OrganizationHierarchy'
import TenantAccessCenter from './admin/TenantAccessCenter'
import BrandingCenter from './admin/BrandingCenter'
import TransactionDrillDown from './TransactionDrillDown'
import {reconApi} from '../services/reconApi'
import {LocalizationProvider} from '@mui/x-date-pickers/LocalizationProvider'
import {DatePicker} from '@mui/x-date-pickers/DatePicker'
import {AdapterDayjs} from '@mui/x-date-pickers/AdapterDayjs'
import dayjs from 'dayjs'
import {useI18n} from '../context/I18nContext'
import {useAuth} from '../context/AuthContext'
import {ACTIVITY_TAB_IDS, ALERT_TAB_IDS, CONFIGURATION_TAB_IDS, EXCEPTION_TAB_IDS, getTabLabel, INTEGRATION_TAB_IDS, OPERATIONS_TAB_IDS, REPORT_TAB_IDS, SLA_TAB_IDS} from '../constants/navigation'
import {
    buildSiocsMfcsPresentation,
    buildSiocsMfcsDemoResolvedProfiles,
    formatSiocsMfcsTransactionFamily,
    formatSiocsMfcsTransactionPhase,
    getSiocsMfcsPhaseOptions,
    getSiocsMfcsReconStatuses,
    getSiocsMfcsStatusPresentation,
} from '../utils/siocsMfcsPresentation'
import {buildFixedInventoryPresentation, isFixedInventoryReconView} from '../utils/simRmsPresentation'
import {
    isSiocsMfcsDemoAvailable,
    isSiocsMfcsDemoEnabled,
    setSiocsMfcsDemoEnabled,
} from '../services/siocsMfcsDemoData'
import {
    EXCEPTION_QUEUE_PREFILL_EVENT,
    EXCEPTION_QUEUE_PREFILL_KEY,
    TRANSACTION_DRILLDOWN_PREFILL_EVENT,
    TRANSACTION_DRILLDOWN_PREFILL_KEY,
} from '../constants/uiStateKeys'

const PAGE_SIZE = 20
const TRANSACTION_DRILLDOWN_TAB_ID = 'transaction-drill-down'
const SECURITY_IDS = ['manage-users', 'manage-roles', 'manage-perms', 'org-hierarchy', 'tenant-access', 'branding-center']
const ALERT_IDS = ALERT_TAB_IDS
const EXCEPTION_IDS = EXCEPTION_TAB_IDS
const OPERATION_IDS = OPERATIONS_TAB_IDS
const INTEGRATION_IDS = INTEGRATION_TAB_IDS
const SLA_IDS = SLA_TAB_IDS
const ACTIVITY_IDS = ACTIVITY_TAB_IDS
const CONFIGURATION_IDS = CONFIGURATION_TAB_IDS
const REPORT_IDS = REPORT_TAB_IDS
const DYNAMIC_IMPORT_FAILURE_PATTERN =
    /Failed to fetch dynamically imported module|Importing a module script failed|Failed to fetch module/i

function getDynamicImportRetryKey(tabId) {
    return `dashboard-tab-import-retry:${tabId}`
}

function shouldAutoReloadForImportFailure(error) {
    const message = error?.message || String(error || '')
    return DYNAMIC_IMPORT_FAILURE_PATTERN.test(message)
}

class ModuleErrorBoundary extends Component {
    constructor(props) {
        super(props)
        this.state = {error: null}
    }

    static getDerivedStateFromError(error) {
        return {error}
    }

    componentDidCatch(error, info) {
        console.error('Dashboard tab render failed', {
            tabId: this.props.tabId,
            tabLabel: this.props.tabLabel,
            error,
            componentStack: info?.componentStack,
        })

        if (
            typeof window !== 'undefined'
            && shouldAutoReloadForImportFailure(error)
        ) {
            const retryKey = getDynamicImportRetryKey(this.props.tabId)
            const alreadyRetried =
                window.sessionStorage.getItem(retryKey) === '1'

            if (!alreadyRetried) {
                window.sessionStorage.setItem(retryKey, '1')
                window.location.reload()
            }
        }
    }

    componentDidUpdate(prevProps) {
        if (prevProps.resetKey !== this.props.resetKey && this.state.error) {
            this.setState({error: null})
        }

        if (
            prevProps.tabId !== this.props.tabId
            && typeof window !== 'undefined'
        ) {
            window.sessionStorage.removeItem(
                getDynamicImportRetryKey(prevProps.tabId)
            )
        }
    }

    handleRetry = () => {
        if (typeof window !== 'undefined') {
            window.sessionStorage.removeItem(
                getDynamicImportRetryKey(this.props.tabId)
            )
        }
        this.setState({error: null})
    }

    render() {
        const {error} = this.state
        const {children, onClose, palette, t, tabLabel} = this.props

        if (!error) {
            return children
        }

        return (
            <Box sx={{px: 1, py: 3}}>
                <Paper
                    elevation={0}
                    sx={{
                        p: 3,
                        borderRadius: '24px',
                        border: `1px solid ${palette.border}`,
                        backgroundColor: palette.cardBg,
                    }}
                >
                    <Typography
                        sx={{
                            fontSize: '1.2rem',
                            fontWeight: 800,
                            color: palette.text,
                        }}
                    >
                        {t('Unable to load this module')}
                    </Typography>
                    <Typography
                        sx={{
                            mt: 0.75,
                            fontSize: '0.9rem',
                            color: palette.textMuted,
                            maxWidth: 760,
                        }}
                    >
                        {t(
                            'The {module} workspace hit a runtime error while opening. The rest of the application is still available.',
                            {module: tabLabel}
                        )}
                    </Typography>
                    <Alert severity="error" sx={{mt: 2}}>
                        {error?.message || t('Unexpected module error')}
                    </Alert>
                    <Box sx={{display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap'}}>
                        <Button variant="contained" onClick={this.handleRetry}>
                            {t('Retry')}
                        </Button>
                        <Button variant="outlined" onClick={onClose}>
                            {t('Close Tab')}
                        </Button>
                    </Box>
                </Paper>
            </Box>
        )
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

function getSessionReconTabIds() {
    return getSessionReconModules()
        .map((module) => module?.tabId)
        .filter(Boolean)
}

function getReconViewByTabId(tabId) {
    return (
        getSessionReconModules().find((module) => module?.tabId === tabId)
            ?.reconView || null
    )
}

function isWorkbenchTab(tabId) {
    return getSessionReconTabIds().includes(tabId)
}

function isInventoryReconView(reconView) {
    return reconView === 'SIOCS_MFCS' || isFixedInventoryReconView(reconView)
}

function isInventoryWorkbenchTab(tabId) {
    return isInventoryReconView(getReconViewByTabId(tabId))
}

function isDynamicInventoryWorkbenchTab(tabId) {
    return getReconViewByTabId(tabId) === 'SIOCS_MFCS'
}

function getMissingKpiTitle(tabId, t) {
    if (isDynamicInventoryWorkbenchTab(tabId)) {
        return t('Missing in Counterparty System')
    }
    if (isInventoryWorkbenchTab(tabId)) {
        return t(`Missing in ${getTargetSystem(tabId)}`)
    }
    switch (tabId) {
    case 'xstore-siocs':
        return t('Missing in SIOCS')
    case 'xstore-xocs':
        return t('Missing in XOCS')
    case 'xstore-sim':
    default:
        return t('Missing in SIM')
    }
}

function getTargetSystem(tabId) {
    return (
        getSessionReconModules().find((module) => module?.tabId === tabId)
            ?.targetSystem || 'SIM'
    )
}

function getProcessingPendingKpiTitle(tabId, t) {
    if (isDynamicInventoryWorkbenchTab(tabId)) {
        return t('Processing Pending in Counterparty System')
    }
    return t(`Processing Pending in ${getTargetSystem(tabId)}`)
}

function getDuplicateKpiTitle(tabId, t) {
    if (isInventoryWorkbenchTab(tabId)) {
        return t('Duplicate Transactions')
    }
    return t(`Duplicate Transactions in ${getTargetSystem(tabId)}`)
}

function getAwaitingKpiTitle(tabId, t) {
    switch (tabId) {
        case 'xstore-siocs':
            return t('Awaiting SIOCS')
        case 'xstore-xocs':
            return t('Awaiting XOCS')
        default:
            if (isInventoryWorkbenchTab(tabId)) {
                return t('Awaiting Next Business Event')
            }
            if (tabId === 'xstore-sim') {
                return t('Awaiting SIM')
            }
            return t('Awaiting SIM')
    }
}

function getKpiSections(tabId, t) {
    const summary = [
        {title: t('Total Transactions'), key: 'total'},
        {title: t('Matched'), key: 'matched'},
        {title: getMissingKpiTitle(tabId, t), key: 'missingInSiocs'},
        ...(tabId === 'xstore-xocs'
            ? []
            : [{
                title: getProcessingPendingKpiTitle(tabId, t),
                key: 'processingPending',
            }]),
        ...(isWorkbenchTab(tabId)
            ? [{
                title: getAwaitingKpiTitle(tabId, t),
                key: 'awaitingSim',
            }]
            : []),
        ...(isWorkbenchTab(tabId)
            ? [{
                title: t('Exceptions'),
                key: 'exceptionCount',
            }]
            : []),
    ]

    const transactionIssues = [
        ...(tabId === 'xstore-xocs'
            ? []
            : [{
                title: getProcessingPendingKpiTitle(tabId, t),
                key: 'processingPending',
            }]),
        ...(isWorkbenchTab(tabId)
            ? [{
                title: getAwaitingKpiTitle(tabId, t),
                key: 'awaitingSim',
            }]
            : []),
        ...(tabId === 'xstore-xocs'
            ? [{title: t('Transaction Total Mismatch'), key: 'totalMismatch'}]
            : []),
        ...((tabId === 'xstore-siocs' || tabId === 'xstore-sim' || isInventoryWorkbenchTab(tabId))
            ? [{title: getDuplicateKpiTitle(tabId, t), key: 'duplicateTransactions'}]
            : []),
    ]

    const itemIssues = [
        {title: t('Item Missing'), key: 'itemMissing'},
        {title: t('Quantity Mismatch'), key: 'quantityMismatch'},
    ]

    return {
        summary,
        issues: [...transactionIssues, ...itemIssues],
    }
}

function getKpiStatus(tabId, selectedKpi, targetOverride = null) {
    const target = targetOverride || getTargetSystem(tabId)

    switch (selectedKpi) {
        case 'matched':
            return 'MATCHED'
        case 'missingInSiocs':
            return `MISSING_IN_${target}`
        case 'quantityMismatch':
            return 'QUANTITY_MISMATCH'
        case 'totalMismatch':
            return 'TOTAL_MISMATCH'
        case 'itemMissing':
            return 'ITEM_MISSING'
        case 'duplicateTransactions':
            return `DUPLICATE_IN_${target}`
        case 'processingPending':
            return `PROCESSING_PENDING_IN_${target}`
        case 'awaitingSim':
            return `AWAITING_${target}`
        case 'pendingTotal':
            return `PROCESSING_PENDING_IN_${target}`
        case 'total':
        default:
            return null
    }
}

function formatStatusCode(status) {
    if (!status) return '-'
    return status
        .split('_')
        .filter(Boolean)
        .map((segment) => segment.charAt(0) + segment.slice(1).toLowerCase())
        .join(' ')
}

function formatDateTimeValue(value) {
    if (!value) {
        return 'Not available yet'
    }
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) {
        return String(value).replace('T', ' ')
    }
    return parsed.toLocaleString()
}

function formatRelativeAge(value, t) {
    if (!value) {
        return t('Not available yet')
    }
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) {
        return t('Not available yet')
    }
    const diffMinutes = Math.max(0, Math.floor((Date.now() - parsed.getTime()) / 60000))
    if (diffMinutes < 1) return t('Just now')
    if (diffMinutes < 60) return `${diffMinutes}m`
    const diffHours = Math.floor(diffMinutes / 60)
    if (diffHours < 24) return `${diffHours}h`
    return `${Math.floor(diffHours / 24)}d`
}

function formatNumberValue(value, digits = 2) {
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

function getStatusPresentation(status, targetSystem, t) {
    const normalized = (status || '').toUpperCase()

    switch (normalized) {
        case 'MATCHED':
            return {
                label: t('Matched'),
                issueType: t('No exception'),
                explanation: t('This transaction reconciled successfully across both systems.'),
                nextAction: t('No action required unless the case is reopened.'),
            }
        case `PROCESSING_PENDING_IN_${targetSystem}`:
            return {
                label: t(`Pending in ${targetSystem}`),
                issueType: t('Pending Processing'),
                explanation: t(`The transaction exists in Xstore and is still waiting for downstream processing in ${targetSystem}.`),
                nextAction: t(`Monitor for completion and escalate if it remains pending past SLA.`),
            }
        case `AWAITING_${targetSystem}`:
            return {
                label: t(`Awaiting ${targetSystem} processing`),
                issueType: t('Pending Processing'),
                explanation: t(`The reconciliation lane is still waiting for the ${targetSystem} side to arrive or complete.`),
                nextAction: t(`Continue monitoring and investigate the downstream path if no movement occurs.`),
            }
        case `MISSING_IN_${targetSystem}`:
            return {
                label: t(`Missing in ${targetSystem}`),
                issueType: t('Missing Transaction'),
                explanation: t(`The transaction is present in Xstore but is missing on the ${targetSystem} side.`),
                nextAction: t(`Validate publish, ingestion, and downstream processing into ${targetSystem}.`),
            }
        case `DUPLICATE_IN_${targetSystem}`:
            return {
                label: t(`Duplicate in ${targetSystem}`),
                issueType: t('Duplicate Transaction'),
                explanation: t(`More than one downstream posting was detected for the same business transaction.`),
                nextAction: t(`Escalate to the exception queue and review duplicate posting history.`),
            }
        case 'QUANTITY_MISMATCH':
            return {
                label: t('Quantity mismatch'),
                issueType: t('Quantity mismatch'),
                explanation: t('The transaction matched at a business level, but the item quantity does not align.'),
                nextAction: t('Review line-level quantities and investigate inventory movement differences.'),
            }
        case 'TOTAL_MISMATCH':
            return {
                label: t('Amount mismatch'),
                issueType: t('Amount mismatch'),
                explanation: t('The transaction totals do not match across the compared systems.'),
                nextAction: t('Review the transaction amount, tender totals, and line calculations.'),
            }
        case 'ITEM_MISSING':
            return {
                label: t('Item missing'),
                issueType: t('Item missing'),
                explanation: t('One or more expected items are missing between the compared transaction payloads.'),
                nextAction: t('Inspect the line details and verify item-level data completeness.'),
            }
        case `PROCESSING_FAILED_IN_${targetSystem}`:
        case 'PROCESSING_FAILED':
            return {
                label: t(`Processing failed in ${targetSystem}`),
                issueType: t('Processing failure'),
                explanation: t(`The downstream ${targetSystem} processing failed after the transaction was received.`),
                nextAction: t('Investigate the failure reason and escalate to the exception queue for follow-up.'),
            }
        default:
            return {
                label: formatStatusCode(normalized),
                issueType: t('Requires review'),
                explanation: t('This transaction needs review based on its current reconciliation state.'),
                nextAction: t('Open drill-down and exception queue to continue investigation.'),
            }
    }
}

function getStatusTone(status, targetSystem) {
    const normalized = (status || '').toUpperCase()
    const matchesPending = targetSystem
        ? (normalized === `PROCESSING_PENDING_IN_${targetSystem}` || normalized === `AWAITING_${targetSystem}`)
        : (normalized.startsWith('PROCESSING_PENDING_IN_') || normalized.startsWith('AWAITING_'))
    const matchesError = targetSystem
        ? (
            normalized === `MISSING_IN_${targetSystem}` ||
            normalized === `DUPLICATE_IN_${targetSystem}` ||
            normalized === `PROCESSING_FAILED_IN_${targetSystem}`
        )
        : (
            normalized.startsWith('MISSING_IN_') ||
            normalized.startsWith('DUPLICATE_IN_') ||
            normalized.startsWith('PROCESSING_FAILED_IN_')
        )

    if (normalized === 'MATCHED') {
        return 'success'
    }
    if (matchesPending) {
        return 'warning'
    }
    if (
        matchesError ||
        normalized === 'QUANTITY_MISMATCH' ||
        normalized === 'TOTAL_MISMATCH' ||
        normalized === 'ITEM_MISSING' ||
        normalized === 'PROCESSING_FAILED'
    ) {
        return 'error'
    }
    return 'info'
}

function getPriorityContext(row, targetSystem, t) {
    const status = (row?.reconStatus || '').toUpperCase()
    const referenceValue = row?.updatedAt || row?.reconciledAt || row?.businessDate
    const parsed = referenceValue ? new Date(referenceValue) : null
    const ageHours = parsed && !Number.isNaN(parsed.getTime())
        ? Math.max(0, Math.floor((Date.now() - parsed.getTime()) / 3600000))
        : 0
    const isMissingStatus = targetSystem
        ? status === `MISSING_IN_${targetSystem}`
        : status.startsWith('MISSING_IN_')
    const isDuplicateStatus = targetSystem
        ? status === `DUPLICATE_IN_${targetSystem}`
        : status.startsWith('DUPLICATE_IN_')
    const isPendingStatus = targetSystem
        ? (status === `PROCESSING_PENDING_IN_${targetSystem}` || status === `AWAITING_${targetSystem}`)
        : (status.startsWith('PROCESSING_PENDING_IN_') || status.startsWith('AWAITING_'))

    if (isMissingStatus || status === 'TOTAL_MISMATCH') {
        return ageHours >= 24
            ? {label: t('Critical'), sortWeight: 400}
            : {label: t('High'), sortWeight: 320}
    }
    if (isDuplicateStatus || status === 'ITEM_MISSING' || status === 'QUANTITY_MISMATCH') {
        return ageHours >= 8
            ? {label: t('High'), sortWeight: 300}
            : {label: t('Medium'), sortWeight: 220}
    }
    if (isPendingStatus) {
        return ageHours >= 4
            ? {label: t('Due Today'), sortWeight: 260}
            : {label: t('Pending'), sortWeight: 180}
    }
    if (status === 'MATCHED') {
        return {label: t('Informational'), sortWeight: 40}
    }
    return {label: t('Medium'), sortWeight: 160}
}

function getToneChipStyles(tone, palette) {
    switch (tone) {
        case 'error':
            return {
                backgroundColor: palette.dangerBg,
                color: palette.dangerText,
                border: `1px solid ${palette.dangerBorder}`,
            }
        case 'warning':
            return {
                backgroundColor: '#FFF7ED',
                color: '#C2410C',
                border: '1px solid #FED7AA',
            }
        case 'success':
            return {
                backgroundColor: palette.tealChipBg,
                color: palette.tealChipText,
                border: `1px solid ${palette.border}`,
            }
        case 'info':
        default:
            return {
                backgroundColor: palette.blueChipBg,
                color: palette.blueChipText,
                border: `1px solid ${palette.border}`,
            }
    }
}

const TAB_COLORS = {
    recon: {active: 'var(--brand-primary)', hover: 'var(--brand-primary-hover)'},
    security: {active: 'var(--brand-primary)', hover: 'var(--brand-primary-hover)'},
}

function getTransactionIdFromExternalId(externalId, fallback = '-') {
    if (!externalId || externalId.length < 14) return fallback
    const seq = externalId.slice(8, externalId.length - 8)
    const normalized = seq.replace(/^0+(?!$)/, '')
    return normalized || '0'
}

function getDefaultSimWorkbenchSelection(kpis, t) {
    if ((kpis.processingPending || 0) > 0) {
        return {
            key: 'processingPending',
            title: t('Processing pending in SIM'),
        }
    }
    if ((kpis.awaitingSim || 0) > 0) {
        return {
            key: 'awaitingSim',
            title: t('Awaiting SIM'),
        }
    }
    if ((kpis.missingInSiocs || 0) > 0) {
        return {
            key: 'missingInSiocs',
            title: t('Missing in SIM'),
        }
    }
    if ((kpis.matched || 0) > 0) {
        return {
            key: 'matched',
            title: t('Matched'),
        }
    }
    return {
        key: 'total',
        title: t('Total Transactions'),
    }
}

function getDetailTableSubtitle(selectedKpi, totalElements, targetSystem, t) {
    const quantityLabel = `${totalElements} ${t(totalElements === 1 ? 'transaction' : 'transactions')}`

    switch (selectedKpi) {
        case 'matched':
            return `${quantityLabel} ${t(`reconciled successfully between Xstore and ${targetSystem}.`)}`
        case 'missingInSiocs':
            return `${quantityLabel} ${t(`were expected in ${targetSystem} but were not found and require investigation.`)}`
        case 'processingPending':
            return `${quantityLabel} ${t(`received by ${targetSystem} and currently processing.`)}`
        case 'awaitingSim':
            return `${quantityLabel} ${t(`are awaiting receipt in ${targetSystem} or are still waiting in ${targetSystem} processing.`)}`
        case 'duplicateTransactions':
            return `${quantityLabel} ${t(`show duplicate downstream postings that require review.`)}`
        case 'quantityMismatch':
            return `${quantityLabel} ${t('show item quantity differences between systems.')}`
        case 'itemMissing':
            return `${quantityLabel} ${t('show missing item lines that require review.')}`
        case 'total':
        default:
            return `${quantityLabel} ${t('in scope for the selected filters.')}`
    }
}

function getThemeMode() {
    return document.documentElement.getAttribute('data-recon-theme') === 'dark'
        ? 'Dark'
        : 'Light'
}

function getPalette(themeMode) {
    const isDark = themeMode === 'Dark'

    return {
        isDark,
        pageBg: isDark ? '#0B1220' : '#F4F6FB',
        cardBg: isDark ? '#0F172A' : '#FFFFFF',
        cardBgAlt: isDark ? '#111827' : '#F7F9FD',
        border: isDark ? '#1E293B' : '#DCE3EF',
        borderSoft: isDark ? '#243041' : '#E8EDF5',
        text: isDark ? '#E2E8F0' : '#22314D',
        textMuted: isDark ? '#94A3B8' : '#6C7A92',
        textSoft: isDark ? '#64748B' : '#8C97AA',
        hoverBg: isDark ? '#111827' : '#F7F9FD',
        selectedBg: isDark ? '#0B1220' : '#FFFFFF',
        selectedBorder: isDark ? 'var(--brand-primary)' : 'var(--brand-primary-border)',
        selectedRowBg: isDark ? '#1A2E4A' : '#DBEAFE',
        selectedRowBorder: isDark ? '#60A5FA' : '#3B82F6',
        blueChipBg: isDark ? 'rgba(var(--brand-primary-rgb), 0.18)' : 'var(--brand-primary-surface)',
        blueChipText: 'var(--brand-primary)',
        tealChipBg: isDark ? 'rgba(var(--brand-secondary-rgb), 0.14)' : 'var(--brand-secondary-surface)',
        tealChipText: 'var(--brand-secondary)',
        neutralChipBg: isDark ? '#111827' : '#EEF1F7',
        neutralChipText: isDark ? '#CBD5E1' : '#4F5C72',
        dangerBg: isDark ? '#2A1215' : '#fff5f5',
        dangerBgHover: isDark ? '#3A151A' : '#fee2e2',
        dangerBorder: '#fca5a5',
        dangerText: '#ef4444',
        scrollbarTrack: isDark ? '#0F172A' : '#EEF2F8',
        scrollbarThumb: isDark ? '#334155' : '#C9D3E3',
        tabBg: isDark ? '#0F172A' : '#F8FAFD',
        tabActiveBg: isDark ? '#111827' : '#FFFFFF',
        overlay1: isDark
            ? 'radial-gradient(circle, rgba(var(--brand-primary-rgb), 0.16), rgba(var(--brand-primary-rgb), 0))'
            : 'radial-gradient(circle, rgba(var(--brand-primary-rgb), 0.12), rgba(var(--brand-primary-rgb), 0))',
        overlay2: isDark
            ? 'radial-gradient(circle, rgba(var(--brand-secondary-rgb), 0.16), rgba(var(--brand-secondary-rgb), 0))'
            : 'radial-gradient(circle, rgba(var(--brand-secondary-rgb), 0.14), rgba(var(--brand-secondary-rgb), 0))',
        heroBg: isDark
            ? 'linear-gradient(135deg, #0F172A 0%, #111827 100%)'
            : 'linear-gradient(135deg, #FFFFFF 0%, rgba(var(--brand-primary-rgb), 0.04) 42%, rgba(var(--brand-secondary-rgb), 0.08) 100%)',
    }
}

function tabColor(id) {
    return SECURITY_IDS.includes(id)
        ? TAB_COLORS.security
        : CONFIGURATION_IDS.includes(id)
            ? {active: 'var(--brand-primary)', hover: 'var(--brand-primary-hover)'}
        : TAB_COLORS.recon
}

function ClearableDatePicker({
                                 label,
                                 value,
                                 onChange,
                                 minDate,
                                 palette,
                             }) {
    const [hovered, setHovered] = useState(false)

    return (
        <Box
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
            sx={{position: 'relative', width: 190}}
        >
            <DatePicker
                label={label}
                value={value ? dayjs(value) : null}
                onChange={(val) =>
                    onChange(val ? val.format('YYYY-MM-DD') : '')
                }
                format="DD-MMM-YYYY"
                minDate={minDate ? dayjs(minDate) : undefined}
                slotProps={{
                    textField: {
                        size: 'small',
                        sx: {
                            width: 190,
                            '& .MuiOutlinedInput-root': {
                                color: palette.text,
                                backgroundColor: palette.cardBg,
                                '& fieldset': {
                                    borderColor: palette.border,
                                },
                                '&:hover fieldset': {
                                    borderColor: palette.textSoft,
                                },
                                '&.Mui-focused fieldset': {
                                    borderColor: 'var(--brand-primary)',
                                },
                            },
                            '& .MuiInputLabel-root': {
                                color: palette.textMuted,
                            },
                            '& .MuiInputLabel-root.Mui-focused': {
                                color: 'var(--brand-primary)',
                            },
                            '& .MuiSvgIcon-root': {
                                color: palette.textMuted,
                            },
                        },
                    },
                    popper: {
                        sx: {
                            '& .MuiPaper-root': {
                                backgroundColor: palette.cardBg,
                                color: palette.text,
                                border: `1px solid ${palette.border}`,
                            },
                        },
                    },
                }}
            />

            {value && hovered && (
                <Box
                    onMouseDown={(e) => {
                        e.preventDefault()
                        e.stopPropagation()
                        onChange('')
                    }}
                    sx={{
                        position: 'absolute',
                        top: -7,
                        right: -7,
                        width: 18,
                        height: 18,
                        borderRadius: '50%',
                        backgroundColor: palette.textMuted,
                        color: '#fff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        fontSize: 10,
                        fontWeight: 700,
                        zIndex: 10,
                        boxShadow: '0 1px 3px rgba(0,0,0,0.25)',
                        '&:hover': {
                            backgroundColor: '#ef4444',
                        },
                    }}
                >
                    ✕
                </Box>
            )}
        </Box>
    )
}

function TabLoadingPanel({palette, label}) {
    return (
        <Box sx={{display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', py: 10, gap: 1.5}}>
            <CircularProgress size={28}/>
            <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                {label}
            </Typography>
        </Box>
    )
}

const WELCOME_RECON_ACCENTS = [
    {accent: '#4A79D8', lightBg: '#EEF4FF'},
    {accent: '#5C74D6', lightBg: '#F1F4FF'},
    {accent: '#2F6E5F', lightBg: '#EDF9F4'},
    {accent: '#C56A1A', lightBg: '#FFF5E8'},
]

function WelcomeLanding({palette, t}) {
    const {hasPermission} = useAuth()
    const reconModules = getSessionReconModules().map((module, index) => {
        const accentTheme = WELCOME_RECON_ACCENTS[index % WELCOME_RECON_ACCENTS.length]
        return {
            id: module?.tabId || module?.reconView || `recon-module-${index}`,
            title: t(module?.label || module?.reconView || t('Reconciliation')),
            desc: t('Open the reconciliation workbench, review KPI exceptions, and drill into mismatches for this lane.'),
            icon: <CompareArrowsRoundedIcon sx={{fontSize: 20}}/>,
            badge: module?.targetSystem
                ? t(`${module.targetSystem} Reconciliation`)
                : t('Reconciliation'),
            accent: accentTheme.accent,
            bg: palette.isDark ? '#0F172A' : accentTheme.lightBg,
        }
    })

    if (hasPermission('ADMIN_USERS')) {
        reconModules.push({
            id: 'manage-users',
            title: t('Manage Users'),
            desc: t('Control user access, store scope, and role assignments across your RetailINQ workspace.'),
            icon: <AdminPanelSettingsRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('Security'),
            accent: '#4A79D8',
            bg: palette.isDark ? '#0F172A' : '#EEF4FF',
        })
    }

    return (
        <Box sx={{px: {xs: 2, md: 3}, py: {xs: 2, md: 2.5}, maxWidth: 1440}}>
            <Paper
                elevation={0}
                sx={{
                    position: 'relative',
                    overflow: 'hidden',
                    borderRadius: '28px',
                    border: `1px solid ${palette.border}`,
                    background: palette.heroBg,
                    boxShadow: palette.isDark
                        ? '0 18px 44px rgba(0,0,0,0.20)'
                        : '0 18px 42px rgba(34,49,77,0.08)',
                    px: {xs: 2.5, md: 3},
                    py: {xs: 2.5, md: 3},
                    mb: 2,
                }}
            >
                <Box
                    sx={{
                        position: 'absolute',
                        top: -60,
                        right: -60,
                        width: 220,
                        height: 220,
                        borderRadius: '50%',
                        background: palette.overlay1,
                        pointerEvents: 'none',
                    }}
                />
                <Box
                    sx={{
                        position: 'absolute',
                        bottom: -80,
                        left: -80,
                        width: 260,
                        height: 260,
                        borderRadius: '50%',
                        background: palette.overlay2,
                        pointerEvents: 'none',
                    }}
                />

                <Box
                    sx={{
                        position: 'relative',
                        zIndex: 1,
                        display: 'grid',
                        gridTemplateColumns: {xs: '1fr', lg: '1.32fr 0.88fr'},
                        gap: 2,
                        alignItems: 'center',
                    }}
                >
                    <Box>
                        <Chip
                            label={t('Retail Reconciliation Platform')}
                            size="small"
                            sx={{
                                mb: 1.5,
                                height: 28,
                                px: 0.5,
                                backgroundColor: palette.tealChipBg,
                                color: palette.tealChipText,
                                fontWeight: 700,
                                fontSize: '0.76rem',
                                boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.65)',
                            }}
                        />

                        <Typography
                            sx={{
                                fontSize: {xs: '1.75rem', md: '2.15rem'},
                                fontWeight: 700,
                                color: palette.text,
                                lineHeight: 1.04,
                                letterSpacing: '-0.6px',
                                mb: 0.8,
                            }}
                        >
                            {t('Welcome to RetailINQ')}
                        </Typography>

                        <Typography
                            sx={{
                                fontSize: {xs: '0.92rem', md: '0.96rem'},
                                color: palette.textMuted,
                                lineHeight: 1.55,
                                maxWidth: 680,
                            }}
                        >
                            {t(
                                'Centralize reconciliation, analytics, and security administration in one premium control center. Select a module from the sidebar to start investigating data, reviewing exceptions, or managing access.'
                            )}
                        </Typography>

                        <Box
                            sx={{
                                display: 'flex',
                                gap: 1.25,
                                flexWrap: 'wrap',
                                mt: 1.5,
                            }}
                        >
                            <Chip
                                label={t('Modern SaaS Admin')}
                                size="small"
                                sx={{
                                    backgroundColor: palette.blueChipBg,
                                    color: palette.blueChipText,
                                    fontWeight: 700,
                                    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.65)',
                                }}
                            />
                            <Chip
                                label={t('Reconciliation Insights')}
                                size="small"
                                sx={{
                                    backgroundColor: palette.tealChipBg,
                                    color: palette.tealChipText,
                                    fontWeight: 700,
                                    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.65)',
                                }}
                            />
                            <Chip
                                label={t('Analytics + Integrations')}
                                size="small"
                                sx={{
                                    backgroundColor: palette.neutralChipBg,
                                    color: palette.neutralChipText,
                                    fontWeight: 700,
                                    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.65)',
                                }}
                            />
                        </Box>
                    </Box>

                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: '1fr',
                            gap: 1,
                            maxWidth: 290,
                            justifySelf: 'end',
                            width: '100%',
                        }}
                    >
                        {[
                            [t('Workspace'), 'RetailINQ', t('Unified operations console')],
                            [t('Experience'), t('Premium SaaS'), t('Secure, modern, scalable')],
                            [t('Core Focus'), t('Reconciliation'), t('Exceptions and controls')],
                        ].map(([label, title, desc]) => (
                            <Paper
                                key={label}
                                elevation={0}
                                sx={{
                                    p: 1.5,
                                    borderRadius: '18px',
                                    border: `1px solid ${palette.border}`,
                                    backgroundColor: palette.cardBg,
                                    boxShadow: palette.isDark
                                        ? '0 10px 20px rgba(0,0,0,0.16)'
                                        : '0 10px 24px rgba(34,49,77,0.08)',
                                }}
                            >
                                <Typography
                                    sx={{
                                        fontSize: '0.72rem',
                                        color: palette.textMuted,
                                        textTransform: 'uppercase',
                                        letterSpacing: '0.5px',
                                        fontWeight: 700,
                                        mb: 0.5,
                                    }}
                                >
                                    {label}
                                </Typography>
                                <Typography
                                    sx={{
                                        fontSize: '1rem',
                                        fontWeight: 700,
                                        color: palette.text,
                                    }}
                                >
                                    {title}
                                </Typography>
                                <Typography
                                    sx={{
                                        mt: 0.35,
                                        fontSize: '0.76rem',
                                        color: palette.textMuted,
                                    }}
                                >
                                    {desc}
                                </Typography>
                            </Paper>
                        ))}
                    </Box>
                </Box>
            </Paper>

            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: {
                        xs: '1fr',
                        md: 'repeat(4, minmax(0, 1fr))',
                    },
                    gap: 1.25,
                }}
            >
                {reconModules.map((module) => (
                    <Paper
                        key={module.id}
                        elevation={0}
                        sx={{
                            p: 1.8,
                            borderRadius: '20px',
                            border: `1px solid ${palette.border}`,
                            backgroundColor: palette.cardBg,
                            boxShadow: palette.isDark
                                ? 'none'
                                : '0 10px 24px rgba(34,49,77,0.06)',
                            transition: 'all 0.18s ease',
                            '&:hover': {
                                transform: 'translateY(-2px)',
                                boxShadow: palette.isDark
                                    ? '0 10px 24px rgba(0, 0, 0, 0.22)'
                                    : '0 16px 30px rgba(34,49,77,0.10)',
                            },
                        }}
                    >
                        <Box
                            sx={{
                                width: 38,
                                height: 38,
                                borderRadius: '12px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                backgroundColor: module.bg,
                                color: module.accent,
                                mb: 1,
                            }}
                        >
                            {module.icon}
                        </Box>

                        <Chip
                            label={module.badge}
                            size="small"
                            sx={{
                                mb: 0.8,
                                backgroundColor: module.bg,
                                color: module.accent,
                                fontWeight: 700,
                                fontSize: '0.72rem',
                            }}
                        />

                        <Typography
                            sx={{
                                fontSize: '0.94rem',
                                fontWeight: 700,
                                color: palette.text,
                                mb: 0.45,
                            }}
                        >
                            {module.title}
                        </Typography>

                        <Typography
                            sx={{
                                fontSize: '0.8rem',
                                color: palette.textMuted,
                                lineHeight: 1.45,
                                mb: 1.1,
                            }}
                        >
                            {module.desc}
                        </Typography>

                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 0.5,
                                color: module.accent,
                            }}
                        >
                            <Typography
                                sx={{
                                    fontSize: '0.78rem',
                                    fontWeight: 700,
                                }}
                            >
                                Open from sidebar
                            </Typography>
                            <ArrowForwardRoundedIcon sx={{fontSize: 18}}/>
                        </Box>
                    </Paper>
                ))}
            </Box>
        </Box>
    )
}

function ComingSoonModule({tabId, palette, t}) {
    return (
        <Box sx={{px: 4, py: 4, maxWidth: 1480}}>
            <Paper
                elevation={0}
                sx={{
                    p: 4,
                    borderRadius: '28px',
                    border: `1px solid ${palette.border}`,
                    background: palette.heroBg,
                }}
            >
                <Chip
                    label={t('Module Preview')}
                    size="small"
                    sx={{
                        mb: 1.5,
                        backgroundColor: palette.blueChipBg,
                        color: palette.blueChipText,
                        fontWeight: 700,
                    }}
                />

                <Typography
                    variant="h4"
                    sx={{
                        fontWeight: 700,
                        color: palette.text,
                        mb: 1.25,
                    }}
                >
                    {getTabLabel(t, tabId)}
                </Typography>

                <Typography
                    sx={{
                        color: palette.textMuted,
                        fontSize: '1rem',
                        maxWidth: 780,
                        lineHeight: 1.7,
                    }}
                >
                    {t(
                        'This module is being prepared with the same premium SaaS experience as the rest of RetailINQ. You can continue with reconciliation or admin modules already available.'
                    )}
                </Typography>
            </Paper>
        </Box>
    )
}

function ReconContent({tabId, palette, t, onOpenTab}) {
    const reconView = getReconViewByTabId(tabId)
    const targetSystem = getTargetSystem(tabId)
    const {hasPermission} = useAuth()
    const canViewExceptionQueue = hasPermission('EXCEPTION_QUEUE_VIEW')
    const [kpis, setKpis] = useState({})
    const [selectedKpi, setSelectedKpi] = useState(null)
    const [selectedKpiFilter, setSelectedKpiFilter] = useState(null)
    const [detailData, setDetailData] = useState([])
    const [selectedDetailRow, setSelectedDetailRow] = useState(null)
    const [detailTitle, setDetailTitle] = useState('')
    const [detailTransactionFamilyFocus, setDetailTransactionFamilyFocus] = useState(null)
    const [analytics, setAnalytics] = useState(null)
    const [contentTab, setContentTab] = useState('workbench')
    const [loadingKpis, setLoadingKpis] = useState(false)
    const [loadingDetail, setLoadingDetail] = useState(false)
    const [loadingAnalytics, setLoadingAnalytics] = useState(false)
    const [page, setPage] = useState(0)
    const [totalElements, setTotalElements] = useState(0)
    const [stores, setStores] = useState([])
    const [registers, setRegisters] = useState([])
    const [transactionTypes, setTransactionTypes] = useState([])
    const [transactionFamilies, setTransactionFamilies] = useState([])
    const transactionPhaseOptions = useMemo(() => getSiocsMfcsPhaseOptions(t), [t])
    const [selectedStores, setSelectedStores] = useState([])
    const [selectedRegisters, setSelectedRegisters] = useState([])
    const [selectedTransactionTypes, setSelectedTransactionTypes] = useState([])
    const [selectedTransactionFamilies, setSelectedTransactionFamilies] = useState([])
    const [selectedTransactionPhases, setSelectedTransactionPhases] = useState([])
    const [mfcsDemoMode, setMfcsDemoMode] = useState(() => isSiocsMfcsDemoEnabled())
    const mfcsDemoAvailable = isSiocsMfcsDemoAvailable()
    const [fromDate, setFromDate] = useState('')
    const [toDate, setToDate] = useState('')
    const isTransactionFamilyScoped = isInventoryReconView(reconView)
    const hasResultsWorkspace = isWorkbenchTab(tabId)
    const isSimWorkbench = tabId === 'xstore-sim'
    const isSiocsMfcsWorkbench = reconView === 'SIOCS_MFCS'
    const isInventoryWorkbench = isInventoryReconView(reconView)
    const inventoryResolvedProfiles = useMemo(
        () => (isSiocsMfcsWorkbench && mfcsDemoMode ? buildSiocsMfcsDemoResolvedProfiles(t) : undefined),
        [isSiocsMfcsWorkbench, mfcsDemoMode, t]
    )
    const inventoryPhaseFilterEnabled = isSiocsMfcsWorkbench && mfcsDemoMode && selectedTransactionFamilies.length > 0
    const detailScopedTransactionFamilies = useMemo(
        () => (isInventoryWorkbench && detailTransactionFamilyFocus
            ? [detailTransactionFamilyFocus]
            : selectedTransactionFamilies),
        [detailTransactionFamilyFocus, isInventoryWorkbench, selectedTransactionFamilies],
    )
    const detailTransactionFamilyLabel = isInventoryWorkbench && detailTransactionFamilyFocus
        ? formatSiocsMfcsTransactionFamily(detailTransactionFamilyFocus, t)
        : ''
    const inventoryPresentation = useMemo(() => {
        if (isSiocsMfcsWorkbench) {
            return buildSiocsMfcsPresentation({
                selectedFamilies: selectedTransactionFamilies,
                selectedPhases: selectedTransactionPhases,
                resolvedProfiles: inventoryResolvedProfiles,
                t,
            })
        }
        if (isInventoryWorkbench) {
            return buildFixedInventoryPresentation({
                reconView,
                selectedFamilies: selectedTransactionFamilies,
                selectedPhases: selectedTransactionPhases,
                t,
            })
        }
        return null
    }, [
        inventoryResolvedProfiles,
        isInventoryWorkbench,
        isSiocsMfcsWorkbench,
        reconView,
        selectedTransactionFamilies,
        selectedTransactionPhases,
        t,
    ])
    const inventoryStatusSystem = isInventoryWorkbench
        ? (inventoryPresentation?.counterpartySystem || null)
        : targetSystem

    useEffect(() => {
        reconApi.getStores(reconView).then(setStores).catch(console.error)
    }, [reconView, mfcsDemoMode])

    useEffect(() => {
        setSelectedRegisters([])
        setSelectedTransactionTypes([])
        setSelectedTransactionFamilies([])
        setSelectedTransactionPhases([])
        setContentTab('workbench')
    }, [reconView])

    useEffect(() => {
        if (!inventoryPhaseFilterEnabled && selectedTransactionPhases.length > 0) {
            setSelectedTransactionPhases([])
        }
    }, [inventoryPhaseFilterEnabled, selectedTransactionPhases.length])

    useEffect(() => {
        if (!reconView) {
            setRegisters([])
            setTransactionTypes([])
            setTransactionFamilies([])
            return
        }

        if (isTransactionFamilyScoped) {
            reconApi.getTransactionFamilies(
                selectedStores.length ? selectedStores : null,
                reconView
            )
                .then(setTransactionFamilies)
                .catch(console.error)
            setRegisters([])
            setTransactionTypes([])
            return
        }

        reconApi.getRegisters(
            selectedStores.length ? selectedStores : null,
            reconView
        )
            .then(setRegisters)
            .catch(console.error)
        setTransactionTypes([])
        setTransactionFamilies([])
    }, [selectedStores, reconView, isTransactionFamilyScoped, mfcsDemoMode])

    useEffect(() => {
        if (!reconView) {
            setKpis({})
            setSelectedKpi(null)
            setDetailData([])
            setSelectedDetailRow(null)
            setAnalytics(null)
            return
        }

        const load = async () => {
            setLoadingKpis(true)
            try {
                const stats = await reconApi.getDashboard({
                    storeIds: selectedStores,
                    transactionFamilies: isTransactionFamilyScoped ? selectedTransactionFamilies : [],
                    transactionPhases: isTransactionFamilyScoped ? selectedTransactionPhases : [],
                    reconView,
                    fromBusinessDate: fromDate || null,
                    toBusinessDate: toDate || null,
                })

                setKpis({
                    total: stats.totalTransactions,
                    matched: stats.matched,
                    missingInSiocs: stats.missingInSiocs,
                    quantityMismatch: stats.quantityMismatch,
                    totalMismatch: stats.transactionTotalMismatch,
                    itemMissing: stats.itemMissing,
                    duplicateTransactions: stats.duplicateTransactions,
                    processingPending: stats.processingPending,
                    awaitingSim: stats.awaitingSim,
                    processingFailed: stats.processingFailed,
                    matchRate: stats.matchRate,
                    asOf: stats.asOf,
                    byTransactionFamily: stats.byTransactionFamily || {},
                    transactionFamilyVolumes: stats.transactionFamilyVolumes || [],
                    sourceQuantityTotal: stats.sourceQuantityTotal,
                    targetQuantityTotal: stats.targetQuantityTotal,
                    quantityVarianceTotal: stats.quantityVarianceTotal,
                    quantityMetricsTransactionCount: stats.quantityMetricsTransactionCount || 0,
                })
            } catch (e) {
                console.error('Failed to load KPIs', e)
            } finally {
                setLoadingKpis(false)
            }
        }

        load()
        setSelectedKpi(null)
        setSelectedKpiFilter(null)
        setDetailData([])
        setSelectedDetailRow(null)
        setDetailTitle('')
        setDetailTransactionFamilyFocus(null)
        setPage(0)
    }, [reconView, selectedStores, selectedTransactionFamilies, selectedTransactionPhases, fromDate, toDate, isTransactionFamilyScoped, mfcsDemoMode])

    useEffect(() => {
        if (!reconView) {
            setAnalytics(null)
            return
        }

        const loadAnalytics = async () => {
            setLoadingAnalytics(true)
            try {
                const response = await reconApi.getDashboardAnalytics({
                    storeIds: selectedStores,
                    wkstnIds: isTransactionFamilyScoped ? [] : selectedRegisters,
                    transactionTypes: isTransactionFamilyScoped ? [] : selectedTransactionTypes,
                    transactionFamilies: isTransactionFamilyScoped ? selectedTransactionFamilies : [],
                    transactionPhases: isTransactionFamilyScoped ? selectedTransactionPhases : [],
                    reconView,
                })
                setAnalytics(response)
            } catch (e) {
                console.error('Failed to load dashboard analytics', e)
            } finally {
                setLoadingAnalytics(false)
            }
        }

        loadAnalytics()
    }, [reconView, selectedStores, selectedRegisters, selectedTransactionTypes, selectedTransactionFamilies, selectedTransactionPhases, isTransactionFamilyScoped, mfcsDemoMode])

    useEffect(() => {
        if (!isSimWorkbench || !reconView || loadingKpis || selectedKpi || Object.keys(kpis).length === 0) {
            return
        }

        const defaultSelection = getDefaultSimWorkbenchSelection(kpis, t)
        setDetailTitle(defaultSelection.title)
        setSelectedKpi(defaultSelection.key)
        setSelectedKpiFilter(defaultSelection.key)
        setPage(0)
    }, [isSimWorkbench, reconView, loadingKpis, selectedKpi, kpis, t])

    useEffect(() => {
        if (!selectedKpi || !reconView) return

        const inventoryReconStatuses = isSiocsMfcsWorkbench && mfcsDemoMode
            ? getSiocsMfcsReconStatuses(selectedKpiFilter || selectedKpi, inventoryPresentation)
            : []
        const reconStatus = inventoryReconStatuses.length > 0
            ? null
            : getKpiStatus(tabId, selectedKpiFilter || selectedKpi, inventoryStatusSystem)

        const load = async () => {
            setLoadingDetail(true)
            try {
                const result = await reconApi.getTransactions({
                    storeIds: selectedStores.length
                        ? selectedStores
                        : undefined,
                    wkstnIds: !isTransactionFamilyScoped && selectedRegisters.length
                        ? selectedRegisters
                        : undefined,
                    transactionTypes: !isTransactionFamilyScoped && selectedTransactionTypes.length
                        ? selectedTransactionTypes
                        : undefined,
                    transactionFamilies: isTransactionFamilyScoped && detailScopedTransactionFamilies.length
                        ? detailScopedTransactionFamilies
                        : undefined,
                    transactionPhases: isTransactionFamilyScoped && selectedTransactionPhases.length
                        ? selectedTransactionPhases
                        : undefined,
                    reconView,
                    fromBusinessDate: fromDate || undefined,
                    toBusinessDate: toDate || undefined,
                    reconStatuses: inventoryReconStatuses.length > 0 ? inventoryReconStatuses : undefined,
                    reconStatus: reconStatus || undefined,
                    page,
                    size: PAGE_SIZE,
                })

                const prioritizedContent = [...(result.content || [])].sort((left, right) => {
                    const rightPriority = getPriorityContext(right, inventoryStatusSystem, t)
                    const leftPriority = getPriorityContext(left, inventoryStatusSystem, t)
                    if (rightPriority.sortWeight !== leftPriority.sortWeight) {
                        return rightPriority.sortWeight - leftPriority.sortWeight
                    }
                    return String(right.updatedAt || right.reconciledAt || '').localeCompare(
                        String(left.updatedAt || left.reconciledAt || '')
                    )
                })

                setDetailData(
                    prioritizedContent.map((item) => {
                        const statusContext = isInventoryWorkbench
                            ? getSiocsMfcsStatusPresentation(item.reconStatus, inventoryPresentation, t)
                            : getStatusPresentation(item.reconStatus, targetSystem, t)
                        const row = {
                            __rowKey: item.transactionKey,
                            __meta: item,
                            __statusTone: getStatusTone(item.reconStatus, inventoryStatusSystem),
                            'Transaction ID': getTransactionIdFromExternalId(
                                item.externalId,
                                '-'
                            ),
                            Store: item.storeId,
                            ...(isTransactionFamilyScoped ? {'Transaction Family': formatSiocsMfcsTransactionFamily(item.transactionFamily, t) || '-'} : {Register: item.wkstnId ?? '-'}),
                            'Business Date':
                                item.businessDateDisplay || item.businessDate,
                            'Transaction Type': item.transactionType,
                            Status: statusContext.label,
                            'Reconciled At': formatDateTimeValue(item.reconciledAt),
                        }
                        return row
                    })
                )
                setSelectedDetailRow(
                    prioritizedContent.length > 0
                        ? (
                            isSimWorkbench && (selectedKpiFilter || selectedKpi) === 'processingPending'
                                ? prioritizedContent.find((item) => getTransactionIdFromExternalId(item.externalId, '') === '86') || prioritizedContent[0]
                                : prioritizedContent[0]
                        )
                        : null
                )
                setTotalElements(result.totalElements || 0)
            } catch (e) {
                console.error('Failed to load detail', e)
            } finally {
                setLoadingDetail(false)
            }
        }

        load()
    }, [
        selectedKpi,
        page,
        selectedStores,
        selectedRegisters,
        selectedTransactionTypes,
        detailScopedTransactionFamilies,
        selectedTransactionPhases,
        fromDate,
        toDate,
        reconView,
        isTransactionFamilyScoped,
        selectedKpiFilter,
        targetSystem,
        inventoryStatusSystem,
        t,
        isSimWorkbench,
        isInventoryWorkbench,
        isSiocsMfcsWorkbench,
        inventoryPresentation,
        mfcsDemoMode,
    ])

    const handleKpiClick = (title, key, filterKey = key) => {
        setDetailTitle(title)
        setSelectedKpi(key)
        setSelectedKpiFilter(filterKey)
        setDetailTransactionFamilyFocus(null)
        setPage(0)
        setSelectedDetailRow(null)
        setContentTab('workbench')
    }

    const pageStart =
        totalElements === 0 ? 0 : page * PAGE_SIZE + 1
    const pageEnd =
        totalElements === 0
            ? 0
            : Math.min((page + 1) * PAGE_SIZE, totalElements)

    const pendingTotal = (kpis.processingPending || 0) + (kpis.awaitingSim || 0)
    const exceptionCount = Math.max(
        (kpis.total || 0) - (kpis.matched || 0) - (kpis.missingInSiocs || 0) - pendingTotal,
        0
    )
    const exceptionRate = (kpis.total || 0) > 0
        ? Math.round((exceptionCount / kpis.total) * 10000) / 100
        : 0
    const laneHealth = (() => {
        if (isInventoryWorkbench) {
            if (!(kpis.total || 0)) {
                return {
                    label: t('No activity'),
                    tone: 'info',
                    summary: t('No transactions matched the current filter set.'),
                    details: [
                        t('Adjust filters or widen the business date range to inspect lane performance.'),
                    ],
                }
            }
            if ((analytics?.slaSummary?.breachedCases || 0) > 0 || (kpis.missingInSiocs || 0) > 0 || exceptionCount > 0) {
                return {
                    label: t('Attention Required'),
                    tone: 'error',
                    summary: t('The {lane} lane has active exceptions or SLA risk that need review.', {
                        lane: inventoryPresentation?.laneTitle || t('inventory'),
                    }),
                    details: [
                        pendingTotal > 0
                            ? t('{count} transactions are still waiting on the next business event or counterparty processing.', {count: pendingTotal})
                            : t('No transactions are currently waiting on the next business event or counterparty processing.'),
                        (kpis.missingInSiocs || 0) > 0
                            ? t('{count} transactions are missing in the counterparty system.', {count: kpis.missingInSiocs})
                            : t('No missing counterparty transactions were detected.'),
                        exceptionCount > 0
                            ? t('{count} transactions currently require exception review.', {count: exceptionCount})
                            : t('No exception-classified transactions are open right now.'),
                        (analytics?.slaSummary?.breachedCases || 0) > 0
                            ? t('{count} cases are already past SLA.', {count: analytics.slaSummary.breachedCases})
                            : t('No active SLA breaches were found.'),
                    ],
                }
            }
            if (pendingTotal > 0) {
                return {
                    label: t('Watch Closely'),
                    tone: 'warning',
                    summary: t('The lane is mostly healthy, but counterparty processing or the next business event is still pending.'),
                    details: [
                        t('{count} transactions are still waiting for counterparty processing or the next business event.', {count: pendingTotal}),
                        t('No missing transactions or material mismatches were detected.'),
                        t('Monitor this queue and escalate only if pending items breach SLA.'),
                    ],
                }
            }
            return {
                label: t('Healthy'),
                tone: 'success',
                summary: t('The {lane} lane is reconciling cleanly for the selected filters.', {
                    lane: inventoryPresentation?.laneTitle || t('inventory'),
                }),
                details: [
                    t('No pending processing backlog was detected.'),
                    t('No missing transactions or open exception drivers were detected.'),
                    t('{matchRate}% of the visible population is matched.', {matchRate: Math.round(kpis.matchRate || 0)}),
                ],
            }
        }
        if (!(kpis.total || 0)) {
            return {
                label: t('No activity'),
                tone: 'info',
                summary: t('No transactions matched the current filter set.'),
                details: [
                    t('Adjust filters or widen the business date range to inspect lane performance.'),
                ],
            }
        }
        if ((analytics?.slaSummary?.breachedCases || 0) > 0 || (kpis.missingInSiocs || 0) > 0 || exceptionCount > 0) {
            return {
                label: t('Attention Required'),
                tone: 'error',
                summary: t(`The ${targetSystem} lane has active exceptions or SLA risk that need review.`),
                details: [
                    pendingTotal > 0
                        ? t(`${pendingTotal} transactions are still waiting on ${targetSystem}.`)
                        : t(`No transactions are currently waiting on ${targetSystem}.`),
                    (kpis.missingInSiocs || 0) > 0
                        ? t(`${kpis.missingInSiocs} transactions are missing in ${targetSystem}.`)
                        : t(`No missing transactions were detected in ${targetSystem}.`),
                    exceptionCount > 0
                        ? t(`${exceptionCount} transactions currently require exception review.`)
                        : t('No exception-classified transactions are open right now.'),
                    (analytics?.slaSummary?.breachedCases || 0) > 0
                        ? t(`${analytics.slaSummary.breachedCases} cases are already past SLA.`)
                        : t('No active SLA breaches were found.'),
                ],
            }
        }
        if (pendingTotal > 0) {
            return {
                label: t('Watch Closely'),
                tone: 'warning',
                summary: t(`The lane is mostly healthy, but ${targetSystem} processing is still in flight.`),
                details: [
                    t(`${pendingTotal} transactions are still pending in ${targetSystem}.`),
                    t(`No missing transactions or material mismatches were detected.`),
                    t(`Monitor this queue and escalate only if pending items breach SLA.`),
                ],
            }
        }
        return {
            label: t('Healthy'),
            tone: 'success',
            summary: t(`The Xstore vs ${targetSystem} lane is reconciling cleanly for the selected filters.`),
            details: [
                t('No pending processing backlog was detected.'),
                t('No missing transactions or open exception drivers were detected.'),
                t(`${Math.round(kpis.matchRate || 0)}% of the visible population is matched.`),
            ],
        }
    })()
    const recommendedFocus = (() => {
        const pendingOptions = (analytics?.slaSummary?.breachedCases || 0) > 0
            ? [
                {
                    cardKey: 'awaitingSim',
                    title: isInventoryWorkbench ? inventoryPresentation.summaryLabels.awaiting : t('Awaiting SIM Processing'),
                    value: kpis.awaitingSim || 0,
                    filterKey: 'awaitingSim',
                    cta: isInventoryWorkbench ? t('Review next-event records') : t('Review awaiting SIM records'),
                },
                {
                    cardKey: 'processingPending',
                    title: isInventoryWorkbench ? inventoryPresentation.summaryLabels.pending : getProcessingPendingKpiTitle(tabId, t),
                    value: kpis.processingPending || 0,
                    filterKey: 'processingPending',
                    cta: isInventoryWorkbench ? t('Review pending counterparty records') : t(`Review pending ${targetSystem} records`),
                },
            ]
            : [
                {
                    cardKey: 'processingPending',
                    title: isInventoryWorkbench ? inventoryPresentation.summaryLabels.pending : getProcessingPendingKpiTitle(tabId, t),
                    value: kpis.processingPending || 0,
                    filterKey: 'processingPending',
                    cta: isInventoryWorkbench ? t('Review pending counterparty records') : t(`Review pending ${targetSystem} records`),
                },
                {
                    cardKey: 'awaitingSim',
                    title: isInventoryWorkbench ? inventoryPresentation.summaryLabels.awaiting : t('Awaiting SIM Processing'),
                    value: kpis.awaitingSim || 0,
                    filterKey: 'awaitingSim',
                    cta: isInventoryWorkbench ? t('Review next-event records') : t('Review awaiting SIM records'),
                },
            ]

        const options = [
            {
                cardKey: 'missingInSiocs',
                title: isInventoryWorkbench ? inventoryPresentation.summaryLabels.missing : getMissingKpiTitle(tabId, t),
                value: kpis.missingInSiocs || 0,
                filterKey: 'missingInSiocs',
                cta: isInventoryWorkbench ? t('Review missing counterparty records') : t(`Review missing ${targetSystem} records`),
            },
            {
                cardKey: 'quantityMismatch',
                title: t('Quantity Mismatch'),
                value: kpis.quantityMismatch || 0,
                filterKey: 'quantityMismatch',
                cta: t('Review quantity mismatches'),
            },
            {
                cardKey: 'itemMissing',
                title: t('Item Missing'),
                value: kpis.itemMissing || 0,
                filterKey: 'itemMissing',
                cta: t('Review item-level gaps'),
            },
            {
                cardKey: 'duplicateTransactions',
                title: isInventoryWorkbench ? inventoryPresentation.exceptionLabels.duplicate : getDuplicateKpiTitle(tabId, t),
                value: kpis.duplicateTransactions || 0,
                filterKey: 'duplicateTransactions',
                cta: isInventoryWorkbench ? t('Review duplicate transaction records') : t(`Review duplicate ${targetSystem} postings`),
            },
            ...pendingOptions,
        ]
        return options.find((item) => item.value > 0) || null
    })()
    const summaryCards = isInventoryWorkbench
        ? [
            {
                title: inventoryPresentation.summaryLabels.total,
                key: 'total',
                value: kpis.total || 0,
                tone: 'neutral',
                filterKey: 'total',
                supportingText: `${inventoryPresentation.familySummary} / ${inventoryPresentation.phaseSummary}`,
                definitionText: inventoryPresentation.summaryDefinitions.total,
            },
            {
                title: inventoryPresentation.summaryLabels.matched,
                key: 'matched',
                value: kpis.matched || 0,
                tone: 'success',
                filterKey: 'matched',
                supportingText: `${Math.round(kpis.matchRate || 0)}% ${t('match rate')}`,
                definitionText: inventoryPresentation.summaryDefinitions.matched,
            },
            {
                title: inventoryPresentation.summaryLabels.missing,
                key: 'missingInSiocs',
                value: kpis.missingInSiocs || 0,
                tone: 'error',
                filterKey: 'missingInSiocs',
                supportingText: t('Investigate counterparty gaps'),
                definitionText: inventoryPresentation.summaryDefinitions.missing,
            },
            {
                title: inventoryPresentation.summaryLabels.pending,
                key: 'processingPending',
                value: kpis.processingPending || 0,
                tone: 'info',
                filterKey: 'processingPending',
                supportingText: t('Counterparty update still processing'),
                definitionText: inventoryPresentation.summaryDefinitions.pending,
            },
            {
                title: inventoryPresentation.summaryLabels.awaiting,
                key: 'awaitingSim',
                value: kpis.awaitingSim || 0,
                tone: 'warning',
                filterKey: 'awaitingSim',
                supportingText: t('Waiting for the next business event'),
                definitionText: inventoryPresentation.summaryDefinitions.awaiting,
            },
            {
                title: inventoryPresentation.summaryLabels.exceptions,
                key: 'exceptionCount',
                value: exceptionCount,
                tone: 'error',
                supportingText: exceptionCount > 0 ? t('Requires review') : t('No open exceptions'),
                definitionText: inventoryPresentation.summaryDefinitions.exceptions,
            },
        ]
        : isSimWorkbench
            ? [
                {
                    title: t('Total Transactions'),
                    key: 'total',
                    value: kpis.total || 0,
                    tone: 'neutral',
                    filterKey: 'total',
                    supportingText: t('All in scope'),
                    definitionText: t('All Xstore transactions in scope for the selected store, register, and date filters.'),
                },
                {
                    title: t('Matched'),
                    key: 'matched',
                    value: kpis.matched || 0,
                    tone: 'success',
                    filterKey: 'matched',
                    supportingText: `${Math.round(kpis.matchRate || 0)}% ${t('match rate')}`,
                    definitionText: t('Reconciled successfully between Xstore and SIM.'),
                },
                {
                    title: getMissingKpiTitle(tabId, t),
                    key: 'missingInSiocs',
                    value: kpis.missingInSiocs || 0,
                    tone: 'error',
                    filterKey: 'missingInSiocs',
                    supportingText: t('Expected in SIM, not found'),
                    definitionText: t('Transactions expected in SIM but not found, requiring investigation.'),
                },
                {
                    title: t('Processing pending'),
                    key: 'processingPending',
                    value: kpis.processingPending || 0,
                    tone: 'info',
                    filterKey: 'processingPending',
                    supportingText: t('Received in SIM queue'),
                    definitionText: t('Received by SIM, still processing.'),
                },
                {
                    title: t('Awaiting SIM'),
                    key: 'awaitingSim',
                    value: kpis.awaitingSim || 0,
                    tone: 'warning',
                    filterKey: 'awaitingSim',
                    supportingText: t('Awaiting receipt in SIM'),
                    definitionText: t('Transactions have not yet been received in SIM or are still waiting in SIM processing.'),
                },
                {
                    title: t('Exceptions'),
                    key: 'exceptionCount',
                    value: exceptionCount,
                    tone: 'error',
                    supportingText: exceptionCount > 0 ? t('Requires review') : t('No open exceptions'),
                    definitionText: t('Transactions not accounted for in matched, missing, pending, or awaiting outcomes.'),
                },
            ]
            : getKpiSections(tabId, t).summary.map((kpi) => ({
                ...kpi,
                value: kpi.key === 'processingPending'
                    ? (kpis.processingPending || 0)
                    : kpi.key === 'exceptionCount'
                        ? exceptionCount
                        : (kpis[kpi.key] || 0),
                tone: ({
                    total: 'neutral',
                    matched: 'success',
                    missingInSiocs: 'error',
                    processingPending: 'info',
                    awaitingSim: 'warning',
                    exceptionCount: 'error',
                })[kpi.key] || 'info',
                filterKey: kpi.key === 'exceptionCount' ? undefined : kpi.key,
                supportingText: ({
                    total: t('All in scope'),
                    matched: `${Math.round(kpis.matchRate || 0)}% ${t('match rate')}`,
                    missingInSiocs: t(`Expected in ${targetSystem}, not found`),
                    processingPending: t(`Still processing in ${targetSystem}`),
                    awaitingSim: t(`Awaiting receipt in ${targetSystem}`),
                    exceptionCount: exceptionCount > 0 ? t('Requires review') : t('No open exceptions'),
                })[kpi.key] || '',
                definitionText: ({
                    total: t('All transactions in scope for the current filters.'),
                    matched: t(`Transactions reconciled successfully between source and ${targetSystem}.`),
                    missingInSiocs: t(`Transactions expected in ${targetSystem} but not found, requiring investigation.`),
                    processingPending: t(`Transactions received by ${targetSystem} and still processing.`),
                    awaitingSim: t(`Transactions are awaiting receipt in ${targetSystem} or are still waiting in ${targetSystem} processing.`),
                    exceptionCount: t('Transactions not accounted for in matched, missing, pending, or awaiting outcomes.'),
                })[kpi.key] || '',
            }))
    const summaryDefinitions = hasResultsWorkspace
        ? summaryCards
            .filter((card) => card.definitionText)
            .map((card) => ({
                title: card.title,
                description: card.definitionText,
            }))
        : []
    const issueCards = (isInventoryWorkbench
        ? [
            {key: 'processingPending', title: inventoryPresentation.exceptionLabels.pending},
            {key: 'awaitingSim', title: inventoryPresentation.exceptionLabels.awaiting},
            {key: 'duplicateTransactions', title: inventoryPresentation.exceptionLabels.duplicate},
            {key: 'itemMissing', title: inventoryPresentation.exceptionLabels.itemMissing},
            {key: 'quantityMismatch', title: inventoryPresentation.exceptionLabels.quantityMismatch},
        ]
        : getKpiSections(tabId, t).issues
    ).map((kpi) => {
        const value = kpis[kpi.key] || 0
        const supportByKey = isInventoryWorkbench
            ? {
                processingPending: value > 0
                    ? t('Monitor delayed counterparty updates and escalate if they breach SLA.')
                    : t('No delayed counterparty updates are open right now.'),
                awaitingSim: value > 0
                    ? t('These transactions are waiting for the next business event needed to complete reconciliation.')
                    : t('No transactions are currently waiting on the next business event.'),
                duplicateTransactions: value > 0
                    ? t('Review duplicate postings and route the case for exception handling if needed.')
                    : t('No duplicate transaction postings were detected.'),
                quantityMismatch: value > 0
                    ? t('Review quantity variance across the inventory lane for the selected family scope.')
                    : t('No quantity mismatch records were detected.'),
                itemMissing: value > 0
                    ? t('Inspect missing item lines across the compared payloads.')
                    : t('No missing item records were detected.'),
                totalMismatch: value > 0
                    ? t('Review status and payload conflicts across the lane.')
                    : t('No status conflict records were detected.'),
            }
            : {
                processingPending: value > 0
                    ? t(`Monitor pending records and escalate if ${targetSystem} does not complete in time.`)
                    : t(`No pending ${targetSystem} backlog right now.`),
                awaitingSim: value > 0
                    ? t(`Transactions are awaiting receipt in ${targetSystem} or are still waiting in ${targetSystem} processing. Open the filtered queue to monitor arrival and age.`)
                    : t(`No transactions are currently awaiting receipt in ${targetSystem} or waiting in processing.`),
                duplicateTransactions: value > 0
                    ? t(`Open the filtered records to investigate duplicate downstream postings.`)
                    : t(`No duplicate ${targetSystem} postings detected.`),
                quantityMismatch: value > 0
                    ? t('Review line-level quantity variance and affected items.')
                    : t('No quantity mismatch records detected.'),
                itemMissing: value > 0
                    ? t('Open filtered records to inspect missing item lines.')
                    : t('No missing item records detected.'),
                totalMismatch: value > 0
                    ? t('Review amount-level variance and tender totals.')
                    : t('No transaction total mismatch records detected.'),
            }
        return {
            ...kpi,
            value,
            tone: value > 0
                ? (kpi.key === 'processingPending' || kpi.key === 'awaitingSim' ? 'warning' : 'error')
                : 'success',
            supportingText: supportByKey[kpi.key] || t('Open filtered records for review.'),
        }
    }).sort((left, right) => {
        if ((left.value > 0) !== (right.value > 0)) {
            return left.value > 0 ? -1 : 1
        }
        return right.value - left.value
    })
    const workbenchIssueCards = hasResultsWorkspace
        ? issueCards.filter((card) => !['processingPending', 'awaitingSim'].includes(card.key))
        : issueCards
    const issueDefinitions = workbenchIssueCards.map((card) => ({
        title: card.title,
        description: (isInventoryWorkbench
            ? {
                duplicateTransactions: t('Transactions posted more than once in the counterparty system.'),
                itemMissing: t('One or more expected item lines are missing across the inventory lane.'),
                quantityMismatch: t('Item quantities differ across the inventory reconciliation lane.'),
                totalMismatch: t('The lane has a status or payload conflict requiring review.'),
                processingPending: t('The counterparty update was received and is still processing.'),
                awaitingSim: t('The lane is waiting for the next business event needed to complete reconciliation.'),
            }
            : {
                duplicateTransactions: t(`Transactions posted more than once in ${targetSystem}.`),
                itemMissing: t('One or more expected item lines are missing between systems.'),
                quantityMismatch: t(`Item quantities differ between Xstore and ${targetSystem}.`),
                totalMismatch: t('Transaction totals or tender values do not align between systems.'),
                processingPending: t(`Transactions are received by ${targetSystem} and still processing.`),
                awaitingSim: t(`Transactions are awaiting receipt in ${targetSystem} or are still waiting in ${targetSystem} processing.`),
            }
        )[card.key] || t('Transactions requiring investigation and follow-up.'),
    }))
    const quantityCoverageRate = (kpis.total || 0) > 0
        ? Math.round(((kpis.quantityMetricsTransactionCount || 0) / kpis.total) * 10000) / 100
        : 0
    const volumeValueCards = isInventoryWorkbench
        ? [
            {
                key: 'quantityMetricsTransactionCount',
                title: inventoryPresentation.volumeLabels.coverage,
                value: kpis.quantityMetricsTransactionCount || 0,
                tone: (kpis.quantityMetricsTransactionCount || 0) > 0 ? 'info' : 'neutral',
                supportingText: `${quantityCoverageRate}% ${t('coverage')}`,
            },
            {
                key: 'sourceQuantityTotal',
                title: inventoryPresentation.volumeLabels.leftQuantity,
                value: kpis.sourceQuantityTotal ?? 0,
                tone: 'neutral',
                supportingText: t('{system}-side quantity total', {
                    system: inventoryPresentation.originSystem || t('Origin'),
                }),
            },
            {
                key: 'targetQuantityTotal',
                title: inventoryPresentation.volumeLabels.rightQuantity,
                value: kpis.targetQuantityTotal ?? 0,
                tone: 'neutral',
                supportingText: t('{system}-side quantity total', {
                    system: inventoryPresentation.counterpartySystem || t('Counterparty'),
                }),
            },
            {
                key: 'quantityVarianceTotal',
                title: inventoryPresentation.volumeLabels.variance,
                value: kpis.quantityVarianceTotal ?? 0,
                tone: Math.abs(Number(kpis.quantityVarianceTotal || 0)) > 0 ? 'warning' : 'success',
                supportingText: t('Source minus target'),
            },
        ]
        : []
    const familyVolumeRows = isInventoryWorkbench
        ? [...(kpis.transactionFamilyVolumes || [])]
            .filter((item) => item?.transactionFamily)
            .sort((left, right) => (right.transactionCount || 0) - (left.transactionCount || 0))
        : []
    const detailSubtitle = isInventoryWorkbench
        ? (() => {
            const quantityLabel = `${totalElements} ${t(totalElements === 1 ? 'transaction' : 'transactions')}`
            const familyFocusSuffix = detailTransactionFamilyLabel
                ? ` ${t('Filtered to {family}.', {family: detailTransactionFamilyLabel})}`
                : ''
            switch (selectedKpiFilter || selectedKpi) {
                case 'matched':
                    return `${quantityLabel} ${t('reconciled successfully across the inventory lane.')}${familyFocusSuffix}`
                case 'missingInSiocs':
                    return `${quantityLabel} ${t('were expected in the counterparty system but were not found and require investigation.')}${familyFocusSuffix}`
                case 'processingPending':
                    return `${quantityLabel} ${t('are waiting for counterparty processing to complete.')}${familyFocusSuffix}`
                case 'awaitingSim':
                    return `${quantityLabel} ${t('are waiting for the next business event needed to complete reconciliation.')}${familyFocusSuffix}`
                case 'duplicateTransactions':
                    return `${quantityLabel} ${t('show duplicate postings that require review.')}${familyFocusSuffix}`
                case 'quantityMismatch':
                    return `${quantityLabel} ${t('show quantity differences across the inventory lane.')}${familyFocusSuffix}`
                case 'itemMissing':
                    return `${quantityLabel} ${t('show missing item lines that require review.')}${familyFocusSuffix}`
                case 'total':
                default:
                    return `${quantityLabel} ${t('in scope for the selected filters.')}${familyFocusSuffix}`
            }
        })()
        : getDetailTableSubtitle(selectedKpiFilter || selectedKpi, totalElements, targetSystem, t)
    const renderedDetailData = useMemo(
        () => detailData.map((row) => {
            const tone = row.__statusTone || 'info'
            return {
                ...row,
                __cellRenderers: {
                    ...(row.__cellRenderers || {}),
                    Status: (
                        <Chip
                            label={row.Status}
                            size="small"
                            sx={{
                                ...getToneChipStyles(tone, palette),
                                fontWeight: 700,
                                height: 24,
                                '& .MuiChip-label': {
                                    px: 1,
                                },
                            }}
                        />
                    ),
                },
            }
        }),
        [detailData, palette]
    )
    const selectedStatusContext = selectedDetailRow
        ? (
            isInventoryWorkbench
                ? getSiocsMfcsStatusPresentation(selectedDetailRow.reconStatus, inventoryPresentation, t)
                : getStatusPresentation(selectedDetailRow.reconStatus, targetSystem, t)
        )
        : null
    const selectedPriorityContext = selectedDetailRow
        ? getPriorityContext(selectedDetailRow, inventoryStatusSystem, t)
        : null
    const selectedStatusTone = selectedDetailRow
        ? getStatusTone(selectedDetailRow.reconStatus, inventoryStatusSystem)
        : 'info'
    const selectedRecordSummary = selectedDetailRow
        ? {
            transactionId: getTransactionIdFromExternalId(selectedDetailRow.externalId, '-'),
            transactionKey: selectedDetailRow.transactionKey || '-',
            reconView: selectedDetailRow.reconView || reconView || '-',
            storeId: selectedDetailRow.storeId || '-',
            register: selectedDetailRow.wkstnId ?? '-',
            businessDate: selectedDetailRow.businessDateDisplay || selectedDetailRow.businessDate || '-',
            transactionFamily: formatSiocsMfcsTransactionFamily(selectedDetailRow.transactionFamily, t) || '-',
            transactionPhase: formatSiocsMfcsTransactionPhase(selectedDetailRow.transactionPhase, t) || '-',
            transactionType: selectedDetailRow.transactionType || '-',
            reconStatus: selectedStatusContext?.label || '-',
            technicalStatus: selectedDetailRow.reconStatus || '-',
            exceptionType: selectedStatusContext?.issueType || '-',
            matchBand: selectedDetailRow.matchBand || '-',
            matchScore: selectedDetailRow.matchScore ?? '-',
            priority: selectedPriorityContext?.label || '-',
            whyFlagged: selectedStatusContext?.explanation || '-',
            nextAction: selectedStatusContext?.nextAction || '-',
            lastEventAt: formatDateTimeValue(selectedDetailRow.updatedAt || selectedDetailRow.reconciledAt),
            age: formatRelativeAge(selectedDetailRow.updatedAt || selectedDetailRow.reconciledAt || selectedDetailRow.businessDate, t),
            amount: formatCurrencyValue(selectedDetailRow.transactionAmount),
            amountVariance: formatCurrencyValue(selectedDetailRow.amountVariance),
            quantityImpact: formatNumberValue(selectedDetailRow.quantityImpact),
            affectedItems: selectedDetailRow.affectedItemCount ?? '-',
            queueHint: canViewExceptionQueue
                ? t('Open in Exception Queues to review or create a case for this transaction.')
                : t('Exception Queue access is not available for the current role.'),
        }
        : null
    const selectedRecordFields = selectedRecordSummary
        ? [
            {label: t('Transaction Key'), value: selectedRecordSummary.transactionKey},
            {label: t('Recon View'), value: selectedRecordSummary.reconView},
            {label: t('Store'), value: selectedRecordSummary.storeId},
            {label: t('Business Date'), value: selectedRecordSummary.businessDate},
            ...(isInventoryWorkbench ? [{label: t('Transaction Family'), value: selectedRecordSummary.transactionFamily}] : []),
            ...(isInventoryWorkbench ? [{label: t('Transaction Phase'), value: selectedRecordSummary.transactionPhase}] : []),
            {label: t('Transaction Type'), value: selectedRecordSummary.transactionType},
            {
                label: t('Recon Status'),
                value: (
                    <Chip
                        label={selectedRecordSummary.reconStatus}
                        size="small"
                        sx={{
                            ...getToneChipStyles(selectedStatusTone, palette),
                            fontWeight: 700,
                            height: 24,
                            '& .MuiChip-label': {
                                px: 1,
                            },
                        }}
                    />
                ),
            },
            {label: t('Match Band'), value: selectedRecordSummary.matchBand},
            {label: t('Match Score'), value: selectedRecordSummary.matchScore},
        ]
        : []

    const openRecordInExceptionQueue = (record) => {
        if (!record || !canViewExceptionQueue) {
            return
        }
        const prefill = {
            reconView: record.reconView || reconView || '',
            transactionKey: record.transactionKey || '',
            search: record.transactionKey || '',
        }
        sessionStorage.setItem(EXCEPTION_QUEUE_PREFILL_KEY, JSON.stringify(prefill))
        window.dispatchEvent(new CustomEvent(EXCEPTION_QUEUE_PREFILL_EVENT, {detail: prefill}))
        onOpenTab?.('exception-queues')
    }

    const openRecordDrillDown = (record) => {
        if (!record) {
            return
        }
        const prefill = {
            transactionKey: record.transactionKey,
            reconView: record.reconView || reconView || '',
            source: 'dashboard-kpi',
        }
        sessionStorage.setItem(TRANSACTION_DRILLDOWN_PREFILL_KEY, JSON.stringify(prefill))
        window.dispatchEvent(new CustomEvent(TRANSACTION_DRILLDOWN_PREFILL_EVENT, {detail: prefill}))
        onOpenTab?.(TRANSACTION_DRILLDOWN_TAB_ID)
    }

    const openSelectedRecordInExceptionQueue = () => openRecordInExceptionQueue(selectedDetailRow)
    const openSelectedRecordDrillDown = () => openRecordDrillDown(selectedDetailRow)
    const toggleMfcsDemoMode = () => {
        if (!mfcsDemoAvailable) {
            return
        }
        const next = !mfcsDemoMode
        setSiocsMfcsDemoEnabled(next)
        setMfcsDemoMode(next)
        setSelectedStores([])
        setSelectedRegisters([])
        setSelectedTransactionTypes([])
        setSelectedTransactionFamilies([])
        setSelectedTransactionPhases([])
        setFromDate('')
        setToDate('')
        setSelectedKpi(null)
        setSelectedKpiFilter(null)
        setSelectedDetailRow(null)
        setDetailData([])
        setDetailTransactionFamilyFocus(null)
        setPage(0)
        setContentTab('workbench')
    }
    const focusTrendStore = (storeId) => {
        if (!storeId) {
            return
        }
        setSelectedStores([String(storeId)])
        setSelectedRegisters([])
        setSelectedTransactionTypes([])
        setSelectedTransactionFamilies([])
        setSelectedTransactionPhases([])
        setDetailTransactionFamilyFocus(null)
        setContentTab('workbench')
    }
    const focusTrendRegister = (key) => {
        if (!key) {
            return
        }
        if (isTransactionFamilyScoped) {
            setSelectedTransactionFamilies([String(key)])
            setSelectedRegisters([])
        } else {
            setSelectedRegisters([String(key)])
        }
        setSelectedTransactionTypes([])
        setSelectedTransactionPhases([])
        setDetailTransactionFamilyFocus(null)
        setContentTab('workbench')
    }
    const openMfcsFamilyBreakdownDetail = (family) => {
        if (!family) {
            return
        }
        setDetailTransactionFamilyFocus(String(family))
        setSelectedKpi('total')
        setSelectedKpiFilter('total')
        setDetailTitle(t('{family} Transactions', {
            family: formatSiocsMfcsTransactionFamily(family, t),
        }))
        setPage(0)
        setSelectedDetailRow(null)
        setContentTab('workbench')
    }

    const trend7Data = (analytics?.last7Days || []).map((point) => ({
        date: point.businessDate?.slice(5) || point.businessDate,
        matchRate: point.matchRate,
        missing: point.missing,
        duplicates: point.duplicates,
    }))

    const trend30Data = (analytics?.last30Days || []).map((point) => ({
        date: point.businessDate?.slice(5) || point.businessDate,
        exceptionCount:
            (point.missing || 0) +
            (point.duplicates || 0) +
            (point.quantityMismatch || 0) +
            (point.totalMismatch || 0) +
            (point.itemMissing || 0),
        total: point.totalTransactions,
    }))

    const renderCheckbox = (checked) => (
        <Box
            sx={{
                width: 14,
                height: 14,
                border: '2px solid',
                borderColor: checked ? '#2563eb' : (palette.isDark ? '#475569' : '#d1d5db'),
                borderRadius: '3px',
                backgroundColor: checked ? '#2563eb' : 'transparent',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
            }}
        >
            {checked && (
                <Box
                    sx={{
                        width: 8,
                        height: 8,
                        backgroundColor: '#fff',
                        clipPath:
                            'polygon(20% 50%,0% 70%,40% 100%,100% 20%,80% 0%,40% 60%)',
                    }}
                />
            )}
        </Box>
    )

    if (!reconView) {
        return <ComingSoonModule tabId={tabId} palette={palette} t={t}/>
    }

    return (
        <Box sx={{px: 4, py: 3, maxWidth: 1600}}>
            <Paper
                elevation={0}
                sx={{
                    p: 3,
                    mb: 4,
                    backgroundColor: palette.cardBg,
                    border: `1px solid ${palette.border}`,
                    borderRadius: '28px',
                    boxShadow: palette.isDark
                        ? '0 1px 2px rgba(0,0,0,0.18)'
                        : '0 1px 2px rgba(0,0,0,0.04)',
                    display: 'flex',
                    gap: 3,
                    flexWrap: 'wrap',
                    alignItems: 'flex-start',
                }}
            >
                <FormControl size="small" sx={{width: 220}}>
                    <InputLabel sx={{color: palette.textMuted}}>Store</InputLabel>
                    <Select
                        multiple
                        value={selectedStores}
                        onChange={(e) => {
                            const v = e.target.value
                            setSelectedStores(
                                typeof v === 'string' ? v.split(',') : v
                            )
                            setSelectedRegisters([])
                            setSelectedTransactionTypes([])
                            setSelectedTransactionFamilies([])
                            setSelectedTransactionPhases([])
                        }}
                        input={<OutlinedInput label="Store"/>}
                        MenuProps={{
                            PaperProps: {
                                style: {
                                    maxHeight: 300,
                                    backgroundColor: palette.cardBg,
                                    color: palette.text,
                                    border: `1px solid ${palette.border}`,
                                },
                            },
                        }}
                        sx={{
                            color: palette.text,
                            backgroundColor: palette.cardBg,
                            '& .MuiOutlinedInput-notchedOutline': {
                                borderColor: palette.border,
                            },
                            '&:hover .MuiOutlinedInput-notchedOutline': {
                                borderColor: palette.textSoft,
                            },
                            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                                borderColor: '#2563EB',
                            },
                            '& .MuiSvgIcon-root': {
                                color: palette.textMuted,
                            },
                        }}
                        renderValue={(sel) => (
                            <Box
                                sx={{
                                    display: 'flex',
                                    flexWrap: 'nowrap',
                                    gap: 0.5,
                                    overflow: 'hidden',
                                }}
                            >
                                {sel.length <= 2 ? (
                                    sel.map((v) => (
                                        <Chip
                                            key={v}
                                            label={v}
                                            size="small"
                                            sx={{
                                                height: 20,
                                                fontSize: '0.75rem',
                                                backgroundColor: palette.blueChipBg,
                                                color: palette.blueChipText,
                                                maxWidth: 80,
                                            }}
                                        />
                                    ))
                                ) : (
                                    <Chip
                                        label={`${sel.length} ${t('stores')}`}
                                        size="small"
                                        sx={{
                                            height: 20,
                                            fontSize: '0.75rem',
                                            backgroundColor: palette.blueChipBg,
                                            color: palette.blueChipText,
                                        }}
                                    />
                                )}
                            </Box>
                        )}
                    >
                        {selectedStores.length > 0 && (
                            <MenuItem
                                onMouseDown={(e) => {
                                    e.preventDefault()
                                    e.stopPropagation()
                                    setSelectedStores([])
                                    setSelectedRegisters([])
                                    setSelectedTransactionTypes([])
                                    setSelectedTransactionFamilies([])
                                    setSelectedTransactionPhases([])
                                }}
                                sx={{
                                    color: '#ef4444',
                                    fontSize: '0.8rem',
                                    fontWeight: 500,
                                    borderBottom: `1px solid ${palette.borderSoft}`,
                                    py: 0.75,
                                }}
                            >
                                x {t('Clear all stores')}
                            </MenuItem>
                        )}

                        {stores.map((s) => (
                            <MenuItem key={s} value={s}>
                                <Box
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 1,
                                    }}
                                >
                                    {renderCheckbox(selectedStores.includes(s))}
                                    {`${t('Store')} ${s}`}
                                </Box>
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>

                <FormControl size="small" sx={{width: 220}}>
                    <InputLabel sx={{color: palette.textMuted}}>
                        {t(isTransactionFamilyScoped ? 'Transaction Family' : 'Register')}
                    </InputLabel>
                    <Select
                        multiple
                        value={isTransactionFamilyScoped ? selectedTransactionFamilies : selectedRegisters}
                        onChange={(e) => {
                            const v = e.target.value
                            const next = typeof v === 'string' ? v.split(',') : v
                            if (isTransactionFamilyScoped) {
                                setSelectedTransactionFamilies(next)
                                setSelectedTransactionPhases([])
                            } else {
                                setSelectedRegisters(next)
                            }
                        }}
                        input={<OutlinedInput label={t(isTransactionFamilyScoped ? 'Transaction Family' : 'Register')}/>}
                        MenuProps={{
                            PaperProps: {
                                style: {
                                    maxHeight: 300,
                                    backgroundColor: palette.cardBg,
                                    color: palette.text,
                                    border: `1px solid ${palette.border}`,
                                },
                            },
                        }}
                        sx={{
                            color: palette.text,
                            backgroundColor: palette.cardBg,
                            '& .MuiOutlinedInput-notchedOutline': {
                                borderColor: palette.border,
                            },
                            '&:hover .MuiOutlinedInput-notchedOutline': {
                                borderColor: palette.textSoft,
                            },
                            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                                borderColor: '#2563EB',
                            },
                            '& .MuiSvgIcon-root': {
                                color: palette.textMuted,
                            },
                        }}
                        renderValue={(sel) => (
                            <Box
                                sx={{
                                    display: 'flex',
                                    flexWrap: 'nowrap',
                                    gap: 0.5,
                                    overflow: 'hidden',
                                }}
                            >
                                {sel.length <= 3 ? (
                                    sel.map((v) => (
                                        <Chip
                                            key={v}
                                            label={isTransactionFamilyScoped ? formatSiocsMfcsTransactionFamily(v, t) : `R${v}`}
                                            size="small"
                                            sx={{
                                                height: 20,
                                                fontSize: '0.75rem',
                                                backgroundColor: palette.blueChipBg,
                                                color: palette.blueChipText,
                                            }}
                                        />
                                    ))
                                ) : (
                                    <Chip
                                        label={`${sel.length} ${t(isTransactionFamilyScoped ? 'families' : 'registers')}`}
                                        size="small"
                                        sx={{
                                            height: 20,
                                            fontSize: '0.75rem',
                                            backgroundColor: palette.blueChipBg,
                                            color: palette.blueChipText,
                                        }}
                                    />
                                )}
                            </Box>
                        )}
                    >
                        {(isTransactionFamilyScoped ? selectedTransactionFamilies.length > 0 : selectedRegisters.length > 0) && (
                            <MenuItem
                                onMouseDown={(e) => {
                                    e.preventDefault()
                                    e.stopPropagation()
                                    if (isTransactionFamilyScoped) {
                                        setSelectedTransactionFamilies([])
                                    } else {
                                        setSelectedRegisters([])
                                    }
                                }}
                                sx={{
                                    color: '#ef4444',
                                    fontSize: '0.8rem',
                                    fontWeight: 500,
                                    borderBottom: `1px solid ${palette.borderSoft}`,
                                    py: 0.75,
                                }}
                            >
                                x {isTransactionFamilyScoped ? t('Clear all transaction families') : t('Clear all registers')}
                            </MenuItem>
                        )}

                        {(isTransactionFamilyScoped ? transactionFamilies : registers).map((option) => (
                            <MenuItem key={option} value={option}>
                                <Box
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 1,
                                    }}
                                >
                                    {renderCheckbox(
                                        isTransactionFamilyScoped
                                            ? selectedTransactionFamilies.includes(option)
                                            : selectedRegisters.includes(option)
                                    )}
                                    {isTransactionFamilyScoped
                                        ? formatSiocsMfcsTransactionFamily(option, t)
                                        : `${t('Register')} ${option}`}
                                </Box>
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>

                {isInventoryWorkbench ? (
                    <FormControl size="small" sx={{width: 220}}>
                        <InputLabel sx={{color: palette.textMuted}}>
                            {t('Transaction Type')}
                        </InputLabel>
                        <Select
                            multiple
                            value={selectedTransactionPhases}
                            disabled={!inventoryPhaseFilterEnabled}
                            onChange={(e) => {
                                const v = e.target.value
                                setSelectedTransactionPhases(typeof v === 'string' ? v.split(',') : v)
                            }}
                            input={<OutlinedInput label={t('Transaction Type')}/>}
                            renderValue={(sel) => (
                                <Box
                                    sx={{
                                        display: 'flex',
                                        flexWrap: 'nowrap',
                                        gap: 0.5,
                                        overflow: 'hidden',
                                    }}
                                >
                                    {sel.length > 0 ? (
                                        sel.map((value) => (
                                            <Chip
                                                key={value}
                                                label={formatSiocsMfcsTransactionPhase(value, t)}
                                                size="small"
                                                sx={{
                                                    height: 20,
                                                    fontSize: '0.75rem',
                                                    backgroundColor: palette.blueChipBg,
                                                    color: palette.blueChipText,
                                                }}
                                            />
                                        ))
                                    ) : (
                                        <Chip
                                            label={t('All transaction types')}
                                            size="small"
                                            sx={{
                                                height: 20,
                                                fontSize: '0.75rem',
                                                backgroundColor: palette.cardBgAlt,
                                                color: palette.textMuted,
                                            }}
                                        />
                                    )}
                                </Box>
                            )}
                            sx={{
                                color: palette.text,
                                backgroundColor: palette.cardBg,
                                '& .MuiOutlinedInput-notchedOutline': {
                                    borderColor: palette.border,
                                },
                                '&.Mui-disabled': {
                                    color: palette.textMuted,
                                    backgroundColor: palette.cardBgAlt,
                                },
                                '& .MuiSvgIcon-root': {
                                    color: palette.textMuted,
                                },
                            }}
                        >
                            {transactionPhaseOptions.map((option) => (
                                <MenuItem key={option.value} value={option.value}>
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 1,
                                        }}
                                    >
                                        {renderCheckbox(selectedTransactionPhases.includes(option.value))}
                                        {option.label}
                                    </Box>
                                </MenuItem>
                            ))}
                        </Select>
                        {!inventoryPhaseFilterEnabled ? (
                            <Typography sx={{mt: 0.6, fontSize: '0.74rem', color: palette.textMuted}}>
                                {isInventoryWorkbench
                                    ? t('Select a transaction family to unlock transaction type filtering.')
                                    : t('Transaction type filtering will be enabled when backend support is available.')}
                            </Typography>
                        ) : null}
                    </FormControl>
                ) : null}

                {isSiocsMfcsWorkbench && mfcsDemoAvailable ? (
                    <Button
                        variant={mfcsDemoMode ? 'contained' : 'outlined'}
                        size="small"
                        onClick={toggleMfcsDemoMode}
                        sx={{
                            alignSelf: 'center',
                            minWidth: 150,
                            textTransform: 'none',
                            fontWeight: 700,
                            borderRadius: 2,
                            ...(mfcsDemoMode
                                ? {
                                    backgroundColor: '#2563EB',
                                    '&:hover': {
                                        backgroundColor: '#1D4ED8',
                                    },
                                }
                                : {}),
                        }}
                    >
                        {mfcsDemoMode ? t('Sample Data On') : t('Load Sample Data')}
                    </Button>
                ) : null}

                <LocalizationProvider dateAdapter={AdapterDayjs}>
                    <ClearableDatePicker
                        label={t('From Date')}
                        value={fromDate}
                        onChange={setFromDate}
                        palette={palette}
                    />
                    <ClearableDatePicker
                        label={t('To Date')}
                        value={toDate}
                        onChange={setToDate}
                        minDate={fromDate}
                        palette={palette}
                    />
                </LocalizationProvider>

                {(selectedStores.length > 0 ||
                    selectedRegisters.length > 0 ||
                    selectedTransactionTypes.length > 0 ||
                    selectedTransactionFamilies.length > 0 ||
                    selectedTransactionPhases.length > 0 ||
                    fromDate ||
                    toDate) && (
                    <Box
                        onClick={() => {
                            setSelectedStores([])
                            setSelectedRegisters([])
                            setSelectedTransactionTypes([])
                            setSelectedTransactionFamilies([])
                            setSelectedTransactionPhases([])
                            setFromDate('')
                            setToDate('')
                        }}
                        sx={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 0.5,
                            px: 1.5,
                            py: 0.85,
                            borderRadius: 1.75,
                            border: `1px solid ${palette.dangerBorder}`,
                            backgroundColor: palette.dangerBg,
                            color: palette.dangerText,
                            fontSize: '0.8rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                            alignSelf: 'center',
                            transition: 'all 0.15s ease',
                            '&:hover': {
                                backgroundColor: palette.dangerBgHover,
                                borderColor: '#ef4444',
                            },
                        }}
                    >
                        x {t('Clear all filters')}
                    </Box>
                )}
            </Paper>

            {loadingKpis ? (
                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'center',
                        py: 6,
                    }}
                >
                    <CircularProgress/>
                </Box>
            ) : (
                <Box sx={{mb: 4, display: 'flex', flexDirection: 'column', gap: 2.5}}>
                    {hasResultsWorkspace ? (
                        <Paper
                            elevation={0}
                            sx={{
                                p: {xs: 2, md: 2.25},
                                borderRadius: 4,
                                border: `1px solid ${palette.border}`,
                                backgroundColor: palette.cardBg,
                            }}
                        >
                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                gap: 2,
                                flexWrap: 'wrap',
                            }}
                        >
                            <Box>
                                <Typography
                                    sx={{
                                        fontSize: '1rem',
                                        fontWeight: 800,
                                        color: palette.text,
                                    }}
                                >
                                    {t('Results Workspace')}
                                </Typography>
                                <Typography
                                    sx={{
                                        mt: 0.35,
                                        fontSize: '0.84rem',
                                        color: palette.textMuted,
                                    }}
                                >
                            {t('Health, exception prioritization, and record triage for the selected reconciliation lane. Filters refresh automatically as you change store, register, or date.')}
                        </Typography>
                    </Box>
                    <Chip
                        label={isInventoryWorkbench ? inventoryPresentation.laneTitle : targetSystem}
                        size="small"
                        sx={{
                            backgroundColor: palette.blueChipBg,
                                    color: palette.blueChipText,
                                    fontWeight: 700,
                                }}
                            />
                        </Box>

                        <Tabs
                            value={contentTab}
                            onChange={(_, value) => setContentTab(value)}
                            variant="scrollable"
                            scrollButtons="auto"
                            sx={{
                                mt: 2,
                                minHeight: 0,
                                '& .MuiTabs-indicator': {
                                    display: 'none',
                                },
                                '& .MuiTab-root': {
                                    minHeight: 0,
                                    px: 1.75,
                                    py: 1,
                                    mr: 1,
                                    borderRadius: 2.5,
                                    textTransform: 'none',
                                    fontWeight: 700,
                                    color: palette.textMuted,
                                    border: `1px solid ${palette.border}`,
                                    backgroundColor: palette.cardBgAlt,
                                },
                                '& .MuiTab-root.Mui-selected': {
                                    color: 'var(--brand-primary)',
                                    backgroundColor: palette.selectedBg,
                                    borderColor: 'var(--brand-primary-border)',
                                },
                            }}
                        >
                            <Tab value="workbench" label={t('Results / Workbench')}/>
                            <Tab value="trends" label={t('Trends & SLA')}/>
                        </Tabs>
                        </Paper>
                    ) : null}

                    {!hasResultsWorkspace || contentTab === 'workbench' ? (
                        <>
                            {isInventoryWorkbench ? (
                                <Paper
                                    elevation={0}
                                    sx={{
                                        p: {xs: 1.1, md: 1.25},
                                        borderRadius: 3,
                                        border: `1px solid ${palette.border}`,
                                        backgroundColor: palette.cardBgAlt,
                                    }}
                                >
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 1,
                                            flexWrap: 'wrap',
                                        }}
                                    >
                                        {inventoryPresentation.banner.eyebrow ? (
                                            <Chip
                                                label={inventoryPresentation.banner.eyebrow}
                                                size="small"
                                                sx={{
                                                    backgroundColor: palette.blueChipBg,
                                                    color: palette.blueChipText,
                                                    fontWeight: 800,
                                                }}
                                            />
                                        ) : null}
                                        <Typography
                                            sx={{
                                                fontSize: '0.84rem',
                                                fontWeight: 800,
                                                color: palette.text,
                                            }}
                                        >
                                            {inventoryPresentation.banner.title}
                                        </Typography>
                                        {inventoryPresentation.banner.directionText ? (
                                            <Typography
                                                sx={{
                                                    fontSize: '0.8rem',
                                                    color: palette.textMuted,
                                                }}
                                            >
                                                {inventoryPresentation.banner.directionText}
                                            </Typography>
                                        ) : null}
                                        <Typography
                                            sx={{
                                                flex: '1 1 360px',
                                                minWidth: 0,
                                                fontSize: '0.79rem',
                                                color: palette.textMuted,
                                                lineHeight: 1.45,
                                            }}
                                        >
                                            {inventoryPresentation.banner.description}
                                        </Typography>
                                        <Box sx={{display: 'flex', gap: 0.75, flexWrap: 'wrap', ml: {md: 'auto'}}}>
                                            {isSiocsMfcsWorkbench && mfcsDemoMode ? (
                                                <Chip
                                                    label={t('Sample Data')}
                                                    size="small"
                                                    sx={{
                                                        backgroundColor: palette.tealChipBg,
                                                        color: palette.tealChipText,
                                                        fontWeight: 700,
                                                    }}
                                                />
                                            ) : null}
                                            <Chip
                                                label={inventoryPresentation.familySummary}
                                                size="small"
                                                sx={{
                                                    backgroundColor: palette.blueChipBg,
                                                    color: palette.blueChipText,
                                                    fontWeight: 700,
                                                }}
                                            />
                                            <Chip
                                                label={inventoryPresentation.phaseSummary}
                                                size="small"
                                                sx={{
                                                    backgroundColor: palette.cardBgAlt,
                                                    color: palette.text,
                                                    fontWeight: 700,
                                                }}
                                            />
                                        </Box>
                                    </Box>
                                </Paper>
                            ) : null}

                            <Paper
                                elevation={0}
                                sx={{
                                    p: {xs: 2, md: 2.25},
                                    borderRadius: 4,
                                    border: `1px solid ${palette.border}`,
                                    backgroundColor: palette.cardBg,
                                }}
                            >
                                <Box
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between',
                                        gap: 2,
                                        mb: 1.75,
                                        flexWrap: 'wrap',
                                    }}
                                >
                                    <Box>
                                        <Typography
                                            sx={{
                                                fontSize: '0.98rem',
                                                fontWeight: 800,
                                                color: palette.text,
                                            }}
                                        >
                                            {t('Reconciliation Summary')}
                                        </Typography>
                                        <Typography
                                            sx={{
                                                mt: 0.35,
                                                fontSize: '0.82rem',
                                                color: palette.textMuted,
                                            }}
                                        >
                                            {isInventoryWorkbench
                                                ? isSiocsMfcsWorkbench
                                                    ? t('These outcome categories stay neutral unless the selected family and phase resolve to a trustworthy direction profile.')
                                                    : t('These outcome categories reconcile across the {lane} inventory lane for the selected filters.', {
                                                        lane: inventoryPresentation?.laneTitle || t('inventory'),
                                                    })
                                                : hasResultsWorkspace
                                                    ? t('These outcome categories reconcile to total transactions for the selected filters.')
                                                    : t('Top-line transaction outcomes and queue states for the selected filters.')}
                                        </Typography>
                                    </Box>
                                </Box>

                                {hasResultsWorkspace ? (
                                    <Box
                                        sx={{
                                            display: 'grid',
                                            gridTemplateColumns: {
                                                xs: '1fr',
                                                sm: 'repeat(2, minmax(0, 1fr))',
                                                xl: `repeat(${summaryCards.length}, minmax(0, 1fr))`,
                                            },
                                            gap: 1.5,
                                        }}
                                    >
                                        {summaryCards.map((card) => (
                                            <KPI
                                                key={card.key}
                                                title={card.title}
                                                value={card.value}
                                                tone={card.tone}
                                                supportingText={card.supportingText}
                                                testId={`kpi-card-${card.key}`}
                                                onClick={card.filterKey
                                                    ? () => handleKpiClick(card.title, card.key, card.filterKey)
                                                    : undefined}
                                                selected={selectedKpi === card.key}
                                            />
                                        ))}
                                    </Box>
                                ) : (
                                    <Grid container spacing={2}>
                                        {summaryCards.map((card) => (
                                            <Grid item xs={12} sm={6} md={4} xl={3} key={card.key}>
                                                <KPI
                                                    title={card.title}
                                                    value={card.value}
                                                    tone={card.tone}
                                                    testId={`kpi-card-${card.key}`}
                                                    onClick={card.filterKey
                                                        ? () => handleKpiClick(card.title, card.key, card.filterKey)
                                                        : undefined}
                                                    selected={selectedKpi === card.key}
                                                />
                                            </Grid>
                                        ))}
                                    </Grid>
                                )}

                                {summaryDefinitions.length > 0 ? (
                                    <Box
                                        sx={{
                                            mt: 1.4,
                                            p: 1.25,
                                            borderRadius: 3,
                                            border: `1px solid ${palette.borderSoft}`,
                                            backgroundColor: palette.cardBgAlt,
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                display: 'grid',
                                                gridTemplateColumns: {xs: '1fr', lg: 'repeat(2, minmax(0, 1fr))'},
                                                gap: 0.75,
                                            }}
                                        >
                                            {summaryDefinitions.map((item) => (
                                                <Box
                                                    key={item.title}
                                                    sx={{
                                                        display: 'flex',
                                                        gap: 0.4,
                                                        fontSize: '0.78rem',
                                                        color: palette.textMuted,
                                                        lineHeight: 1.45,
                                                    }}
                                                >
                                                    <Box component="span" sx={{fontWeight: 700, color: palette.text, flexShrink: 0}}>
                                                        {item.title}
                                                    </Box>
                                                    <Box component="span">{`- ${item.description}`}</Box>
                                                </Box>
                                            ))}
                                        </Box>
                                    </Box>
                                ) : null}
                            </Paper>

                            {isInventoryWorkbench ? (
                                <Paper
                                    elevation={0}
                                    sx={{
                                        p: {xs: 2, md: 2.25},
                                        borderRadius: 4,
                                        border: `1px solid ${palette.border}`,
                                        backgroundColor: palette.cardBg,
                                    }}
                                >
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'space-between',
                                            gap: 2,
                                            mb: 1.75,
                                            flexWrap: 'wrap',
                                        }}
                                    >
                                        <Box>
                                            <Typography
                                                sx={{
                                                    fontSize: '0.98rem',
                                                    fontWeight: 800,
                                                color: palette.text,
                                            }}
                                        >
                                                {inventoryPresentation.volumeLabels.title}
                                            </Typography>
                                            <Typography
                                                sx={{
                                                    mt: 0.35,
                                                    fontSize: '0.82rem',
                                                    color: palette.textMuted,
                                                }}
                                            >
                                                {inventoryPresentation.volumeLabels.note}
                                            </Typography>
                                        </Box>
                                        <Chip
                                            label={t('Quantity live now')}
                                            size="small"
                                            sx={{
                                                backgroundColor: palette.tealChipBg,
                                                color: palette.tealChipText,
                                                fontWeight: 700,
                                            }}
                                        />
                                    </Box>

                                    <Box
                                        sx={{
                                            display: 'grid',
                                            gridTemplateColumns: {xs: '1fr', md: 'repeat(4, minmax(0, 1fr))'},
                                            gap: 1.5,
                                        }}
                                    >
                                        {volumeValueCards.map((card) => (
                                            <KPI
                                                key={card.key}
                                                title={card.title}
                                                value={card.value}
                                                tone={card.tone}
                                                supportingText={card.supportingText}
                                            />
                                        ))}
                                    </Box>

                                    <Box
                                        sx={{
                                            mt: 1.4,
                                            p: 1.25,
                                            borderRadius: 3,
                                            border: `1px solid ${palette.borderSoft}`,
                                            backgroundColor: palette.cardBgAlt,
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'space-between',
                                                gap: 1,
                                                mb: familyVolumeRows.length > 0 ? 1 : 0,
                                                flexWrap: 'wrap',
                                            }}
                                        >
                                            <Typography sx={{fontSize: '0.82rem', color: palette.text, fontWeight: 700}}>
                                                {t('Transaction Family Breakdown')}
                                            </Typography>
                                            <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                {t('{count} families in scope', {count: familyVolumeRows.length})}
                                            </Typography>
                                        </Box>

                                        {familyVolumeRows.length > 0 ? (
                                            <Box
                                                sx={{
                                                    display: 'grid',
                                                    gridTemplateColumns: 'repeat(1, minmax(0, 1fr))',
                                                    gap: 0.85,
                                                    maxHeight: {xs: 420, md: 360},
                                                    overflowY: 'auto',
                                                    pr: 0.4,
                                                    scrollbarWidth: 'thin',
                                                    scrollbarColor: `${palette.scrollbarThumb} ${palette.scrollbarTrack}`,
                                                    '&::-webkit-scrollbar': {
                                                        width: 6,
                                                    },
                                                    '&::-webkit-scrollbar-track': {
                                                        backgroundColor: palette.scrollbarTrack,
                                                        borderRadius: 999,
                                                    },
                                                    '&::-webkit-scrollbar-thumb': {
                                                        backgroundColor: palette.scrollbarThumb,
                                                        borderRadius: 999,
                                                    },
                                                }}
                                            >
                                                {familyVolumeRows.map((item) => {
                                                    const isSelected = detailTransactionFamilyFocus === item.transactionFamily
                                                    return (
                                                        <Box
                                                            key={item.transactionFamily}
                                                            onClick={() => openMfcsFamilyBreakdownDetail(item.transactionFamily)}
                                                            role="button"
                                                            aria-pressed={isSelected}
                                                            sx={{
                                                                display: 'grid',
                                                                gridTemplateColumns: {xs: '1fr', md: '1.2fr repeat(4, minmax(0, 1fr))'},
                                                                gap: 1,
                                                                alignItems: 'center',
                                                                p: 1.15,
                                                                borderRadius: 2.5,
                                                                border: isSelected
                                                                    ? `1px solid ${palette.selectedRowBorder}`
                                                                    : `1px solid ${palette.border}`,
                                                                backgroundColor: isSelected ? palette.selectedRowBg : palette.cardBg,
                                                                boxShadow: isSelected ? `inset 4px 0 0 ${palette.selectedRowBorder}` : 'none',
                                                                cursor: 'pointer',
                                                                transition: 'all 0.15s ease',
                                                                '&:hover': {
                                                                    borderColor: isSelected ? palette.selectedRowBorder : 'var(--brand-primary-border)',
                                                                    backgroundColor: isSelected ? palette.selectedRowBg : palette.hoverBg,
                                                                },
                                                            }}
                                                        >
                                                            <Box>
                                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>
                                                                    {formatSiocsMfcsTransactionFamily(item.transactionFamily, t)}
                                                                </Typography>
                                                                <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                                    {item.transactionCount || 0} {t('transactions')}
                                                                </Typography>
                                                            </Box>
                                                            <Box>
                                                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                                                    {t('Source Qty')}
                                                                </Typography>
                                                                <Typography sx={{fontSize: '0.84rem', color: palette.text, fontWeight: 700}}>
                                                                    {formatNumberValue(item.sourceQuantityTotal)}
                                                                </Typography>
                                                            </Box>
                                                            <Box>
                                                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                                                    {t('Target Qty')}
                                                                </Typography>
                                                                <Typography sx={{fontSize: '0.84rem', color: palette.text, fontWeight: 700}}>
                                                                    {formatNumberValue(item.targetQuantityTotal)}
                                                                </Typography>
                                                            </Box>
                                                            <Box>
                                                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                                                    {t('Variance')}
                                                                </Typography>
                                                                <Typography
                                                                    sx={{
                                                                        fontSize: '0.84rem',
                                                                        color: Math.abs(Number(item.quantityVarianceTotal || 0)) > 0 ? '#D97706' : palette.text,
                                                                        fontWeight: 700,
                                                                    }}
                                                                >
                                                                    {formatNumberValue(item.quantityVarianceTotal)}
                                                                </Typography>
                                                            </Box>
                                                            <Box>
                                                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                                                    {t('Metric Coverage')}
                                                                </Typography>
                                                                <Typography sx={{fontSize: '0.84rem', color: palette.text, fontWeight: 700}}>
                                                                    {item.quantityMetricsTransactionCount || 0}/{item.transactionCount || 0}
                                                                </Typography>
                                                            </Box>
                                                        </Box>
                                                    )
                                                })}
                                            </Box>
                                        ) : (
                                            <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                                {t('No quantity breakdown is available for the selected {lane} filters yet.', {
                                                    lane: inventoryPresentation?.laneTitle || t('inventory'),
                                                })}
                                            </Typography>
                                        )}
                                    </Box>
                                </Paper>
                            ) : null}
                        </>
                    ) : null}

                    {!hasResultsWorkspace || contentTab === 'workbench' ? (
                        <>
                            <Paper
                                elevation={0}
                                sx={{
                                    p: {xs: 2, md: 2.25},
                                    borderRadius: 4,
                                    border: `1px solid ${palette.border}`,
                                    backgroundColor: palette.cardBg,
                                }}
                            >
                                <Box
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between',
                                        gap: 2,
                                        mb: 1.75,
                                        flexWrap: 'wrap',
                                    }}
                                >
                                    <Box>
                                        <Typography
                                            sx={{
                                                fontSize: '0.98rem',
                                                fontWeight: 800,
                                                color: palette.text,
                                            }}
                                        >
                                            {t('Exception Metrics')}
                                        </Typography>
                                        <Typography
                                            sx={{
                                                mt: 0.35,
                                                fontSize: '0.82rem',
                                                color: palette.textMuted,
                                            }}
                                        >
                                            {t('Transaction and item discrepancies requiring operational review')}
                                        </Typography>
                                    </Box>
                                </Box>

                                {hasResultsWorkspace ? (
                                    <Box
                                        sx={{
                                            display: 'grid',
                                            gridTemplateColumns: {
                                                xs: '1fr',
                                                md: `repeat(${workbenchIssueCards.length}, minmax(0, 1fr))`,
                                            },
                                            gap: 1.5,
                                        }}
                                    >
                                        {workbenchIssueCards.map((card) => (
                                            <KPI
                                                key={card.key}
                                                title={card.title}
                                                value={card.value}
                                                tone={card.tone}
                                                testId={`kpi-card-${card.key}`}
                                                onClick={() => handleKpiClick(card.title, card.key)}
                                                selected={selectedKpi === card.key}
                                            />
                                        ))}
                                    </Box>
                                ) : (
                                    <Grid container spacing={2}>
                                        {workbenchIssueCards.map((card) => (
                                            <Grid item xs={12} sm={6} lg={3} key={card.key}>
                                                <KPI
                                                    title={card.title}
                                                    value={card.value}
                                                    tone={card.tone}
                                                    testId={`kpi-card-${card.key}`}
                                                    onClick={() => handleKpiClick(card.title, card.key)}
                                                    selected={selectedKpi === card.key}
                                                />
                                            </Grid>
                                        ))}
                                    </Grid>
                                )}

                                {issueDefinitions.length > 0 ? (
                                    <Box
                                        sx={{
                                            mt: 1.4,
                                            p: 1.25,
                                            borderRadius: 3,
                                            border: `1px solid ${palette.borderSoft}`,
                                            backgroundColor: palette.cardBgAlt,
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                display: 'grid',
                                                gridTemplateColumns: {xs: '1fr', lg: 'repeat(2, minmax(0, 1fr))'},
                                                gap: 0.75,
                                            }}
                                        >
                                            {issueDefinitions.map((item) => (
                                                <Box
                                                    key={item.title}
                                                    sx={{
                                                        display: 'flex',
                                                        gap: 0.4,
                                                        fontSize: '0.78rem',
                                                        color: palette.textMuted,
                                                        lineHeight: 1.45,
                                                    }}
                                                >
                                                    <Box component="span" sx={{fontWeight: 700, color: palette.text, flexShrink: 0}}>
                                                        {item.title}
                                                    </Box>
                                                    <Box component="span">{`- ${item.description}`}</Box>
                                                </Box>
                                            ))}
                                        </Box>
                                    </Box>
                                ) : null}
                            </Paper>
                        </>
                    ) : null}
                </Box>
            )}

            {!hasResultsWorkspace || contentTab === 'trends' ? (
                <Paper
                    elevation={0}
                    sx={{
                        p: {xs: 2, md: 2.25},
                        mb: 4,
                        borderRadius: 4,
                        border: `1px solid ${palette.border}`,
                        backgroundColor: palette.cardBg,
                    }}
                >
                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: 2,
                        flexWrap: 'wrap',
                        mb: 2,
                    }}
                >
                    <Box>
                        <Typography
                            sx={{
                                fontSize: '1rem',
                                fontWeight: 800,
                                color: palette.text,
                            }}
                        >
                            {t(hasResultsWorkspace ? 'Trends & SLA' : 'Operational Trends')}
                        </Typography>
                        <Typography
                            sx={{
                                mt: 0.35,
                                fontSize: '0.84rem',
                                color: palette.textMuted,
                            }}
                        >
                            {t(hasResultsWorkspace
                                ? 'Supervisor views for recurring exceptions, SLA exposure, and failing store or register concentration.'
                                : 'Trend lines, SLA breaches, failing locations, and exception aging for the selected reconciliation lane')}
                        </Typography>
                    </Box>
                    <Chip
                        label={t(hasResultsWorkspace ? 'Secondary analysis' : 'Phase 2')}
                        size="small"
                        sx={{
                            backgroundColor: palette.blueChipBg,
                            color: palette.blueChipText,
                            fontWeight: 700,
                        }}
                    />
                </Box>

                {loadingAnalytics ? (
                    <Box sx={{display: 'flex', justifyContent: 'center', py: 5}}>
                        <CircularProgress size={28}/>
                    </Box>
                ) : (
                    <Box>
                        <Paper
                            elevation={0}
                            data-testid="selected-record-summary"
                            sx={{
                                p: 2,
                                borderRadius: 3,
                                border: `1px solid ${palette.borderSoft}`,
                                backgroundColor: palette.cardBgAlt,
                            }}
                        >
                            <Box
                                sx={{
                                    display: 'grid',
                                    gridTemplateColumns: {
                                        xs: 'repeat(2, minmax(0, 1fr))',
                                        lg: 'repeat(5, minmax(0, 1fr))',
                                    },
                                    gap: 1.25,
                                }}
                            >
                                {[
                                    {
                                        label: t('Active SLA Cases'),
                                        value: analytics?.slaSummary?.activeCases || 0,
                                        bg: palette.blueChipBg,
                                        color: palette.blueChipText,
                                    },
                                    {
                                        label: t('SLA Breaches'),
                                        value: analytics?.slaSummary?.breachedCases || 0,
                                        bg: '#FEF2F2',
                                        color: '#DC2626',
                                    },
                                    {
                                        label: t('Due Soon'),
                                        value: analytics?.slaSummary?.dueSoonCases || 0,
                                        bg: '#FFF7ED',
                                        color: '#D97706',
                                    },
                                    {
                                        label: t('Within SLA'),
                                        value: analytics?.slaSummary?.withinSlaCases || 0,
                                        bg: palette.tealChipBg,
                                        color: palette.tealChipText,
                                    },
                                    {
                                        label: t('Breach Rate'),
                                        value: `${analytics?.slaSummary?.breachRate || 0}%`,
                                        bg: '#F5F3FF',
                                        color: '#7C3AED',
                                    },
                                ].map((item) => (
                                    <Paper
                                        key={item.label}
                                        elevation={0}
                                        sx={{
                                            p: 1.5,
                                            borderRadius: 2.5,
                                            border: `1px solid ${palette.border}`,
                                            backgroundColor: palette.cardBg,
                                        }}
                                    >
                                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted, fontWeight: 700}}>
                                            {item.label}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '1.45rem', color: item.color, fontWeight: 800}}>
                                            {item.value}
                                        </Typography>
                                    </Paper>
                                ))}
                            </Box>
                        </Paper>

                        <Box
                            sx={{
                                mt: 2.25,
                                display: 'grid',
                                gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'},
                                gap: 2,
                            }}
                        >
                            <Box>
                                <LineChartComponent
                                    data={trend7Data}
                                    dataKey="matchRate"
                                    title={t('7-Day Match Rate Trend')}
                                />
                            </Box>
                            <Box>
                                <LineChartComponent
                                    data={trend30Data}
                                    dataKey="exceptionCount"
                                    title={t('30-Day Exception Trend')}
                                />
                            </Box>
                        </Box>

                        <Grid container spacing={2} sx={{mt: 0.1}}>
                            <Grid item xs={12} md={6} lg={4}>
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 2.25,
                                    height: '100%',
                                    borderRadius: 3,
                                    border: `1px solid ${palette.borderSoft}`,
                                    backgroundColor: palette.cardBgAlt,
                                }}
                            >
                                <Typography sx={{fontSize: '0.92rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                                    {t('Top Failing Stores')}
                                </Typography>
                                {(analytics?.topFailingStores || []).length > 0 ? (
                                    <Box sx={{display: 'flex', flexDirection: 'column', gap: 1.1}}>
                                        {analytics.topFailingStores.map((item, index) => (
                                            <Box
                                                key={`${item.key}-${index}`}
                                                onClick={() => focusTrendStore(item.key)}
                                                sx={{
                                                    display: 'flex',
                                                    justifyContent: 'space-between',
                                                    gap: 1.5,
                                                    p: 1.3,
                                                    borderRadius: 2.5,
                                                    backgroundColor: palette.cardBg,
                                                    border: `1px solid ${palette.border}`,
                                                    cursor: 'pointer',
                                                    transition: 'all 0.15s ease',
                                                    '&:hover': {
                                                        borderColor: 'var(--brand-primary-border)',
                                                        backgroundColor: palette.selectedBg,
                                                    },
                                                }}
                                            >
                                                <Box>
                                                    <Typography sx={{fontSize: '0.88rem', fontWeight: 700, color: palette.text}}>
                                                        {t('Store')} {item.key}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {t('Exceptions')}: {item.exceptionCount} • {t('Match Rate')}: {item.matchRate}%
                                                    </Typography>
                                                    <Typography sx={{mt: 0.25, fontSize: '0.76rem', color: palette.textSoft}}>
                                                        {t('Click to filter the workbench to this store.')}
                                                    </Typography>
                                                </Box>
                                                <Chip
                                                    label={`${item.missing} ${t('missing')}`}
                                                    size="small"
                                                    sx={{backgroundColor: palette.tealChipBg, color: palette.tealChipText, fontWeight: 700}}
                                                />
                                            </Box>
                                        ))}
                                    </Box>
                                ) : (
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('No store trend data available.')}
                                    </Typography>
                                )}
                            </Paper>
                        </Grid>

                        <Grid item xs={12} md={6} lg={4}>
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 2.25,
                                    height: '100%',
                                    borderRadius: 3,
                                    border: `1px solid ${palette.borderSoft}`,
                                    backgroundColor: palette.cardBgAlt,
                                }}
                            >
                                <Typography sx={{fontSize: '0.92rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                                    {t(isTransactionFamilyScoped ? 'Top Failing Transaction Families' : 'Top Failing Registers')}
                                </Typography>
                                {(analytics?.topFailingRegisters || []).length > 0 ? (
                                    <Box sx={{display: 'flex', flexDirection: 'column', gap: 1.1}}>
                                        {analytics.topFailingRegisters.map((item, index) => (
                                            <Box
                                                key={`${item.key}-${index}`}
                                                onClick={() => focusTrendRegister(item.key)}
                                                sx={{
                                                    display: 'flex',
                                                    justifyContent: 'space-between',
                                                    gap: 1.5,
                                                    p: 1.3,
                                                    borderRadius: 2.5,
                                                    backgroundColor: palette.cardBg,
                                                    border: `1px solid ${palette.border}`,
                                                    cursor: 'pointer',
                                                    transition: 'all 0.15s ease',
                                                    '&:hover': {
                                                        borderColor: 'var(--brand-primary-border)',
                                                        backgroundColor: palette.selectedBg,
                                                    },
                                                }}
                                            >
                                                <Box>
                                                    <Typography sx={{fontSize: '0.88rem', fontWeight: 700, color: palette.text}}>
                                                        {isTransactionFamilyScoped ? formatSiocsMfcsTransactionFamily(item.key, t) : `${t('Register')} ${item.key}`}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {t('Duplicates')}: {item.duplicates} • {t('Match Rate')}: {item.matchRate}%
                                                    </Typography>
                                                    <Typography sx={{mt: 0.25, fontSize: '0.76rem', color: palette.textSoft}}>
                                                        {t(isTransactionFamilyScoped ? 'Click to filter the workbench to this transaction family.' : 'Click to filter the workbench to this register.')}
                                                    </Typography>
                                                </Box>
                                                <Chip
                                                    label={`${item.duplicates} ${t('duplicates')}`}
                                                    size="small"
                                                    sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}
                                                />
                                            </Box>
                                        ))}
                                    </Box>
                                ) : (
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t(isTransactionFamilyScoped ? 'No transaction family trend data available.' : 'No register trend data available.')}
                                    </Typography>
                                )}
                            </Paper>
                        </Grid>

                        <Grid item xs={12} lg={4}>
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 2.25,
                                    height: '100%',
                                    borderRadius: 3,
                                    border: `1px solid ${palette.borderSoft}`,
                                    backgroundColor: palette.cardBgAlt,
                                }}
                            >
                                <Typography sx={{fontSize: '0.92rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                                    {t('Exception Aging')}
                                </Typography>
                                {(analytics?.exceptionAging || []).length > 0 ? (
                                    <Box sx={{display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1.2}}>
                                        {analytics.exceptionAging.map((bucket) => (
                                            <Paper
                                                key={bucket.label}
                                                elevation={0}
                                                sx={{
                                                    p: 1.5,
                                                    borderRadius: 2.5,
                                                    border: `1px solid ${palette.border}`,
                                                    backgroundColor: palette.cardBg,
                                                }}
                                            >
                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted, fontWeight: 700}}>
                                                    {bucket.label}
                                                </Typography>
                                                <Typography sx={{mt: 0.4, fontSize: '1.4rem', color: palette.text, fontWeight: 800}}>
                                                    {bucket.count}
                                                </Typography>
                                            </Paper>
                                        ))}
                                    </Box>
                                ) : (
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('No exception aging data available.')}
                                    </Typography>
                                )}
                            </Paper>
                            </Grid>
                        </Grid>
                    </Box>
                )}
                </Paper>
            ) : null}

            {contentTab === 'workbench' && selectedKpi &&
                (loadingDetail ? (
                    <Box
                        sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            py: 4,
                        }}
                    >
                        <CircularProgress size={28}/>
                    </Box>
                ) : (
                    <>
                        <DetailTable
                            title={detailTitle || t('Detail Records')}
                            subtitle={detailSubtitle}
                            data={renderedDetailData}
                            onRowSelect={(row) => setSelectedDetailRow(row.__meta || null)}
                            selectedRowKey={selectedDetailRow?.transactionKey || null}
                        />

                        {selectedRecordSummary ? (
                            <Box
                                sx={{
                                    mt: 2,
                                }}
                            >
                                <Typography
                                    sx={{
                                        fontSize: '0.78rem',
                                        fontWeight: 800,
                                        letterSpacing: '0.08em',
                                        textTransform: 'uppercase',
                                        color: palette.textMuted,
                                        mb: 1,
                                    }}
                                >
                                    {t('Selected Record')}
                                </Typography>
                                <Paper
                                    elevation={0}
                                    sx={{
                                        p: 2,
                                        borderRadius: 3,
                                        border: `1px solid ${palette.border}`,
                                        backgroundColor: palette.cardBg,
                                    }}
                                >
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
                                                {`${t('Transaction')} ${selectedRecordSummary.transactionId} - ${t('Store')} ${selectedRecordSummary.storeId}`}
                                            </Typography>
                                            <Typography sx={{mt: 0.35, fontSize: '0.8rem', color: palette.textMuted}}>
                                                {t('Review this record or open drill-down and exception workflow from here.')}
                                            </Typography>
                                        </Box>
                                        <Box sx={{display: 'flex', gap: 1, flexWrap: 'wrap', justifyContent: 'flex-end'}}>
                                            <Button
                                                variant="outlined"
                                                data-testid="selected-record-open-drilldown"
                                                onClick={openSelectedRecordDrillDown}
                                            >
                                                {t('Open drill-down')}
                                            </Button>
                                            {canViewExceptionQueue ? (
                                                <Button
                                                    variant="outlined"
                                                    data-testid="selected-record-open-exception-queues"
                                                    onClick={openSelectedRecordInExceptionQueue}
                                                >
                                                    {t('Open in Exception Queues')}
                                                </Button>
                                            ) : null}
                                        </Box>
                                    </Box>

                                    <Box
                                        sx={{
                                            mt: 1.5,
                                            pt: 1.5,
                                            borderTop: `1px solid ${palette.borderSoft}`,
                                            display: 'grid',
                                            gridTemplateColumns: {xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(4, minmax(0, 1fr))'},
                                            gap: 1.1,
                                        }}
                                    >
                                        {selectedRecordFields.map((item) => (
                                            <Box
                                                key={item.label}
                                                sx={{
                                                    p: 1.2,
                                                    minHeight: 82,
                                                    borderRadius: 2.25,
                                                    border: `1px solid ${palette.borderSoft}`,
                                                    backgroundColor: palette.cardBgAlt,
                                                }}
                                            >
                                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                                                    {item.label}
                                                </Typography>
                                                <Box
                                                    sx={{
                                                        mt: 0.55,
                                                        fontSize: '0.88rem',
                                                        color: palette.text,
                                                        fontWeight: 700,
                                                        wordBreak: 'break-word',
                                                    }}
                                                >
                                                    {item.value}
                                                </Box>
                                            </Box>
                                        ))}
                                    </Box>
                                </Paper>
                            </Box>
                        ) : null}

                        {totalElements > PAGE_SIZE && (
                            <Box
                                sx={{
                                    display: 'flex',
                                    justifyContent: 'center',
                                    alignItems: 'center',
                                    gap: 2,
                                    mt: 2,
                                }}
                            >
                                <Button
                                    variant="outlined"
                                    disabled={page === 0}
                                    onClick={() => setPage((p) => p - 1)}
                                    sx={{
                                        textTransform: 'none',
                                        borderColor: palette.border,
                                        color: palette.text,
                                        '&:hover': {
                                            borderColor: 'var(--brand-primary)',
                                            backgroundColor: palette.hoverBg,
                                        },
                                    }}
                                >
                                    {t('Previous')}
                                </Button>

                                <Typography
                                    variant="body2"
                                    sx={{color: palette.textMuted}}
                                >
                                    {`Showing ${pageStart}-${pageEnd} of ${totalElements} records`}
                                </Typography>

                                <Typography
                                    variant="body2"
                                    sx={{color: palette.textSoft}}
                                >
                                    {t('Page {page} of {total}', {
                                        page: page + 1,
                                        total: Math.ceil(totalElements / PAGE_SIZE),
                                    })}
                                </Typography>

                                <Button
                                    variant="outlined"
                                    disabled={
                                        (page + 1) * PAGE_SIZE >= totalElements
                                    }
                                    onClick={() => setPage((p) => p + 1)}
                                    sx={{
                                        textTransform: 'none',
                                        borderColor: palette.border,
                                        color: palette.text,
                                        '&:hover': {
                                            borderColor: 'var(--brand-primary)',
                                            backgroundColor: palette.hoverBg,
                                        },
                                    }}
                                >
                                    {t('Next')}
                                </Button>
                            </Box>
                        )}
                    </>
                ))}
        </Box>
    )
}

export default function Dashboard({
                                      openTabs,
                                      activeTab,
                                      setActiveTab,
                                      handleOpenTab,
                                      handleCloseTab,
                                  }) {
    const [themeMode, setThemeMode] = useState(getThemeMode())
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

    return (
        <Box
            sx={{
                display: 'flex',
                flexDirection: 'column',
                height: '100%',
                backgroundColor: palette.pageBg,
                color: palette.text,
            }}
        >
            {openTabs.length > 0 && (
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 0.5,
                        px: 2,
                        pt: 1.5,
                        borderBottom: `1px solid ${palette.border}`,
                        backgroundColor: palette.tabBg,
                        flexWrap: 'nowrap',
                        overflowX: 'auto',
                        flexShrink: 0,
                        '&::-webkit-scrollbar': {height: 4},
                        '&::-webkit-scrollbar-track': {
                            backgroundColor: palette.scrollbarTrack,
                        },
                        '&::-webkit-scrollbar-thumb': {
                            backgroundColor: palette.scrollbarThumb,
                            borderRadius: 2,
                        },
                    }}
                >
                    {openTabs.map((tabId) => {
                        const isActive = activeTab === tabId
                        const colors = tabColor(tabId)

                        return (
                            <Box
                                key={tabId}
                                onClick={() => setActiveTab(tabId)}
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 1,
                                    px: 2,
                                    py: 0.85,
                                    borderRadius: '10px 10px 0 0',
                                    cursor: 'pointer',
                                    flexShrink: 0,
                                    backgroundColor: isActive
                                        ? palette.tabActiveBg
                                        : 'transparent',
                                    borderBottom: isActive
                                        ? `2px solid ${colors.active}`
                                        : '2px solid transparent',
                                    transition: 'all 0.15s ease',
                                    '&:hover': {
                                        backgroundColor: palette.tabActiveBg,
                                    },
                                }}
                            >
                                <Typography
                                    sx={{
                                        fontSize: '0.83rem',
                                        fontWeight: isActive ? 700 : 500,
                                        color: isActive
                                            ? colors.active
                                            : palette.textMuted,
                                        whiteSpace: 'nowrap',
                                        userSelect: 'none',
                                    }}
                                >
                                    {getTabLabel(t, tabId)}
                                </Typography>

                                <Box
                                    onClick={(e) => {
                                        e.stopPropagation()
                                        handleCloseTab(tabId)
                                    }}
                                    sx={{
                                        width: 18,
                                        height: 18,
                                        borderRadius: '50%',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        color: palette.textSoft,
                                        flexShrink: 0,
                                        '&:hover': {
                                            backgroundColor: palette.isDark
                                                ? '#1E293B'
                                                : '#E2E8F0',
                                            color: palette.text,
                                        },
                                    }}
                                >
                                    <CloseIcon sx={{fontSize: 12}}/>
                                </Box>
                            </Box>
                        )
                    })}
                </Box>
            )}

            <Box sx={{flex: 1, overflowY: 'auto'}}>
                {!activeTab && <WelcomeLanding palette={palette} t={t}/>}

                {openTabs.map((tabId) => (
                    <Box
                        key={tabId}
                        sx={{
                            display: activeTab === tabId ? 'block' : 'none',
                        }}
                    >
                        <ModuleErrorBoundary
                            resetKey={activeTab}
                            tabId={tabId}
                            tabLabel={getTabLabel(t, tabId)}
                            palette={palette}
                            t={t}
                            onClose={() => handleCloseTab(tabId)}
                        >
                            <Suspense fallback={<TabLoadingPanel palette={palette} label={t('Loading module...')}/>}>
                                {renderDashboardTab(tabId, palette, t, handleOpenTab)}
                            </Suspense>
                        </ModuleErrorBoundary>
                    </Box>
                ))}
            </Box>
        </Box>
    )
}

function renderDashboardTab(tabId, palette, t, handleOpenTab) {
    if (tabId === TRANSACTION_DRILLDOWN_TAB_ID) {
        return <TransactionDrillDown palette={palette} t={t} onOpenTab={handleOpenTab}/>
    }

    if (SECURITY_IDS.includes(tabId)) {
        return renderSecurityTab(tabId)
    }

    if (REPORT_IDS.includes(tabId)) {
        if (tabId === 'operations-command-center') {
            return <OperationsCommandCenter palette={palette} t={t}/>
        }
        if (tabId === 'executive-scorecards') {
            return <ExecutiveScorecards palette={palette} t={t}/>
        }
        if (tabId === 'store-scorecards') {
            return <StoreScorecards palette={palette} t={t}/>
        }
        if (tabId === 'recurrence-analytics') {
            return <RecurrenceAnalytics palette={palette} t={t}/>
        }
        return <RootCauseAnalytics palette={palette} t={t}/>
    }

    if (ALERT_IDS.includes(tabId)) {
        return <Alerts palette={palette} t={t}/>
    }

    if (EXCEPTION_IDS.includes(tabId)) {
        if (tabId === 'approval-center') {
            return <ApprovalCenter palette={palette} t={t}/>
        }
        if (tabId === 'store-manager-lite') {
            return <StoreManagerLite palette={palette} t={t}/>
        }
        if (tabId === 'regional-incident-board') {
            return <RegionalIncidentBoard palette={palette} t={t}/>
        }
        if (tabId === 'noise-suppression') {
            return <NoiseSuppression palette={palette} t={t}/>
        }
        if (tabId === 'known-issues') {
            return <KnownIssues palette={palette} t={t}/>
        }
        if (tabId === 'ticketing-comms') {
            return <TicketingCommunications palette={palette} t={t}/>
        }
        if (tabId === 'routing-playbooks') {
            return <RoutingPlaybooks palette={palette} t={t}/>
        }
        return <ExceptionQueues palette={palette} t={t}/>
    }

    if (OPERATION_IDS.includes(tabId)) {
        if (tabId === 'recon-jobs') {
            return <ReconciliationJobs palette={palette} t={t}/>
        }
        return <Operations palette={palette} t={t}/>
    }

    if (INTEGRATION_IDS.includes(tabId)) {
        return <IntegrationHub palette={palette} t={t}/>
    }

    if (SLA_IDS.includes(tabId)) {
        return <SlaManagement palette={palette} t={t}/>
    }

    if (ACTIVITY_IDS.includes(tabId)) {
        return <Activity palette={palette} t={t}/>
    }

    if (CONFIGURATION_IDS.includes(tabId)) {
        return <Configurations tabId={tabId} palette={palette} t={t}/>
    }

    return <ReconContent tabId={tabId} palette={palette} t={t} onOpenTab={handleOpenTab}/>
}

function renderSecurityTab(tabId) {
    switch (tabId) {
        case 'manage-users':
            return <ManageUsers/>
        case 'manage-roles':
            return <ManageRoles/>
        case 'manage-perms':
            return <ManagePermissions/>
        case 'org-hierarchy':
            return <OrganizationHierarchy/>
        case 'tenant-access':
            return <TenantAccessCenter/>
        case 'branding-center':
            return <BrandingCenter/>
        default:
            return null
    }
}
