import {Suspense, lazy, useEffect, useMemo, useState} from 'react'
import {
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
import {reconApi} from '../services/reconApi'
import {LocalizationProvider} from '@mui/x-date-pickers/LocalizationProvider'
import {DatePicker} from '@mui/x-date-pickers/DatePicker'
import {AdapterDayjs} from '@mui/x-date-pickers/AdapterDayjs'
import dayjs from 'dayjs'
import {useI18n} from '../context/I18nContext'
import {useAuth} from '../context/AuthContext'
import {ACTIVITY_TAB_IDS, ALERT_TAB_IDS, CONFIGURATION_TAB_IDS, EXCEPTION_TAB_IDS, getTabLabel, INTEGRATION_TAB_IDS, OPERATIONS_TAB_IDS, REPORT_TAB_IDS, SLA_TAB_IDS} from '../constants/navigation'
import {RECON_VIEW_BY_TAB, getReconTargetSystemByTab} from '../constants/reconViews'
import {
    EXCEPTION_QUEUE_PREFILL_EVENT,
    EXCEPTION_QUEUE_PREFILL_KEY,
    TRANSACTION_DRILLDOWN_PREFILL_EVENT,
    TRANSACTION_DRILLDOWN_PREFILL_KEY,
} from '../constants/uiStateKeys'

const Alerts = lazy(() => import('./Alerts'))
const ApprovalCenter = lazy(() => import('./ApprovalCenter'))
const Activity = lazy(() => import('./Activity'))
const Configurations = lazy(() => import('./Configurations'))
const ExecutiveScorecards = lazy(() => import('./ExecutiveScorecards'))
const ExceptionQueues = lazy(() => import('./ExceptionQueues'))
const KnownIssues = lazy(() => import('./KnownIssues'))
const NoiseSuppression = lazy(() => import('./NoiseSuppression'))
const Operations = lazy(() => import('./Operations'))
const ReconciliationJobs = lazy(() => import('./ReconciliationJobs'))
const IntegrationHub = lazy(() => import('./IntegrationHub'))
const OperationsCommandCenter = lazy(() => import('./OperationsCommandCenter'))
const RegionalIncidentBoard = lazy(() => import('./RegionalIncidentBoard'))
const RecurrenceAnalytics = lazy(() => import('./RecurrenceAnalytics'))
const RootCauseAnalytics = lazy(() => import('./RootCauseAnalytics'))
const RoutingPlaybooks = lazy(() => import('./RoutingPlaybooks'))
const SlaManagement = lazy(() => import('./SlaManagement'))
const StoreManagerLite = lazy(() => import('./StoreManagerLite'))
const StoreScorecards = lazy(() => import('./StoreScorecards'))
const TicketingCommunications = lazy(() => import('./TicketingCommunications'))
const ManageUsers = lazy(() => import('./admin/ManageUsers'))
const ManageRoles = lazy(() => import('./admin/ManageRoles'))
const ManagePermissions = lazy(() => import('./admin/ManagePermissions'))
const OrganizationHierarchy = lazy(() => import('./admin/OrganizationHierarchy'))
const TenantAccessCenter = lazy(() => import('./admin/TenantAccessCenter'))
const BrandingCenter = lazy(() => import('./admin/BrandingCenter'))
const TransactionDrillDown = lazy(() => import('./TransactionDrillDown'))

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
function getMissingKpiTitle(tabId, t) {
    switch (tabId) {
        case 'siocs-mfcs':
            return t('Missing in MFCS')
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
    return getReconTargetSystemByTab(tabId)
}

function getProcessingPendingKpiTitle(tabId, t) {
    return t(`Processing Pending in ${getTargetSystem(tabId)}`)
}

function getDuplicateKpiTitle(tabId, t) {
    return t(`Duplicate Transactions in ${getTargetSystem(tabId)}`)
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
    ]

    const transactionIssues = [
        ...(tabId === 'xstore-xocs'
            ? [{title: t('Transaction Total Mismatch'), key: 'totalMismatch'}]
            : []),
        ...((tabId === 'xstore-siocs' || tabId === 'xstore-sim' || tabId === 'siocs-mfcs')
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

function getKpiStatus(tabId, selectedKpi) {
    const target = getTargetSystem(tabId)

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
        case 'total':
        default:
            return null
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

function WelcomeLanding({palette, t}) {
    const reconModules = [
        {
            id: 'xstore-sim',
            title: t('Xstore vs SIM'),
            desc: t('Monitor transaction reconciliation, identify mismatches, and drill into operational exceptions.'),
            icon: <CompareArrowsRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('Reconciliation'),
            accent: '#4A79D8',
            bg: palette.isDark ? '#0F172A' : '#EEF4FF',
        },
        {
            id: 'xstore-siocs',
            title: t('Xstore vs SIOCS'),
            desc: t('Review reconciliation results produced from the cloud SIOCS connector against the existing Xstore source.'),
            icon: <CompareArrowsRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('Cloud Reconciliation'),
            accent: '#4A79D8',
            bg: palette.isDark ? '#0F172A' : '#EEF4FF',
        },
        {
            id: 'siocs-mfcs',
            title: t('SIOCS vs MFCS'),
            desc: t('Track the new SIOCS to MFCS reconciliation lane and investigate ERP-facing mismatches from the same workbench.'),
            icon: <CompareArrowsRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('ERP Reconciliation'),
            accent: '#5C74D6',
            bg: palette.isDark ? '#0F172A' : '#F1F4FF',
        },
        {
            id: 'manage-users',
            title: t('Manage Users'),
            desc: t('Control user access, store scope, and role assignments across your RetailINQ workspace.'),
            icon: <AdminPanelSettingsRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('Security'),
            accent: '#4A79D8',
            bg: palette.isDark ? '#0F172A' : '#EEF4FF',
        },
    ]

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
    const reconView = RECON_VIEW_BY_TAB[tabId] || null
    const {hasPermission} = useAuth()
    const canViewExceptionQueue = hasPermission('EXCEPTION_QUEUE_VIEW')
    const [kpis, setKpis] = useState({})
    const [selectedKpi, setSelectedKpi] = useState(null)
    const [detailData, setDetailData] = useState([])
    const [selectedDetailRow, setSelectedDetailRow] = useState(null)
    const [detailTitle, setDetailTitle] = useState('')
    const [analytics, setAnalytics] = useState(null)
    const [loadingKpis, setLoadingKpis] = useState(false)
    const [loadingDetail, setLoadingDetail] = useState(false)
    const [loadingAnalytics, setLoadingAnalytics] = useState(false)
    const [page, setPage] = useState(0)
    const [totalElements, setTotalElements] = useState(0)
    const [stores, setStores] = useState([])
    const [registers, setRegisters] = useState([])
    const [transactionTypes, setTransactionTypes] = useState([])
    const [selectedStores, setSelectedStores] = useState([])
    const [selectedRegisters, setSelectedRegisters] = useState([])
    const [selectedTransactionTypes, setSelectedTransactionTypes] = useState([])
    const [fromDate, setFromDate] = useState('')
    const [toDate, setToDate] = useState('')
    const isTransactionTypeScoped = reconView === 'SIOCS_MFCS'

    useEffect(() => {
        reconApi.getStores(reconView).then(setStores).catch(console.error)
    }, [reconView])

    useEffect(() => {
        setSelectedRegisters([])
        setSelectedTransactionTypes([])
    }, [reconView])

    useEffect(() => {
        if (!reconView) {
            setRegisters([])
            setTransactionTypes([])
            return
        }

        if (isTransactionTypeScoped) {
            reconApi.getTransactionTypes(
                selectedStores.length ? selectedStores : null,
                reconView
            )
                .then(setTransactionTypes)
                .catch(console.error)
            setRegisters([])
            return
        }

        reconApi.getRegisters(
            selectedStores.length ? selectedStores : null,
            reconView
        )
            .then(setRegisters)
            .catch(console.error)
        setTransactionTypes([])
    }, [selectedStores, reconView, isTransactionTypeScoped])

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
                })
            } catch (e) {
                console.error('Failed to load KPIs', e)
            } finally {
                setLoadingKpis(false)
            }
        }

        load()
        setSelectedKpi(null)
        setDetailData([])
        setSelectedDetailRow(null)
        setDetailTitle('')
        setPage(0)
    }, [reconView, selectedStores, fromDate, toDate])

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
                    wkstnIds: isTransactionTypeScoped ? [] : selectedRegisters,
                    transactionTypes: isTransactionTypeScoped ? selectedTransactionTypes : [],
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
    }, [reconView, selectedStores, selectedRegisters, selectedTransactionTypes, isTransactionTypeScoped])

    useEffect(() => {
        if (!selectedKpi || !reconView) return

        const reconStatus = getKpiStatus(tabId, selectedKpi)

        const load = async () => {
            setLoadingDetail(true)
            try {
                const result = await reconApi.getTransactions({
                    storeIds: selectedStores.length
                        ? selectedStores
                        : undefined,
                    wkstnIds: !isTransactionTypeScoped && selectedRegisters.length
                        ? selectedRegisters
                        : undefined,
                    transactionTypes: isTransactionTypeScoped && selectedTransactionTypes.length
                        ? selectedTransactionTypes
                        : undefined,
                    reconView,
                    fromBusinessDate: fromDate || undefined,
                    toBusinessDate: toDate || undefined,
                    reconStatus: reconStatus || undefined,
                    page,
                    size: PAGE_SIZE,
                })

                setDetailData(
                    (result.content || []).map((t) => {
                        const row = {
                            __rowKey: t.transactionKey,
                            __meta: t,
                            'Transaction ID': getTransactionIdFromExternalId(
                                t.externalId,
                                '-'
                            ),
                            Store: t.storeId,
                            'Business Date':
                                t.businessDateDisplay || t.businessDate,
                            Type: t.transactionType,
                            Status: t.reconStatus,
                            'Match Score': t.matchScore ?? '-',
                            'Match Band': t.matchBand || '-',
                            'Reconciled At': t.reconciledAt,
                        }
                        if (!isTransactionTypeScoped) {
                            row.Register = t.wkstnId ?? '-'
                        }
                        return row
                    })
                )
                setSelectedDetailRow(
                    (result.content || []).length > 0
                        ? (result.content || [])[0]
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
        fromDate,
        toDate,
        reconView,
        isTransactionTypeScoped,
    ])

    const handleKpiClick = (title, key) => {
        setDetailTitle(title)
        setSelectedKpi(key)
        setPage(0)
        setSelectedDetailRow(null)
    }

    const pageStart =
        totalElements === 0 ? 0 : page * PAGE_SIZE + 1
    const pageEnd =
        totalElements === 0
            ? 0
            : Math.min((page + 1) * PAGE_SIZE, totalElements)

    const selectedRecordSummary = selectedDetailRow
        ? {
            transactionKey: selectedDetailRow.transactionKey || '-',
            reconView: selectedDetailRow.reconView || reconView || '-',
            storeId: selectedDetailRow.storeId || '-',
            businessDate: selectedDetailRow.businessDateDisplay || selectedDetailRow.businessDate || '-',
            transactionType: selectedDetailRow.transactionType || '-',
            reconStatus: selectedDetailRow.reconStatus || '-',
            matchBand: selectedDetailRow.matchBand || '-',
            matchScore: selectedDetailRow.matchScore ?? '-',
        }
        : null

    const openSelectedRecordInExceptionQueue = () => {
        if (!selectedDetailRow || !canViewExceptionQueue) {
            return
        }
        const prefill = {
            reconView: selectedDetailRow.reconView || reconView || '',
            transactionKey: selectedDetailRow.transactionKey || '',
            search: selectedDetailRow.transactionKey || '',
        }
        sessionStorage.setItem(EXCEPTION_QUEUE_PREFILL_KEY, JSON.stringify(prefill))
        window.dispatchEvent(new CustomEvent(EXCEPTION_QUEUE_PREFILL_EVENT, {detail: prefill}))
        onOpenTab?.('exception-queues')
    }

    const openSelectedRecordDrillDown = () => {
        if (!selectedDetailRow) {
            return
        }
        const prefill = {
            transactionKey: selectedDetailRow.transactionKey,
            reconView: selectedDetailRow.reconView || reconView || '',
            source: 'dashboard-kpi',
        }
        sessionStorage.setItem(TRANSACTION_DRILLDOWN_PREFILL_KEY, JSON.stringify(prefill))
        window.dispatchEvent(new CustomEvent(TRANSACTION_DRILLDOWN_PREFILL_EVENT, {detail: prefill}))
        onOpenTab?.(TRANSACTION_DRILLDOWN_TAB_ID)
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
                                }}
                                sx={{
                                    color: '#ef4444',
                                    fontSize: '0.8rem',
                                    fontWeight: 500,
                                    borderBottom: `1px solid ${palette.borderSoft}`,
                                    py: 0.75,
                                }}
                            >
                                ✕&nbsp; Clear all stores
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
                        {t(isTransactionTypeScoped ? 'Transaction Type' : 'Register')}
                    </InputLabel>
                    <Select
                        multiple
                        value={isTransactionTypeScoped ? selectedTransactionTypes : selectedRegisters}
                        onChange={(e) => {
                            const v = e.target.value
                            const next = typeof v === 'string' ? v.split(',') : v
                            if (isTransactionTypeScoped) {
                                setSelectedTransactionTypes(next)
                            } else {
                                setSelectedRegisters(next)
                            }
                        }}
                        input={<OutlinedInput label={t(isTransactionTypeScoped ? 'Transaction Type' : 'Register')}/>}
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
                                            label={isTransactionTypeScoped ? v : `R${v}`}
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
                                        label={`${sel.length} ${t(isTransactionTypeScoped ? 'types' : 'registers')}`}
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
                        {(isTransactionTypeScoped ? selectedTransactionTypes.length > 0 : selectedRegisters.length > 0) && (
                            <MenuItem
                                onMouseDown={(e) => {
                                    e.preventDefault()
                                    e.stopPropagation()
                                    if (isTransactionTypeScoped) {
                                        setSelectedTransactionTypes([])
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
                                ✕&nbsp; {isTransactionTypeScoped ? t('Clear all transaction types') : t('Clear all registers')}
                            </MenuItem>
                        )}

                        {(isTransactionTypeScoped ? transactionTypes : registers).map((option) => (
                            <MenuItem key={option} value={option}>
                                <Box
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 1,
                                    }}
                                >
                                    {renderCheckbox(
                                        isTransactionTypeScoped
                                            ? selectedTransactionTypes.includes(option)
                                            : selectedRegisters.includes(option)
                                    )}
                                    {isTransactionTypeScoped
                                        ? option
                                        : `${t('Register')} ${option}`}
                                </Box>
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>

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
                    fromDate ||
                    toDate) && (
                    <Box
                        onClick={() => {
                            setSelectedStores([])
                            setSelectedRegisters([])
                            setSelectedTransactionTypes([])
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
                        ✕&nbsp; Clear all filters
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
                    <Paper
                        elevation={0}
                        sx={{
                            p: {xs: 2, md: 2.25},
                            borderRadius: 4,
                            border: `1px solid ${palette.border}`,
                            background: palette.heroBg,
                            position: 'relative',
                            overflow: 'hidden',
                        }}
                    >
                        <Box
                            sx={{
                                position: 'absolute',
                                inset: 0,
                                pointerEvents: 'none',
                                backgroundImage: `${palette.overlay1}, ${palette.overlay2}`,
                                backgroundSize: '320px 320px, 260px 260px',
                                backgroundPosition: 'right -140px top -130px, left -140px bottom -140px',
                                backgroundRepeat: 'no-repeat',
                                opacity: 0.75,
                            }}
                        />

                        <Box sx={{position: 'relative'}}>
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
                                            fontSize: '1rem',
                                            fontWeight: 800,
                                            color: palette.text,
                                            letterSpacing: '-0.01em',
                                        }}
                                    >
                                        {t('Reconciliation Summary')}
                                    </Typography>
                                    <Typography
                                        sx={{
                                            mt: 0.35,
                                            fontSize: '0.84rem',
                                            color: palette.textMuted,
                                        }}
                                    >
                                        {t('Top-line transaction outcomes for the selected filters')}
                                    </Typography>
                                </Box>
                                <Chip
                                    label={getTargetSystem(tabId)}
                                    size="small"
                                    sx={{
                                        backgroundColor: palette.blueChipBg,
                                        color: palette.blueChipText,
                                        fontWeight: 700,
                                    }}
                                />
                            </Box>

                            <Grid container spacing={2}>
                                {getKpiSections(tabId, t).summary.map((kpi) => (
                                    <Grid item xs={12} sm={6} lg={3} key={kpi.key}>
                                        <KPI
                                            title={kpi.title}
                                            value={kpis[kpi.key]}
                                            testId={`kpi-card-${kpi.key}`}
                                            onClick={() =>
                                                handleKpiClick(kpi.title, kpi.key)
                                            }
                                            selected={selectedKpi === kpi.key}
                                        />
                                    </Grid>
                                ))}
                            </Grid>
                        </Box>
                    </Paper>

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

                        <Grid container spacing={2}>
                            {getKpiSections(tabId, t).issues.map((kpi) => (
                                <Grid item xs={12} sm={6} lg={3} key={kpi.key}>
                                    <KPI
                                        title={kpi.title}
                                        value={kpis[kpi.key]}
                                        testId={`kpi-card-${kpi.key}`}
                                        onClick={() =>
                                            handleKpiClick(kpi.title, kpi.key)
                                        }
                                        selected={selectedKpi === kpi.key}
                                    />
                                </Grid>
                            ))}
                        </Grid>
                    </Paper>
                </Box>
            )}

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
                            {t('Operational Trends')}
                        </Typography>
                        <Typography
                            sx={{
                                mt: 0.35,
                                fontSize: '0.84rem',
                                color: palette.textMuted,
                            }}
                        >
                            {t('Trend lines, SLA breaches, failing locations, and exception aging for the selected reconciliation lane')}
                        </Typography>
                    </Box>
                    <Chip
                        label={t('Phase 2')}
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
                    <Grid container spacing={2}>
                        <Grid item xs={12}>
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
                        </Grid>
                        <Grid item xs={12} lg={6}>
                            <LineChartComponent
                                data={trend7Data}
                                dataKey="matchRate"
                                title={t('7-Day Match Rate Trend')}
                            />
                        </Grid>
                        <Grid item xs={12} lg={6}>
                            <LineChartComponent
                                data={trend30Data}
                                dataKey="exceptionCount"
                                title={t('30-Day Exception Trend')}
                            />
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
                                    {t('Top Failing Stores')}
                                </Typography>
                                {(analytics?.topFailingStores || []).length > 0 ? (
                                    <Box sx={{display: 'flex', flexDirection: 'column', gap: 1.1}}>
                                        {analytics.topFailingStores.map((item, index) => (
                                            <Box
                                                key={`${item.key}-${index}`}
                                                sx={{
                                                    display: 'flex',
                                                    justifyContent: 'space-between',
                                                    gap: 1.5,
                                                    p: 1.3,
                                                    borderRadius: 2.5,
                                                    backgroundColor: palette.cardBg,
                                                    border: `1px solid ${palette.border}`,
                                                }}
                                            >
                                                <Box>
                                                    <Typography sx={{fontSize: '0.88rem', fontWeight: 700, color: palette.text}}>
                                                        {t('Store')} {item.key}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {t('Exceptions')}: {item.exceptionCount} • {t('Match Rate')}: {item.matchRate}%
                                                    </Typography>
                                                </Box>
                                                <Chip
                                                    label={item.missing}
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
                                    {t(isTransactionTypeScoped ? 'Top Failing Transaction Types' : 'Top Failing Registers')}
                                </Typography>
                                {(analytics?.topFailingRegisters || []).length > 0 ? (
                                    <Box sx={{display: 'flex', flexDirection: 'column', gap: 1.1}}>
                                        {analytics.topFailingRegisters.map((item, index) => (
                                            <Box
                                                key={`${item.key}-${index}`}
                                                sx={{
                                                    display: 'flex',
                                                    justifyContent: 'space-between',
                                                    gap: 1.5,
                                                    p: 1.3,
                                                    borderRadius: 2.5,
                                                    backgroundColor: palette.cardBg,
                                                    border: `1px solid ${palette.border}`,
                                                }}
                                            >
                                                <Box>
                                                    <Typography sx={{fontSize: '0.88rem', fontWeight: 700, color: palette.text}}>
                                                        {isTransactionTypeScoped ? item.key : `${t('Register')} ${item.key}`}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {t('Duplicates')}: {item.duplicates} • {t('Match Rate')}: {item.matchRate}%
                                                    </Typography>
                                                </Box>
                                                <Chip
                                                    label={item.exceptionCount}
                                                    size="small"
                                                    sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}
                                                />
                                            </Box>
                                        ))}
                                    </Box>
                                ) : (
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t(isTransactionTypeScoped ? 'No transaction type trend data available.' : 'No register trend data available.')}
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
                )}
            </Paper>

            {selectedKpi &&
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
                            title={`${detailTitle} — ${totalElements} records`}
                            data={detailData}
                            onRowSelect={(row) => setSelectedDetailRow(row.__meta || null)}
                            selectedRowKey={selectedDetailRow?.transactionKey || null}
                        />

                        {selectedRecordSummary ? (
                            <Paper
                                elevation={0}
                                sx={{
                                    mt: 2,
                                    p: 2.25,
                                    borderRadius: 3,
                                    border: `1px solid ${palette.borderSoft}`,
                                    backgroundColor: palette.cardBgAlt,
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
                                        <Typography sx={{fontSize: '0.98rem', fontWeight: 800, color: palette.text}}>
                                            {t('Selected Record')}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.8rem', color: palette.textMuted}}>
                                            {t('Review the currently selected KPI row here, or jump to Exception Queues for full case triage and case-level workflow.')}
                                        </Typography>
                                    </Box>
                                    <Box sx={{display: 'flex', gap: 1, flexWrap: 'wrap'}}>
                                        <Button
                                            variant="outlined"
                                            data-testid="selected-record-open-drilldown"
                                            onClick={openSelectedRecordDrillDown}
                                        >
                                            {t('Open Transaction Drill-down')}
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
                                        mt: 1.6,
                                        display: 'grid',
                                        gridTemplateColumns: {xs: '1fr', md: 'repeat(4, minmax(0, 1fr))'},
                                        gap: 1.2,
                                    }}
                                >
                                    {[
                                        {label: t('Transaction Key'), value: selectedRecordSummary.transactionKey},
                                        {label: t('Recon View'), value: selectedRecordSummary.reconView},
                                        {label: t('Store'), value: selectedRecordSummary.storeId},
                                        {label: t('Business Date'), value: selectedRecordSummary.businessDate},
                                        {label: t('Transaction Type'), value: selectedRecordSummary.transactionType},
                                        {label: t('Recon Status'), value: selectedRecordSummary.reconStatus},
                                        {label: t('Match Band'), value: selectedRecordSummary.matchBand},
                                        {label: t('Match Score'), value: selectedRecordSummary.matchScore},
                                    ].map((item) => (
                                        <Box
                                            key={item.label}
                                            sx={{
                                                p: 1.25,
                                                borderRadius: 2.5,
                                                border: `1px solid ${palette.border}`,
                                                backgroundColor: palette.cardBg,
                                            }}
                                        >
                                            <Typography sx={{fontSize: '0.74rem', color: palette.textMuted, fontWeight: 700}}>
                                                {item.label}
                                            </Typography>
                                            <Typography sx={{mt: 0.45, fontSize: '0.86rem', color: palette.text, fontWeight: 700, wordBreak: 'break-word'}}>
                                                {item.value}
                                            </Typography>
                                        </Box>
                                    ))}
                                </Box>
                            </Paper>
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
                        <Suspense fallback={<TabLoadingPanel palette={palette} label={t('Loading module...')}/>}>
                            {tabId === TRANSACTION_DRILLDOWN_TAB_ID ? (
                                <TransactionDrillDown palette={palette} t={t} onOpenTab={handleOpenTab}/>
                            ) : SECURITY_IDS.includes(tabId) ? (
                                renderSecurityTab(tabId)
                            ) : REPORT_IDS.includes(tabId) ? (
                                tabId === 'operations-command-center' ? (
                                    <OperationsCommandCenter palette={palette} t={t}/>
                                ) : tabId === 'executive-scorecards' ? (
                                    <ExecutiveScorecards palette={palette} t={t}/>
                                ) : tabId === 'store-scorecards' ? (
                                    <StoreScorecards palette={palette} t={t}/>
                                ) : tabId === 'recurrence-analytics' ? (
                                    <RecurrenceAnalytics palette={palette} t={t}/>
                                ) : (
                                    <RootCauseAnalytics palette={palette} t={t}/>
                                )
                            ) : ALERT_IDS.includes(tabId) ? (
                                <Alerts palette={palette} t={t}/>
                            ) : EXCEPTION_IDS.includes(tabId) ? (
                                tabId === 'approval-center' ? (
                                    <ApprovalCenter palette={palette} t={t}/>
                                ) : tabId === 'store-manager-lite' ? (
                                    <StoreManagerLite palette={palette} t={t}/>
                                ) : tabId === 'regional-incident-board' ? (
                                    <RegionalIncidentBoard palette={palette} t={t}/>
                                ) : tabId === 'noise-suppression' ? (
                                    <NoiseSuppression palette={palette} t={t}/>
                                ) : tabId === 'known-issues' ? (
                                    <KnownIssues palette={palette} t={t}/>
                                ) : tabId === 'ticketing-comms' ? (
                                    <TicketingCommunications palette={palette} t={t}/>
                                ) : tabId === 'routing-playbooks' ? (
                                    <RoutingPlaybooks palette={palette} t={t}/>
                                ) : (
                                    <ExceptionQueues palette={palette} t={t}/>
                                )
                            ) : OPERATION_IDS.includes(tabId) ? (
                                tabId === 'recon-jobs' ? (
                                    <ReconciliationJobs palette={palette} t={t}/>
                                ) : (
                                    <Operations palette={palette} t={t}/>
                                )
                            ) : INTEGRATION_IDS.includes(tabId) ? (
                                <IntegrationHub palette={palette} t={t}/>
                            ) : SLA_IDS.includes(tabId) ? (
                                <SlaManagement palette={palette} t={t}/>
                            ) : ACTIVITY_IDS.includes(tabId) ? (
                                <Activity palette={palette} t={t}/>
                            ) : CONFIGURATION_IDS.includes(tabId) ? (
                                <Configurations tabId={tabId} palette={palette} t={t}/>
                            ) : (
                                <ReconContent tabId={tabId} palette={palette} t={t} onOpenTab={handleOpenTab}/>
                            )}
                        </Suspense>
                    </Box>
                ))}
            </Box>
        </Box>
    )
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
