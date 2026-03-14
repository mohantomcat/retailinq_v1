import {useEffect, useMemo, useState} from 'react'
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
import {reconApi} from '../services/reconApi'
import ManageUsers from './admin/ManageUsers'
import ManageRoles from './admin/ManageRoles'
import ManagePermissions from './admin/ManagePermissions'
import {LocalizationProvider} from '@mui/x-date-pickers/LocalizationProvider'
import {DatePicker} from '@mui/x-date-pickers/DatePicker'
import {AdapterDayjs} from '@mui/x-date-pickers/AdapterDayjs'
import dayjs from 'dayjs'
import {useI18n} from '../context/I18nContext'
import {getTabLabel} from '../constants/navigation'

const KPI_STATUS_MAP = {
    total: null,
    matched: 'MATCHED',
    missingInSiocs: 'MISSING_IN_SIOCS',
    quantityMismatch: 'QUANTITY_MISMATCH',
    itemMissing: 'ITEM_MISSING',
    processingPending: 'PROCESSING_PENDING',
}

const PAGE_SIZE = 20
const SECURITY_IDS = ['manage-users', 'manage-roles', 'manage-perms']
const RECON_VIEW_BY_TAB = {
    'xstore-sim': 'XSTORE_SIM',
    'xstore-siocs': 'XSTORE_SIOCS',
}

const TAB_COLORS = {
    recon: {active: '#0F7C86', hover: '#157F8B'},
    security: {active: '#0F7C86', hover: '#157F8B'},
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
        pageBg: isDark ? '#0B1220' : '#F8FAFC',
        cardBg: isDark ? '#0F172A' : '#FFFFFF',
        cardBgAlt: isDark ? '#111827' : '#F8FAFC',
        border: isDark ? '#1E293B' : '#E2E8F0',
        borderSoft: isDark ? '#243041' : '#F1F5F9',
        text: isDark ? '#E2E8F0' : '#0F172A',
        textMuted: isDark ? '#94A3B8' : '#64748B',
        textSoft: isDark ? '#64748B' : '#94A3B8',
        hoverBg: isDark ? '#111827' : '#F8FAFC',
        selectedBg: isDark ? '#0B1220' : '#EFF6FF',
        selectedBorder: isDark ? '#1D4ED8' : '#BFDBFE',
        blueChipBg: isDark ? '#0F172A' : '#EFF6FF',
        blueChipText: '#2563EB',
        tealChipBg: isDark ? '#0F172A' : '#ECFEFF',
        tealChipText: '#0F7C86',
        neutralChipBg: isDark ? '#111827' : '#F1F5F9',
        neutralChipText: isDark ? '#CBD5E1' : '#334155',
        dangerBg: isDark ? '#2A1215' : '#fff5f5',
        dangerBgHover: isDark ? '#3A151A' : '#fee2e2',
        dangerBorder: '#fca5a5',
        dangerText: '#ef4444',
        scrollbarTrack: isDark ? '#0F172A' : '#f1f5f9',
        scrollbarThumb: isDark ? '#334155' : '#cbd5e1',
        tabBg: isDark ? '#0F172A' : '#FFFFFF',
        tabActiveBg: isDark ? '#111827' : '#F8FAFC',
        overlay1: isDark
            ? 'radial-gradient(circle, rgba(37,99,235,0.16), rgba(37,99,235,0))'
            : 'radial-gradient(circle, rgba(37,99,235,0.12), rgba(37,99,235,0))',
        overlay2: isDark
            ? 'radial-gradient(circle, rgba(15,124,134,0.14), rgba(15,124,134,0))'
            : 'radial-gradient(circle, rgba(15,124,134,0.10), rgba(15,124,134,0))',
        heroBg: isDark
            ? 'linear-gradient(135deg, #0F172A 0%, #111827 100%)'
            : 'linear-gradient(135deg, #FFFFFF 0%, #F8FAFC 100%)',
    }
}

function tabColor(id) {
    return SECURITY_IDS.includes(id)
        ? TAB_COLORS.security
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
                                    borderColor: '#2563EB',
                                },
                            },
                            '& .MuiInputLabel-root': {
                                color: palette.textMuted,
                            },
                            '& .MuiInputLabel-root.Mui-focused': {
                                color: '#2563EB',
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

function WelcomeLanding({palette, t}) {
    const reconModules = [
        {
            id: 'xstore-sim',
            title: t('Xstore vs SIM'),
            desc: t('Monitor transaction reconciliation, identify mismatches, and drill into operational exceptions.'),
            icon: <CompareArrowsRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('Reconciliation'),
            accent: '#0F7C86',
            bg: palette.isDark ? '#0F172A' : '#ECFEFF',
        },
        {
            id: 'xstore-siocs',
            title: t('Xstore vs SIOCS'),
            desc: t('Review reconciliation results produced from the cloud SIOCS connector against the existing Xstore source.'),
            icon: <CompareArrowsRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('Cloud Reconciliation'),
            accent: '#2563EB',
            bg: palette.isDark ? '#0F172A' : '#EFF6FF',
        },
        {
            id: 'manage-users',
            title: t('Manage Users'),
            desc: t('Control user access, store scope, and role assignments across your RetailINQ workspace.'),
            icon: <AdminPanelSettingsRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('Security'),
            accent: '#2563EB',
            bg: palette.isDark ? '#0F172A' : '#EFF6FF',
        },
        {
            id: 'manage-roles',
            title: t('Manage Roles'),
            desc: t('Define reusable access models and align permissions to business responsibilities.'),
            icon: <DashboardCustomizeRoundedIcon sx={{fontSize: 20}}/>,
            badge: t('Administration'),
            accent: '#7C3AED',
            bg: palette.isDark ? '#0F172A' : '#F5F3FF',
        },
    ]

    return (
        <Box sx={{px: 4, py: 4, maxWidth: 1480}}>
            <Paper
                elevation={0}
                sx={{
                    position: 'relative',
                    overflow: 'hidden',
                    borderRadius: '28px',
                    border: `1px solid ${palette.border}`,
                    background: palette.heroBg,
                    px: {xs: 3, md: 4},
                    py: {xs: 3.5, md: 4.5},
                    mb: 3,
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
                        gridTemplateColumns: {xs: '1fr', lg: '1.4fr 0.9fr'},
                        gap: 3,
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
                            }}
                        />

                        <Typography
                            sx={{
                                fontSize: {xs: '2rem', md: '2.5rem'},
                                fontWeight: 700,
                                color: palette.text,
                                lineHeight: 1.1,
                                letterSpacing: '-0.6px',
                                mb: 1.25,
                            }}
                        >
                            {t('Welcome to RetailINQ')}
                        </Typography>

                        <Typography
                            sx={{
                                fontSize: {xs: '1rem', md: '1.06rem'},
                                color: palette.textMuted,
                                lineHeight: 1.7,
                                maxWidth: 760,
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
                                mt: 2.5,
                            }}
                        >
                            <Chip
                                label={t('Modern SaaS Admin')}
                                size="small"
                                sx={{
                                    backgroundColor: palette.blueChipBg,
                                    color: palette.blueChipText,
                                    fontWeight: 700,
                                }}
                            />
                            <Chip
                                label={t('Reconciliation Insights')}
                                size="small"
                                sx={{
                                    backgroundColor: palette.tealChipBg,
                                    color: palette.tealChipText,
                                    fontWeight: 700,
                                }}
                            />
                            <Chip
                                label={t('Analytics + Integrations')}
                                size="small"
                                sx={{
                                    backgroundColor: palette.neutralChipBg,
                                    color: palette.neutralChipText,
                                    fontWeight: 700,
                                }}
                            />
                        </Box>
                    </Box>

                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
                            gap: 1.5,
                        }}
                    >
                        {[
                            [t('Workspace'), 'RetailINQ', t('Unified operations console')],
                            [t('Experience'), t('Premium SaaS'), t('Secure, modern, scalable')],
                            [t('Core Focus'), t('Reconciliation'), t('Exceptions, trends, controls')],
                            [t('Administration'), t('Users & Roles'), t('Access and governance')],
                        ].map(([label, title, desc]) => (
                            <Paper
                                key={label}
                                elevation={0}
                                sx={{
                                    p: 2.25,
                                    borderRadius: '22px',
                                    border: `1px solid ${palette.border}`,
                                    backgroundColor: palette.cardBg,
                                }}
                            >
                                <Typography
                                    sx={{
                                        fontSize: '0.78rem',
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
                                        fontSize: '1.2rem',
                                        fontWeight: 700,
                                        color: palette.text,
                                    }}
                                >
                                    {title}
                                </Typography>
                                <Typography
                                    sx={{
                                        mt: 0.5,
                                        fontSize: '0.84rem',
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
                        md: 'repeat(3, minmax(0, 1fr))',
                    },
                    gap: 2,
                }}
            >
                {reconModules.map((module) => (
                    <Paper
                        key={module.id}
                        elevation={0}
                        sx={{
                            p: 2.5,
                            borderRadius: '24px',
                            border: `1px solid ${palette.border}`,
                            backgroundColor: palette.cardBg,
                            transition: 'all 0.18s ease',
                            '&:hover': {
                                transform: 'translateY(-2px)',
                                boxShadow: palette.isDark
                                    ? '0 10px 24px rgba(0, 0, 0, 0.22)'
                                    : '0 10px 24px rgba(15, 23, 42, 0.06)',
                            },
                        }}
                    >
                        <Box
                            sx={{
                                width: 44,
                                height: 44,
                                borderRadius: '14px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                backgroundColor: module.bg,
                                color: module.accent,
                                mb: 1.5,
                            }}
                        >
                            {module.icon}
                        </Box>

                        <Chip
                            label={module.badge}
                            size="small"
                            sx={{
                                mb: 1.25,
                                backgroundColor: module.bg,
                                color: module.accent,
                                fontWeight: 700,
                                fontSize: '0.72rem',
                            }}
                        />

                        <Typography
                            sx={{
                                fontSize: '1.02rem',
                                fontWeight: 700,
                                color: palette.text,
                                mb: 0.75,
                            }}
                        >
                            {module.title}
                        </Typography>

                        <Typography
                            sx={{
                                fontSize: '0.88rem',
                                color: palette.textMuted,
                                lineHeight: 1.65,
                                mb: 1.75,
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
                                    fontSize: '0.84rem',
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

function ReconContent({tabId, palette, t}) {
    const reconView = RECON_VIEW_BY_TAB[tabId] || null
    const [kpis, setKpis] = useState({})
    const [selectedKpi, setSelectedKpi] = useState(null)
    const [detailData, setDetailData] = useState([])
    const [detailTitle, setDetailTitle] = useState('')
    const [loadingKpis, setLoadingKpis] = useState(false)
    const [loadingDetail, setLoadingDetail] = useState(false)
    const [page, setPage] = useState(0)
    const [totalElements, setTotalElements] = useState(0)
    const [stores, setStores] = useState([])
    const [registers, setRegisters] = useState([])
    const [selectedStores, setSelectedStores] = useState([])
    const [selectedRegisters, setSelectedRegisters] = useState([])
    const [fromDate, setFromDate] = useState('')
    const [toDate, setToDate] = useState('')

    useEffect(() => {
        reconApi.getStores(reconView).then(setStores).catch(console.error)
    }, [reconView])

    useEffect(() => {
        reconApi.getRegisters(
            selectedStores.length ? selectedStores : null,
            reconView
        )
            .then(setRegisters)
            .catch(console.error)
    }, [selectedStores, reconView])

    useEffect(() => {
        if (!reconView) {
            setKpis({})
            setSelectedKpi(null)
            setDetailData([])
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
                    itemMissing: stats.itemMissing,
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
        setDetailTitle('')
        setPage(0)
    }, [reconView, selectedStores, fromDate, toDate])

    useEffect(() => {
        if (!selectedKpi || !reconView) return

        const reconStatus = KPI_STATUS_MAP[selectedKpi]

        const load = async () => {
            setLoadingDetail(true)
            try {
                const result = await reconApi.getTransactions({
                    storeIds: selectedStores.length
                        ? selectedStores
                        : undefined,
                    wkstnIds: selectedRegisters.length
                        ? selectedRegisters
                        : undefined,
                    reconView,
                    fromBusinessDate: fromDate || undefined,
                    toBusinessDate: toDate || undefined,
                    reconStatus: reconStatus || undefined,
                    page,
                    size: PAGE_SIZE,
                })

                setDetailData(
                    (result.content || []).map((t) => ({
                        'Transaction ID': getTransactionIdFromExternalId(
                            t.externalId,
                            '-'
                        ),
                        Store: t.storeId,
                        Register: t.wkstnId ?? '-',
                        'Business Date':
                            t.businessDateDisplay || t.businessDate,
                        Type: t.transactionType,
                        Status: t.reconStatus,
                        'Reconciled At': t.reconciledAt,
                    }))
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
        fromDate,
        toDate,
        reconView,
    ])

    const handleKpiClick = (title, key) => {
        setDetailTitle(title)
        setSelectedKpi(key)
        setPage(0)
    }

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
                        {t('Register')}
                    </InputLabel>
                    <Select
                        multiple
                        value={selectedRegisters}
                        onChange={(e) => {
                            const v = e.target.value
                            setSelectedRegisters(
                                typeof v === 'string' ? v.split(',') : v
                            )
                        }}
                        input={<OutlinedInput label={t('Register')}/>}
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
                                            label={`R${v}`}
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
                                        label={`${sel.length} ${t('registers')}`}
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
                        {selectedRegisters.length > 0 && (
                            <MenuItem
                                onMouseDown={(e) => {
                                    e.preventDefault()
                                    e.stopPropagation()
                                    setSelectedRegisters([])
                                }}
                                sx={{
                                    color: '#ef4444',
                                    fontSize: '0.8rem',
                                    fontWeight: 500,
                                    borderBottom: `1px solid ${palette.borderSoft}`,
                                    py: 0.75,
                                }}
                            >
                                ✕&nbsp; Clear all registers
                            </MenuItem>
                        )}

                        {registers.map((r) => (
                            <MenuItem key={r} value={r}>
                                <Box
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 1,
                                    }}
                                >
                                    {renderCheckbox(
                                        selectedRegisters.includes(r)
                                    )}
                                    {`${t('Register')} ${r}`}
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
                    fromDate ||
                    toDate) && (
                    <Box
                        onClick={() => {
                            setSelectedStores([])
                            setSelectedRegisters([])
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
                <Grid container spacing={3} sx={{mb: 4}}>
                    {[
                        {title: t('Total Transactions'), key: 'total'},
                        {title: t('Matched'), key: 'matched'},
                        {title: t('Missing in SIM'), key: 'missingInSiocs'},
                        {title: t('Quantity Mismatch'), key: 'quantityMismatch'},
                        {title: t('Item Missing'), key: 'itemMissing'},
                        {title: t('Processing Pending'), key: 'processingPending'},
                    ].map((kpi) => (
                        <Grid item xs={12} sm={6} md={2} key={kpi.key}>
                            <KPI
                                title={kpi.title}
                                value={kpis[kpi.key]}
                                onClick={() =>
                                    handleKpiClick(kpi.title, kpi.key)
                                }
                                selected={selectedKpi === kpi.key}
                            />
                        </Grid>
                    ))}
                </Grid>
            )}

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
                        />

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
                                            borderColor: '#2563EB',
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
                                            borderColor: '#2563EB',
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
                        {SECURITY_IDS.includes(tabId) ? (
                            renderSecurityTab(tabId)
                        ) : (
                            <ReconContent tabId={tabId} palette={palette} t={t}/>
                        )}
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
        default:
            return null
    }
}
