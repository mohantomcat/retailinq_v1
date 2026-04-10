import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Drawer,
    FormControl,
    Grid,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    Tab,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tabs,
    TextField,
    Typography,
} from '@mui/material'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import {useAuth} from '../context/AuthContext'
import {integrationHubApi} from '../services/integrationHubApi'
import {useReconModules} from '../hooks/useReconModules'

const CONNECTOR_DELAY_MINUTES = 180
const FLOW_DELAY_MINUTES = 180
const SHELL_HEADER_HEIGHT = 72

function SummaryCard({label, value, supporting, palette, tone}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.65, fontSize: '1.5rem', fontWeight: 800, color: tone || palette.text}}>{value}</Typography>
            <Typography sx={{mt: 0.45, fontSize: '0.76rem', color: palette.textMuted}}>{supporting}</Typography>
        </Paper>
    )
}

function Section({title, subtitle, palette, action, children}) {
    return (
        <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
            <Box sx={{px: 2.5, py: 2, display: 'flex', justifyContent: 'space-between', gap: 2}}>
                <Box>
                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{title}</Typography>
                    {subtitle ? <Typography sx={{mt: 0.45, fontSize: '0.78rem', color: palette.textMuted}}>{subtitle}</Typography> : null}
                </Box>
                {action}
            </Box>
            <Divider />
            {children}
        </Paper>
    )
}

function EmptyState({title, description, palette}) {
    return (
        <Box sx={{px: 2.5, py: 3}}>
            <Typography sx={{fontSize: '0.88rem', fontWeight: 700, color: palette.text}}>{title}</Typography>
            <Typography sx={{mt: 0.55, fontSize: '0.78rem', color: palette.textMuted, maxWidth: 560}}>{description}</Typography>
        </Box>
    )
}

function MetaRow({label, value, palette}) {
    return (
        <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 2, py: 0.4}}>
            <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{fontSize: '0.78rem', color: palette.text, textAlign: 'right', wordBreak: 'break-word'}}>{value || '-'}</Typography>
        </Box>
    )
}

function RuntimeScopeChips({item, palette, t}) {
    const affectedLabels = item?.affectedReconLabels || []
    const isShared = item?.sharedAsset && affectedLabels.length > 1

    if (!item?.endpointMode && !isShared) {
        return null
    }

    return (
        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{mt: 0.55}}>
            {item?.endpointMode ? (
                <Chip
                    size="small"
                    label={item.endpointMode}
                    sx={{fontWeight: 700, backgroundColor: palette.blueChipBg, color: palette.blueChipText}}
                />
            ) : null}
            {isShared ? (
                <Chip
                    size="small"
                    label={t('Shared Runtime')}
                    sx={{fontWeight: 700, backgroundColor: '#FFF7ED', color: '#C2410C'}}
                />
            ) : null}
        </Stack>
    )
}

function SharedImpactCaption({item, palette, t}) {
    const affectedLabels = item?.affectedReconLabels || []
    const isShared = item?.sharedAsset && affectedLabels.length > 1

    if (!isShared) {
        return null
    }

    return (
        <Typography sx={{mt: 0.45, fontSize: '0.74rem', color: palette.textMuted}}>
            {t('Also impacts')}: {affectedLabels.join(', ')}
        </Typography>
    )
}

function parseDateValue(value) {
    if (!value) return null
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? null : parsed
}

function formatDateTimeValue(value) {
    if (!value) return '-'
    const parsed = parseDateValue(value)
    return parsed ? parsed.toLocaleString() : value.replace('T', ' ')
}

function formatRelativeAge(value) {
    const parsed = parseDateValue(value)
    if (!parsed) return 'No recent activity'
    const diffMinutes = Math.max(0, Math.floor((Date.now() - parsed.getTime()) / 60000))
    if (diffMinutes < 1) return 'Just now'
    if (diffMinutes < 60) return `${diffMinutes}m ago`
    const diffHours = Math.floor(diffMinutes / 60)
    if (diffHours < 24) return `${diffHours}h ago`
    return `${Math.floor(diffHours / 24)}d ago`
}

function formatDuration(startedAt, completedAt) {
    const start = parseDateValue(startedAt)
    const end = parseDateValue(completedAt)
    if (!start || !end) return '-'
    const totalSeconds = Math.max(0, Math.floor((end.getTime() - start.getTime()) / 1000))
    if (totalSeconds < 60) return `${totalSeconds}s`
    const minutes = Math.floor(totalSeconds / 60)
    if (minutes < 60) return `${minutes}m ${totalSeconds % 60}s`
    return `${Math.floor(minutes / 60)}h ${minutes % 60}m`
}

function ageMinutes(value) {
    const parsed = parseDateValue(value)
    return parsed ? Math.max(0, Math.floor((Date.now() - parsed.getTime()) / 60000)) : null
}

function toneChip(tone, palette) {
    if (tone === 'success') return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
    if (tone === 'info') return {backgroundColor: palette.blueChipBg, color: palette.blueChipText}
    if (tone === 'warning') return {backgroundColor: '#FFF7ED', color: '#C2410C'}
    if (tone === 'error') return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
    return {backgroundColor: palette.cardBgAlt, color: palette.textMuted}
}

function statusChip(status, palette) {
    const value = (status || '').toUpperCase()
    if (['COMPLETED', 'PUBLISHED', 'HEALTHY'].includes(value)) return toneChip('success', palette)
    if (['RUNNING', 'QUEUED', 'REQUESTED', 'ACTIVE'].includes(value)) return toneChip('info', palette)
    if (['DELAYED', 'WARNING', 'UNSUPPORTED'].includes(value)) return toneChip('warning', palette)
    if (['FAILED', 'OPEN', 'ERROR', 'COMPLETED_WITH_ERRORS'].includes(value)) return toneChip('error', palette)
    return toneChip('neutral', palette)
}

function connectorHealth(item) {
    const lastActivityAt = item.lastPublishedAt || item.latestRunCompletedAt || item.latestRunStartedAt
    const lag = ageMinutes(lastActivityAt)
    const status = (item.latestRunStatus || '').toUpperCase()
    if (!item.enabled) return {label: 'Disabled', tone: 'neutral', detail: 'Connector is disabled', lastActivityAt}
    if (status === 'FAILED' || item.openErrorCount > 0 || item.failedMessagesLast24Hours > 0) {
        return {label: 'Failed', tone: 'error', detail: 'Errors or failed publishes need support action', lastActivityAt}
    }
    if (status === 'RUNNING' || status === 'QUEUED') return {label: 'Active', tone: 'info', detail: 'Connector run is in progress', lastActivityAt}
    if (!lastActivityAt) return {label: 'Idle', tone: 'neutral', detail: 'No recorded connector activity', lastActivityAt}
    if (lag !== null && lag > CONNECTOR_DELAY_MINUTES) return {label: 'Delayed', tone: 'warning', detail: `Last activity ${formatRelativeAge(lastActivityAt)}`, lastActivityAt}
    return {label: 'Healthy', tone: 'success', detail: `Last activity ${formatRelativeAge(lastActivityAt)}`, lastActivityAt}
}

function flowHealth(item) {
    const lastActivityAt = item.lastPublishedAt || item.latestRunCompletedAt || item.latestRunStartedAt
    const lag = ageMinutes(lastActivityAt)
    const status = (item.latestRunStatus || '').toUpperCase()
    if (!item.enabled) return {label: 'Disabled', tone: 'neutral', detail: 'Flow is disabled', lastActivityAt}
    if (status === 'FAILED' || item.errorsLast24Hours > 0) return {label: 'Failed', tone: 'error', detail: 'Recent failures or error queue activity', lastActivityAt}
    if (status === 'RUNNING' || status === 'QUEUED') return {label: 'Active', tone: 'info', detail: 'Flow is currently processing', lastActivityAt}
    if (!lastActivityAt) return {label: 'Idle', tone: 'neutral', detail: 'No published flow activity yet', lastActivityAt}
    if (lag !== null && lag > FLOW_DELAY_MINUTES) return {label: 'Delayed', tone: 'warning', detail: `Last message ${formatRelativeAge(lastActivityAt)}`, lastActivityAt}
    return {label: 'Healthy', tone: 'success', detail: `Last message ${formatRelativeAge(lastActivityAt)}`, lastActivityAt}
}

function formatReplayWindow(request) {
    if (request.requestedFrom && request.requestedTo) return `${formatDateTimeValue(request.requestedFrom)} -> ${formatDateTimeValue(request.requestedTo)}`
    return request.businessKey || request.documentId || '-'
}

function toLocalDateTimeInputValue(value, endOfDay = false) {
    if (!value) return ''
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) return ''
    const normalized = new Date(parsed)
    normalized.setHours(endOfDay ? 23 : 0, endOfDay ? 59 : 0, 0, 0)
    const year = normalized.getFullYear()
    const month = String(normalized.getMonth() + 1).padStart(2, '0')
    const day = String(normalized.getDate()).padStart(2, '0')
    const hours = String(normalized.getHours()).padStart(2, '0')
    const minutes = String(normalized.getMinutes()).padStart(2, '0')
    return `${year}-${month}-${day}T${hours}:${minutes}`
}

export default function IntegrationHub({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptions, labelByValue} = useReconModules()
    const canView = hasPermission('INTEGRATION_HUB_VIEW')
    const canReplay = hasPermission('INTEGRATION_HUB_REPLAY')
    const [selectedReconView, setSelectedReconView] = useState('')
    const [loading, setLoading] = useState(true)
    const [messagesLoading, setMessagesLoading] = useState(false)
    const [messageDetailLoading, setMessageDetailLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [executingReplayId, setExecutingReplayId] = useState('')
    const [resolvingErrorId, setResolvingErrorId] = useState('')
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')
    const [data, setData] = useState(null)
    const [messageTrace, setMessageTrace] = useState([])
    const [messageTraceLoaded, setMessageTraceLoaded] = useState(false)
    const [selectedMessageDetail, setSelectedMessageDetail] = useState(null)
    const [selectedRun, setSelectedRun] = useState(null)
    const [activeTab, setActiveTab] = useState(0)
    const [replayDialog, setReplayDialog] = useState({open: false, mode: 'request', payload: null})
    const [form, setForm] = useState({connectorKey: '', flowKey: '', businessKey: '', documentId: '', requestedFrom: '', requestedTo: '', reason: ''})
    const [messageFilters, setMessageFilters] = useState({connectorKey: '', flowKey: '', businessKey: '', documentId: '', messageStatus: ''})
    const selectedReconLabel = labelByValue[selectedReconView] || moduleOptions[0]?.label || ''

    const loadData = async (reconView = selectedReconView) => {
        try {
            setLoading(true)
            setError('')
            const response = await integrationHubApi.getHub(reconView || null)
            setData(response)
            setForm((current) => ({
                ...current,
                connectorKey: current.connectorKey || response?.connectors?.[0]?.connectorKey || '',
                flowKey: current.flowKey || response?.flows?.[0]?.flowKey || '',
            }))
        } catch (err) {
            setError(err.message || 'Failed to load Integration Hub')
        } finally {
            setLoading(false)
        }
    }

    const loadMessageTrace = async (filters = messageFilters, reconView = selectedReconView) => {
        try {
            setMessagesLoading(true)
            setError('')
            const response = await integrationHubApi.getMessages({...filters, limit: 20}, reconView || null)
            setMessageTrace(response || [])
            setMessageTraceLoaded(true)
        } catch (err) {
            setError(err.message || 'Failed to load message trace')
        } finally {
            setMessagesLoading(false)
        }
    }

    const loadMessageDetail = async (messageId, reconView = selectedReconView) => {
        try {
            setMessageDetailLoading(true)
            setError('')
            const response = await integrationHubApi.getMessageDetail(messageId, reconView || null)
            setSelectedMessageDetail(response)
        } catch (err) {
            setError(err.message || 'Failed to load message detail')
        } finally {
            setMessageDetailLoading(false)
        }
    }

    useEffect(() => {
        if (!moduleOptions.length) {
            setSelectedReconView('')
            return
        }
        if (!moduleOptions.some((option) => option.value === selectedReconView)) {
            setSelectedReconView(moduleOptions[0].value)
        }
    }, [moduleOptions, selectedReconView])

    useEffect(() => {
        if (!canView) {
            return
        }
        if (moduleOptions.length && !selectedReconView) {
            return
        }
        setMessageTrace([])
        setMessageTraceLoaded(false)
        setSelectedMessageDetail(null)
        setSelectedRun(null)
        setMessageFilters({connectorKey: '', flowKey: '', businessKey: '', documentId: '', messageStatus: ''})
        loadData(selectedReconView)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canView, selectedReconView, moduleOptions.length])

    useEffect(() => {
        if (canView && activeTab === 1 && !messageTraceLoaded) loadMessageTrace({connectorKey: '', flowKey: '', businessKey: '', documentId: '', messageStatus: ''}, selectedReconView)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeTab, canView, messageTraceLoaded, selectedReconView])

    const summary = data?.summary || {}
    const connectors = useMemo(() => data?.connectors || [], [data])
    const flows = useMemo(() => data?.flows || [], [data])
    const recentRuns = useMemo(() => data?.recentRuns || [], [data])
    const openErrors = useMemo(() => data?.openErrors || [], [data])
    const replayRequests = useMemo(() => data?.replayRequests || [], [data])
    const connectorLookup = useMemo(() => new Map(connectors.map((item) => [item.connectorKey, item])), [connectors])
    const flowLookup = useMemo(() => new Map(flows.map((item) => [item.flowKey, item])), [flows])
    const executableReplayConnectors = useMemo(() => new Set(['siocs-cloud-main', 'mfcs-rds-main', 'xocs-cloud-main']), [])

    if (!canView) return null

    const businessImpact = [
        {
            title: t('POS publishing'),
            status: connectors.some((item) => ['XSTORE', 'XOCS'].includes((item.sourceSystem || '').toUpperCase()) && ['Failed', 'Delayed'].includes(connectorHealth(item).label)) ? t('Needs attention') : t('Healthy'),
            tone: connectors.some((item) => ['XSTORE', 'XOCS'].includes((item.sourceSystem || '').toUpperCase()) && ['Failed', 'Delayed'].includes(connectorHealth(item).label)) ? 'warning' : 'success',
            detail: t('Use this view to translate connector issues into retailer-facing impact.'),
        },
        {
            title: t('Inventory sync'),
            status: connectors.some((item) => ['SIM', 'SIOCS', 'MFCS', 'RMS'].includes((item.sourceSystem || '').toUpperCase()) && ['Failed', 'Delayed'].includes(connectorHealth(item).label)) ? t('Watch closely') : t('Healthy'),
            tone: connectors.some((item) => ['SIM', 'SIOCS', 'MFCS', 'RMS'].includes((item.sourceSystem || '').toUpperCase()) && ['Failed', 'Delayed'].includes(connectorHealth(item).label)) ? 'warning' : 'success',
            detail: t('Inventory-facing flows are separated here so support teams can act early.'),
        },
        {
            title: t('Recovery queue'),
            status: (summary.openErrors || 0) > 0 || (summary.pendingReplayRequests || 0) > 0 ? t('Action required') : t('Clear'),
            tone: (summary.openErrors || 0) > 0 || (summary.pendingReplayRequests || 0) > 0 ? 'info' : 'success',
            detail: t('Open errors and replay requests are support/admin work, not standard business exceptions.'),
        },
    ]

    const openMessageDetail = async (messageId) => {
        setActiveTab(1)
        if (!messageTraceLoaded) await loadMessageTrace(messageFilters, selectedReconView)
        await loadMessageDetail(messageId, selectedReconView)
    }

    const applyReplayScope = ({connectorKey, flowKey, businessKey, documentId, timestamp, reason}) => {
        setActiveTab(2)
        setForm((current) => ({
            ...current,
            connectorKey: connectorKey || current.connectorKey,
            flowKey: flowKey || current.flowKey,
            businessKey: businessKey || '',
            documentId: documentId || '',
            requestedFrom: toLocalDateTimeInputValue(timestamp, false),
            requestedTo: toLocalDateTimeInputValue(timestamp, true),
            reason: reason || current.reason,
        }))
        setSuccess(t('Replay request form populated from the selected trace'))
    }

    const openReplayRequestDialog = () => {
        if (!form.connectorKey || !form.flowKey) {
            setError(t('Connector and flow are required before raising a replay request'))
            return
        }
        setReplayDialog({open: true, mode: 'request', payload: {...form}})
    }

    const openReplayExecutionDialog = (request) => setReplayDialog({open: true, mode: 'execute', payload: request})
    const closeReplayDialog = () => {
        if (!saving && !executingReplayId) setReplayDialog({open: false, mode: 'request', payload: null})
    }

    const submitReplayRequest = async () => {
        try {
            setSaving(true)
            setError('')
            setSuccess('')
            await integrationHubApi.createReplayRequest(replayDialog.payload, selectedReconView || null)
            setReplayDialog({open: false, mode: 'request', payload: null})
            setSuccess(t('Replay request created and added to the recovery queue'))
            setForm((current) => ({...current, businessKey: '', documentId: '', requestedFrom: '', requestedTo: '', reason: ''}))
            await loadData(selectedReconView)
        } catch (err) {
            setError(err.message || 'Failed to create replay request')
        } finally {
            setSaving(false)
        }
    }

    const executeReplay = async (requestId) => {
        try {
            setExecutingReplayId(requestId)
            setError('')
            setSuccess('')
            const response = await integrationHubApi.executeReplayRequest(requestId, selectedReconView || null)
            const replayStatus = (response?.replayStatus || '').toUpperCase()
            const message = response?.resolutionMessage || t('Replay execution updated')
            if (replayStatus === 'FAILED' || replayStatus === 'UNSUPPORTED') setError(message)
            else setSuccess(message)
            setReplayDialog({open: false, mode: 'request', payload: null})
            await loadData(selectedReconView)
            if (selectedMessageDetail?.id) await loadMessageDetail(selectedMessageDetail.id, selectedReconView)
        } catch (err) {
            setError(err.message || 'Failed to execute replay request')
        } finally {
            setExecutingReplayId('')
        }
    }

    const confirmReplayAction = async () => {
        if (replayDialog.mode === 'request') await submitReplayRequest()
        if (replayDialog.mode === 'execute' && replayDialog.payload?.id) await executeReplay(replayDialog.payload.id)
    }

    const resolveError = async (item) => {
        try {
            setResolvingErrorId(item.id)
            setError('')
            setSuccess('')
            await integrationHubApi.resolveError(
                item.id,
                {resolutionNotes: `Resolved from Integration Hub for ${item.businessKey || item.documentId || item.connectorKey}`},
                selectedReconView || null
            )
            setSuccess(t('Integration error marked as resolved'))
            await loadData(selectedReconView)
            if (selectedMessageDetail?.id) await loadMessageDetail(selectedMessageDetail.id, selectedReconView)
        } catch (err) {
            setError(err.message || 'Failed to resolve integration error')
        } finally {
            setResolvingErrorId('')
        }
    }

    const replayConnector = replayDialog.payload ? connectorLookup.get(replayDialog.payload.connectorKey) : null
    const replayFlow = replayDialog.payload ? flowLookup.get(replayDialog.payload.flowKey) : null

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5, flexWrap: 'wrap'}}>
                    <Box sx={{mt: 0.3, width: 42, height: 42, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.blueChipBg, color: palette.blueChipText, flexShrink: 0}}>
                        <HubOutlinedIcon sx={{fontSize: 22}} />
                    </Box>
                    <Box sx={{flex: 1}}>
                        <Stack direction={{xs: 'column', sm: 'row'}} spacing={1} alignItems={{xs: 'flex-start', sm: 'center'}}>
                            <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Integration Hub')}</Typography>
                            <Chip size="small" label={t('Admin / Integration Ops')} sx={{fontWeight: 700, ...toneChip('info', palette)}} />
                        </Stack>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Technical operations console for integration admins, platform operators, and support teams. Use it for connector health, trace investigation, and replay recovery rather than day-to-day business exception work.')}
                        </Typography>
                    </Box>
                    <Box sx={{minWidth: {xs: '100%', md: 260}, ml: 'auto'}}>
                        <FormControl fullWidth size="small">
                            <InputLabel>{t('Reconciliation Lane')}</InputLabel>
                            <Select
                                value={selectedReconView}
                                label={t('Reconciliation Lane')}
                                onChange={(event) => setSelectedReconView(event.target.value)}
                                disabled={!moduleOptions.length}
                            >
                                {moduleOptions.map((option) => (
                                    <MenuItem key={option.value} value={option.value}>
                                        {option.label}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Box>
                </Box>
            </Paper>

            {selectedReconLabel ? (
                <Alert severity="info" sx={{mb: 2}}>
                    {t('Showing lane-scoped integration activity for')} {selectedReconLabel}. {t('Shared runtime markers identify connectors that can also affect other allowed reconciliation lanes.')}
                </Alert>
            ) : null}
            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}
            {success ? <Alert severity="success" sx={{mb: 2}}>{success}</Alert> : null}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress /></Box>
            ) : (
                <>
                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(6, minmax(0, 1fr))'}, gap: 1.5, mb: 3}}>
                        <SummaryCard label={t('Active Connectors')} value={summary.activeConnectors || 0} supporting={t('Registered connector runtimes')} tone={palette.text} palette={palette} />
                        <SummaryCard label={t('Active Flows')} value={summary.activeFlows || 0} supporting={t('Configured canonical and runtime flows')} tone={palette.blueChipText} palette={palette} />
                        <SummaryCard label={t('Active Runs')} value={summary.runningRuns || 0} supporting={t('Connector runs in progress now')} tone="#D97706" palette={palette} />
                        <SummaryCard label={t('Failed Runs (24h)')} value={summary.failedRunsLast24Hours || 0} supporting={t('Runs that ended in failure in the last day')} tone="#B91C1C" palette={palette} />
                        <SummaryCard label={t('Open Integration Errors')} value={summary.openErrors || 0} supporting={t('Messages needing support intervention')} tone="#B91C1C" palette={palette} />
                        <SummaryCard label={t('Published Messages (24h)')} value={summary.publishedMessagesLast24Hours || 0} supporting={t('Tracked messages published in the last day')} tone="#15803D" palette={palette} />
                    </Box>

                    <Paper elevation={0} sx={{mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Tabs value={activeTab} onChange={(_, value) => setActiveTab(value)} variant="scrollable" scrollButtons="auto" sx={{px: 1.5, '& .MuiTab-root': {minHeight: 56, fontWeight: 700}}}>
                            <Tab label={t('Overview')} />
                            <Tab label={t('Message Trace')} />
                            <Tab label={t('Replay & Recovery')} />
                        </Tabs>
                    </Paper>

                    {activeTab === 0 ? (
                        <Stack spacing={2.2}>
                            <Section title={t('Business Impact')} subtitle={t('Translate integration health into business-facing impact for retail support and operations teams.')} palette={palette}>
                                <Box sx={{p: 2.5}}>
                                    <Grid container spacing={1.5}>
                                        {businessImpact.map((item) => (
                                            <Grid size={{xs: 12, md: 4}} key={item.title}>
                                                <Paper elevation={0} sx={{p: 1.7, height: '100%', borderRadius: '18px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                                    <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{item.title}</Typography>
                                                        <Chip size="small" label={item.status} sx={{fontWeight: 700, ...toneChip(item.tone, palette)}} />
                                                    </Stack>
                                                    <Typography sx={{mt: 0.7, fontSize: '0.76rem', color: palette.textMuted}}>{item.detail}</Typography>
                                                </Paper>
                                            </Grid>
                                        ))}
                                    </Grid>
                                </Box>
                            </Section>

                            <Grid container spacing={2.2}>
                                <Grid size={{xs: 12, xl: 7}}>
                                    <Section title={t('Connector Runtime')} subtitle={t('Operational visibility for connector health, last activity, volume, and error pressure.')} palette={palette}>
                                        <TableContainer>
                                            <Table size="small">
                                                <TableHead>
                                                    <TableRow>
                                                        <TableCell>{t('Connector')}</TableCell>
                                                        <TableCell>{t('Health')}</TableCell>
                                                        <TableCell>{t('Systems')}</TableCell>
                                                        <TableCell>{t('Last Activity')}</TableCell>
                                                        <TableCell>{t('24h Volume')}</TableCell>
                                                    </TableRow>
                                                </TableHead>
                                                <TableBody>
                                                    {connectors.length ? connectors.map((item) => {
                                                        const health = connectorHealth(item)
                                                        return (
                                                            <TableRow key={item.connectorKey} hover>
                                                                <TableCell>
                                                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{item.connectorLabel}</Typography>
                                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{item.connectorKey}</Typography>
                                                                    <RuntimeScopeChips item={item} palette={palette} t={t} />
                                                                    <SharedImpactCaption item={item} palette={palette} t={t} />
                                                                </TableCell>
                                                                <TableCell sx={{minWidth: 180}}>
                                                                    <Chip size="small" label={t(health.label)} sx={{fontWeight: 700, ...toneChip(health.tone, palette)}} />
                                                                    <Typography sx={{mt: 0.45, fontSize: '0.75rem', color: palette.textMuted}}>{t(health.detail)}</Typography>
                                                                </TableCell>
                                                                <TableCell>
                                                                    <Typography sx={{fontSize: '0.8rem', color: palette.text}}>{item.sourceSystem} {'->'} {item.targetSystem || 'RECON'}</Typography>
                                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.connectorType}</Typography>
                                                                </TableCell>
                                                                <TableCell>
                                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>{formatDateTimeValue(health.lastActivityAt)}</Typography>
                                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{formatRelativeAge(health.lastActivityAt)}</Typography>
                                                                </TableCell>
                                                                <TableCell>
                                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>{item.publishedMessagesLast24Hours || 0} {t('published')}</Typography>
                                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.failedMessagesLast24Hours || 0} {t('failed')} • {item.openErrorCount || 0} {t('open errors')}</Typography>
                                                                </TableCell>
                                                            </TableRow>
                                                        )
                                                    }) : (
                                                        <TableRow><TableCell colSpan={5}><EmptyState title={t('No connector runtimes are registered yet')} description={t('Connectors will appear here once the Integration Hub catalog is seeded and runtime journaling is active for this tenant.')} palette={palette} /></TableCell></TableRow>
                                                    )}
                                                </TableBody>
                                            </Table>
                                        </TableContainer>
                                    </Section>
                                </Grid>

                                <Grid size={{xs: 12, xl: 5}}>
                                    <Section title={t('Flow Health')} subtitle={t('Lag and status view for monitored integration flows.')} palette={palette}>
                                        <TableContainer>
                                            <Table size="small">
                                                <TableHead>
                                                    <TableRow>
                                                        <TableCell>{t('Flow')}</TableCell>
                                                        <TableCell>{t('Health')}</TableCell>
                                                        <TableCell>{t('Last Message')}</TableCell>
                                                    </TableRow>
                                                </TableHead>
                                                <TableBody>
                                                    {flows.length ? flows.map((item) => {
                                                        const health = flowHealth(item)
                                                        return (
                                                            <TableRow key={item.flowKey} hover>
                                                                <TableCell>
                                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{item.flowLabel}</Typography>
                                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.messageType}</Typography>
                                                                    <RuntimeScopeChips item={item} palette={palette} t={t} />
                                                                    <SharedImpactCaption item={item} palette={palette} t={t} />
                                                                </TableCell>
                                                                <TableCell sx={{minWidth: 180}}>
                                                                    <Chip size="small" label={t(health.label)} sx={{fontWeight: 700, ...toneChip(health.tone, palette)}} />
                                                                    <Typography sx={{mt: 0.45, fontSize: '0.74rem', color: palette.textMuted}}>{item.messagesLast24Hours || 0} {t('messages')} • {item.errorsLast24Hours || 0} {t('errors')}</Typography>
                                                                </TableCell>
                                                                <TableCell>
                                                                    <Typography sx={{fontSize: '0.8rem', color: palette.text}}>{formatDateTimeValue(health.lastActivityAt)}</Typography>
                                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{formatRelativeAge(health.lastActivityAt)}</Typography>
                                                                </TableCell>
                                                            </TableRow>
                                                        )
                                                    }) : (
                                                        <TableRow><TableCell colSpan={3}><EmptyState title={t('No monitored flows are available')} description={t('Flow monitoring is populated after connector catalog seeding and message journaling are active for the tenant.')} palette={palette} /></TableCell></TableRow>
                                                    )}
                                                </TableBody>
                                            </Table>
                                        </TableContainer>
                                    </Section>
                                </Grid>
                            </Grid>

                            <Grid container spacing={2.2}>
                                <Grid size={{xs: 12, xl: 7}}>
                                    <Section title={t('Recent Runs')} subtitle={t('What just happened, how long it took, and whether support should care.')} palette={palette}>
                                        {!recentRuns.length ? (
                                            <EmptyState title={t('No connector runs have been journaled yet')} description={t('Recent execution history will appear here after a connector run starts publishing Integration Hub run entries.')} palette={palette} />
                                        ) : (
                                            <TableContainer>
                                                <Table size="small">
                                                    <TableHead><TableRow><TableCell>{t('Run')}</TableCell><TableCell>{t('Status')}</TableCell><TableCell>{t('Duration')}</TableCell><TableCell>{t('Result')}</TableCell><TableCell>{t('Actions')}</TableCell></TableRow></TableHead>
                                                    <TableBody>
                                                        {recentRuns.map((run) => (
                                                            <TableRow key={run.id} hover>
                                                                <TableCell><Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{connectorLookup.get(run.connectorKey)?.connectorLabel || run.connectorKey}</Typography><Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{formatDateTimeValue(run.startedAt)} • {run.triggerType || '-'}</Typography><RuntimeScopeChips item={run} palette={palette} t={t} /><SharedImpactCaption item={run} palette={palette} t={t} /></TableCell>
                                                                <TableCell><Chip size="small" label={run.runStatus} sx={{fontWeight: 700, ...statusChip(run.runStatus, palette)}} /></TableCell>
                                                                <TableCell>{formatDuration(run.startedAt, run.completedAt)}</TableCell>
                                                                <TableCell sx={{minWidth: 220}}><Typography sx={{fontSize: '0.8rem', color: palette.text}}>{run.publishedRecordCount || 0} {t('published')}</Typography><Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{run.errorCount || 0} {t('errors')} • {run.sourceRecordCount || 0} {t('source records')}</Typography></TableCell>
                                                                <TableCell><Button variant="outlined" size="small" onClick={() => setSelectedRun(run)}>{t('View Run')}</Button></TableCell>
                                                            </TableRow>
                                                        ))}
                                                    </TableBody>
                                                </Table>
                                            </TableContainer>
                                        )}
                                    </Section>
                                </Grid>

                                <Grid size={{xs: 12, xl: 5}}>
                                    <Section title={t('Open Integration Errors')} subtitle={t('Current technical issues requiring support follow-up.')} palette={palette} action={openErrors.length ? <Button variant="outlined" size="small" onClick={() => setActiveTab(2)}>{t('Open Recovery Console')}</Button> : null}>
                                        {!openErrors.length ? (
                                            <EmptyState title={t('No active integration errors')} description={t('All monitored connectors are healthy right now. If a connector starts failing, the affected messages will appear here with trace and replay shortcuts.')} palette={palette} />
                                        ) : (
                                            <Stack spacing={1.1} sx={{p: 2.5}}>
                                                {openErrors.slice(0, 4).map((item) => (
                                                    <Paper key={item.id} elevation={0} sx={{p: 1.3, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                                        <Stack direction="row" justifyContent="space-between" spacing={1}>
                                                            <Box>
                                                                <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{connectorLookup.get(item.connectorKey)?.connectorLabel || item.connectorKey}</Typography>
                                                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.businessKey || item.documentId || '-'}</Typography>
                                                                <RuntimeScopeChips item={item} palette={palette} t={t} />
                                                                <SharedImpactCaption item={item} palette={palette} t={t} />
                                                            </Box>
                                                            <Chip size="small" label={item.errorType} sx={{fontWeight: 700, ...statusChip(item.errorStatus, palette)}} />
                                                        </Stack>
                                                        <Typography sx={{mt: 0.55, fontSize: '0.74rem', color: palette.textMuted}}>{item.errorMessage}</Typography>
                                                    </Paper>
                                                ))}
                                            </Stack>
                                        )}
                                    </Section>
                                </Grid>
                            </Grid>
                        </Stack>
                    ) : null}

                    {activeTab === 1 ? (
                        <Section title={t('Message Trace')} subtitle={t('Investigate connector messages, correlation ids, payload snapshots, and replay reuse.')} palette={palette} action={<Button variant="outlined" size="small" onClick={() => loadMessageTrace()} disabled={messagesLoading}>{messagesLoading ? t('Refreshing...') : t('Refresh Trace')}</Button>}>
                            <Box sx={{p: 2.5}}>
                                <Box
                                    sx={{
                                        display: 'grid',
                                        gap: 1.2,
                                        gridTemplateColumns: {
                                            xs: '1fr',
                                            md: 'repeat(2, minmax(0, 1fr))',
                                            xl: 'repeat(3, minmax(0, 1fr))',
                                        },
                                        '& .MuiFormControl-root, & .MuiTextField-root': {
                                            minWidth: 0,
                                        },
                                    }}
                                >
                                    <FormControl size="small" fullWidth><InputLabel>{t('Connector')}</InputLabel><Select value={messageFilters.connectorKey} label={t('Connector')} onChange={(event) => setMessageFilters((current) => ({...current, connectorKey: event.target.value}))}><MenuItem value="">{t('All connectors')}</MenuItem>{connectors.map((item) => <MenuItem key={item.connectorKey} value={item.connectorKey}>{item.connectorLabel}</MenuItem>)}</Select></FormControl>
                                    <FormControl size="small" fullWidth><InputLabel>{t('Flow')}</InputLabel><Select value={messageFilters.flowKey} label={t('Flow')} onChange={(event) => setMessageFilters((current) => ({...current, flowKey: event.target.value}))}><MenuItem value="">{t('All flows')}</MenuItem>{flows.map((item) => <MenuItem key={item.flowKey} value={item.flowKey}>{item.flowLabel}</MenuItem>)}</Select></FormControl>
                                    <FormControl size="small" fullWidth><InputLabel>{t('Status')}</InputLabel><Select value={messageFilters.messageStatus} label={t('Status')} onChange={(event) => setMessageFilters((current) => ({...current, messageStatus: event.target.value}))}><MenuItem value="">{t('All statuses')}</MenuItem>{['PUBLISHED', 'FAILED', 'QUEUED', 'OPEN'].map((item) => <MenuItem key={item} value={item}>{item}</MenuItem>)}</Select></FormControl>
                                    <TextField size="small" fullWidth label={t('Business Key / Transaction Id')} value={messageFilters.businessKey} onChange={(event) => setMessageFilters((current) => ({...current, businessKey: event.target.value}))} />
                                    <TextField size="small" fullWidth label={t('Document / Reference Id')} value={messageFilters.documentId} onChange={(event) => setMessageFilters((current) => ({...current, documentId: event.target.value}))} />
                                </Box>
                                <Stack direction="row" spacing={1} sx={{mt: 1.5}}>
                                    <Button variant="contained" onClick={() => loadMessageTrace(messageFilters)} disabled={messagesLoading}>{messagesLoading ? t('Searching...') : t('Search Trace')}</Button>
                                    <Button variant="outlined" onClick={() => { const reset = {connectorKey: '', flowKey: '', businessKey: '', documentId: '', messageStatus: ''}; setMessageFilters(reset); loadMessageTrace(reset) }} disabled={messagesLoading}>{t('Clear')}</Button>
                                </Stack>
                            </Box>
                            <Divider />
                            {messagesLoading ? <Box sx={{display: 'flex', justifyContent: 'center', py: 6}}><CircularProgress size={28} /></Box> : !messageTrace.length ? <EmptyState title={t('No trace messages matched the current filters')} description={t('Try a broader connector or flow scope. When traffic is healthy, recent canonical message traces will appear here for investigation and replay reuse.')} palette={palette} /> : (
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead><TableRow><TableCell>{t('Message')}</TableCell><TableCell>{t('Flow')}</TableCell><TableCell>{t('Status')}</TableCell><TableCell>{t('Last Seen')}</TableCell><TableCell>{t('Actions')}</TableCell></TableRow></TableHead>
                                        <TableBody>
                                            {messageTrace.map((item) => (
                                                <TableRow key={item.id} hover>
                                                    <TableCell><Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{item.businessKey || item.documentId || item.messageId}</Typography><Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{item.connectorKey} • {item.messageType}</Typography></TableCell>
                                                    <TableCell><Typography sx={{fontSize: '0.82rem', color: palette.text}}>{flowLookup.get(item.flowKey)?.flowLabel || item.flowKey}</Typography><Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{item.sourceSystem} {'->'} {item.targetSystem || 'RECON'}</Typography></TableCell>
                                                    <TableCell><Chip size="small" label={item.messageStatus} sx={{fontWeight: 700, ...statusChip(item.messageStatus, palette)}} /><Typography sx={{mt: 0.45, fontSize: '0.72rem', color: palette.textMuted}}>{t('Correlation')}: {item.traceId || '-'}</Typography></TableCell>
                                                    <TableCell><Typography sx={{fontSize: '0.82rem', color: palette.text}}>{formatDateTimeValue(item.updatedAt || item.createdAt)}</Typography><Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{formatRelativeAge(item.updatedAt || item.createdAt)}</Typography></TableCell>
                                                    <TableCell sx={{minWidth: 220}}><Stack direction="row" spacing={1}><Button variant="outlined" size="small" onClick={() => openMessageDetail(item.id)}>{t('Open Details')}</Button><Button variant="outlined" size="small" onClick={() => applyReplayScope({connectorKey: item.connectorKey, flowKey: item.flowKey, businessKey: item.businessKey, documentId: item.documentId, timestamp: item.createdAt, reason: `Replay requested from Integration Hub trace ${item.messageId}`})}>{t('Use for Replay')}</Button></Stack></TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            )}
                        </Section>
                    ) : null}

                    {activeTab === 2 ? (
                        <Grid container spacing={2.2}>
                            <Grid size={{xs: 12, xl: 5}}>
                                <Section title={t('Replay & Recovery')} subtitle={t('Restricted recovery workspace for integration admins. Replay actions are audited and should be used only after confirming impact.')} palette={palette}>
                                    <Box sx={{p: 2.5}}>
                                        {!canReplay ? <Alert severity="info" sx={{mb: 2}}>{t('You can view recovery status here, but replay creation and execution require the Integration Hub replay permission.')}</Alert> : null}
                                        <Stack spacing={1.2}>
                                            <FormControl size="small" fullWidth><InputLabel>{t('Connector')}</InputLabel><Select value={form.connectorKey} label={t('Connector')} onChange={(event) => setForm((current) => ({...current, connectorKey: event.target.value}))}>{connectors.map((item) => <MenuItem key={item.connectorKey} value={item.connectorKey}>{item.connectorLabel}</MenuItem>)}</Select></FormControl>
                                            <FormControl size="small" fullWidth><InputLabel>{t('Flow')}</InputLabel><Select value={form.flowKey} label={t('Flow')} onChange={(event) => setForm((current) => ({...current, flowKey: event.target.value}))}>{flows.map((item) => <MenuItem key={item.flowKey} value={item.flowKey}>{item.flowLabel}</MenuItem>)}</Select></FormControl>
                                            <TextField size="small" label={t('Business Key / Transaction Id')} value={form.businessKey} onChange={(event) => setForm((current) => ({...current, businessKey: event.target.value}))} />
                                            <TextField size="small" label={t('Document / Reference Id')} value={form.documentId} onChange={(event) => setForm((current) => ({...current, documentId: event.target.value}))} />
                                            <TextField size="small" type="datetime-local" label={t('Requested From')} InputLabelProps={{shrink: true}} value={form.requestedFrom} onChange={(event) => setForm((current) => ({...current, requestedFrom: event.target.value}))} />
                                            <TextField size="small" type="datetime-local" label={t('Requested To')} InputLabelProps={{shrink: true}} value={form.requestedTo} onChange={(event) => setForm((current) => ({...current, requestedTo: event.target.value}))} />
                                            <TextField size="small" label={t('Reason / Impact Note')} value={form.reason} onChange={(event) => setForm((current) => ({...current, reason: event.target.value}))} multiline minRows={2} />
                                            <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}><Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted}}>{t('Safeguards')}</Typography><Typography sx={{mt: 0.55, fontSize: '0.76rem', color: palette.textMuted}}>{t('Review connector, flow, time window, and duplicate risk before submitting or executing a recovery action. Use the narrowest possible scope.')}</Typography></Paper>
                                            <Stack direction="row" spacing={1}><Button variant="contained" onClick={openReplayRequestDialog} disabled={!canReplay || saving || !form.connectorKey || !form.flowKey}>{t('Review Replay Request')}</Button><Button variant="outlined" onClick={loadData} disabled={loading || saving}>{t('Refresh')}</Button></Stack>
                                        </Stack>
                                    </Box>
                                </Section>
                            </Grid>

                            <Grid size={{xs: 12, xl: 7}}>
                                <Stack spacing={2.2}>
                                    <Section title={t('Replay Requests')} subtitle={t('Recovery history, request status, and execution follow-up for replay actions.')} palette={palette}>
                                        {!replayRequests.length ? (
                                            <EmptyState title={t('No replay requests are waiting for action')} description={t('Replay requests will appear here once a support analyst raises a recovery request from trace, error queue, or the replay form.')} palette={palette} />
                                        ) : (
                                            <TableContainer>
                                                <Table size="small">
                                                    <TableHead><TableRow><TableCell>{t('Requested')}</TableCell><TableCell>{t('Replay Scope')}</TableCell><TableCell>{t('Status')}</TableCell><TableCell>{t('Actions')}</TableCell></TableRow></TableHead>
                                                    <TableBody>
                                                        {replayRequests.map((item) => (
                                                            <TableRow key={item.id} hover>
                                                                <TableCell><Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{formatDateTimeValue(item.requestedAt)}</Typography><Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{item.requestedBy}</Typography></TableCell>
                                                                <TableCell sx={{minWidth: 260}}><Typography sx={{fontSize: '0.8rem', color: palette.text}}>{formatReplayWindow(item)}</Typography><Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{(connectorLookup.get(item.connectorKey)?.connectorLabel || item.connectorKey) + ' • ' + (flowLookup.get(item.flowKey)?.flowLabel || item.flowKey)}</Typography><RuntimeScopeChips item={item} palette={palette} t={t} /><SharedImpactCaption item={item} palette={palette} t={t} /><Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.reason || '-'}</Typography>{item.resolutionMessage ? <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.resolutionMessage}</Typography> : null}</TableCell>
                                                                <TableCell><Chip size="small" label={item.replayStatus} sx={{fontWeight: 700, ...statusChip(item.replayStatus, palette)}} /></TableCell>
                                                                <TableCell sx={{minWidth: 180}}>{canReplay && executableReplayConnectors.has(item.connectorKey) && ['REQUESTED', 'FAILED'].includes((item.replayStatus || '').toUpperCase()) ? <Button variant="outlined" size="small" onClick={() => openReplayExecutionDialog(item)} disabled={executingReplayId === item.id}>{executingReplayId === item.id ? t('Running...') : t('Review Execute')}</Button> : <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{item.processedBy ? `${t('Processed by')} ${item.processedBy}` : t('Waiting for execution')}</Typography>}</TableCell>
                                                            </TableRow>
                                                        ))}
                                                    </TableBody>
                                                </Table>
                                            </TableContainer>
                                        )}
                                    </Section>

                                    <Section title={t('Open Integration Errors')} subtitle={t('Technical error queue for integration support teams. Use trace and replay actions from here to recover or close incidents.')} palette={palette}>
                                        {!openErrors.length ? (
                                            <EmptyState title={t('No active integration errors')} description={t('All monitored connectors are currently clear. When issues occur, error details, trace jumps, and replay shortcuts will appear here.')} palette={palette} />
                                        ) : (
                                            <TableContainer>
                                                <Table size="small">
                                                    <TableHead><TableRow><TableCell>{t('Connector')}</TableCell><TableCell>{t('Business Area')}</TableCell><TableCell>{t('Error')}</TableCell><TableCell>{t('Actions')}</TableCell></TableRow></TableHead>
                                                    <TableBody>
                                                        {openErrors.map((item) => (
                                                            <TableRow key={item.id} hover>
                                                    <TableCell><Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{connectorLookup.get(item.connectorKey)?.connectorLabel || item.connectorKey}</Typography><Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{formatDateTimeValue(item.createdAt)}</Typography><RuntimeScopeChips item={item} palette={palette} t={t} /><SharedImpactCaption item={item} palette={palette} t={t} /></TableCell>
                                                                <TableCell><Typography sx={{fontSize: '0.8rem', color: palette.text}}>{item.businessKey || item.documentId || '-'}</Typography><Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.flowKey || '-'}</Typography></TableCell>
                                                                <TableCell sx={{minWidth: 240}}><Chip size="small" label={item.errorType} sx={{fontWeight: 700, ...statusChip(item.errorStatus, palette)}} /><Typography sx={{mt: 0.45, fontSize: '0.75rem', color: palette.textMuted}}>{item.errorMessage}</Typography></TableCell>
                                                                <TableCell sx={{minWidth: 240}}><Stack direction="row" spacing={1}>{item.integrationMessageId ? <Button variant="outlined" size="small" onClick={() => openMessageDetail(item.integrationMessageId)}>{t('Open Trace')}</Button> : null}<Button variant="outlined" size="small" onClick={() => applyReplayScope({connectorKey: item.connectorKey, flowKey: item.flowKey, businessKey: item.businessKey, documentId: item.documentId, timestamp: item.createdAt, reason: `Replay requested from Integration Hub error queue for ${item.businessKey || item.documentId || item.connectorKey}`})}>{t('Use for Replay')}</Button><Button variant="outlined" size="small" onClick={() => resolveError(item)} disabled={resolvingErrorId === item.id}>{resolvingErrorId === item.id ? t('Resolving...') : t('Resolve')}</Button></Stack></TableCell>
                                                            </TableRow>
                                                        ))}
                                                    </TableBody>
                                                </Table>
                                            </TableContainer>
                                        )}
                                    </Section>
                                </Stack>
                            </Grid>
                        </Grid>
                    ) : null}
                </>
            )}

            <Drawer anchor="right" open={Boolean(selectedRun)} onClose={() => setSelectedRun(null)} PaperProps={{sx: {top: `${SHELL_HEADER_HEIGHT}px`, height: `calc(100% - ${SHELL_HEADER_HEIGHT}px)`, width: {xs: '100%', sm: 460}, backgroundColor: palette.cardBg}}}>
                {selectedRun ? (
                    <Box sx={{p: 2.5}}>
                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Run Details')}</Typography>
                        <Typography sx={{mt: 0.45, fontSize: '0.78rem', color: palette.textMuted}}>{connectorLookup.get(selectedRun.connectorKey)?.connectorLabel || selectedRun.connectorKey}</Typography>
                        <Divider sx={{my: 2}} />
                        <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                            <MetaRow label={t('Flow')} value={flowLookup.get(selectedRun.flowKey)?.flowLabel || selectedRun.flowKey} palette={palette} />
                            <MetaRow label={t('Endpoint Mode')} value={selectedRun.endpointMode || '-'} palette={palette} />
                            <MetaRow label={t('Affected Lanes')} value={(selectedRun.affectedReconLabels || []).join(', ') || selectedReconLabel || '-'} palette={palette} />
                            <MetaRow label={t('Status')} value={selectedRun.runStatus} palette={palette} />
                            <MetaRow label={t('Triggered by')} value={selectedRun.triggerType || '-'} palette={palette} />
                            <MetaRow label={t('Started')} value={formatDateTimeValue(selectedRun.startedAt)} palette={palette} />
                            <MetaRow label={t('Completed')} value={formatDateTimeValue(selectedRun.completedAt)} palette={palette} />
                            <MetaRow label={t('Duration')} value={formatDuration(selectedRun.startedAt, selectedRun.completedAt)} palette={palette} />
                            <MetaRow label={t('Source Records')} value={String(selectedRun.sourceRecordCount || 0)} palette={palette} />
                            <MetaRow label={t('Published Records')} value={String(selectedRun.publishedRecordCount || 0)} palette={palette} />
                            <MetaRow label={t('Errors')} value={String(selectedRun.errorCount || 0)} palette={palette} />
                        </Paper>
                        <Paper elevation={0} sx={{mt: 1.5, p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                            <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted}}>{t('Result Summary')}</Typography>
                            <Typography sx={{mt: 0.6, fontSize: '0.78rem', color: palette.textMuted}}>{selectedRun.runSummary || t('No additional run summary was captured for this execution.')}</Typography>
                        </Paper>
                    </Box>
                ) : null}
            </Drawer>

            <Drawer anchor="right" open={Boolean(selectedMessageDetail)} onClose={() => setSelectedMessageDetail(null)} PaperProps={{sx: {top: `${SHELL_HEADER_HEIGHT}px`, height: `calc(100% - ${SHELL_HEADER_HEIGHT}px)`, width: {xs: '100%', lg: 620}, backgroundColor: palette.cardBg}}}>
                {selectedMessageDetail ? (
                    <Box sx={{p: 2.5}}>
                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Trace Details')}</Typography>
                        <Typography sx={{mt: 0.45, fontSize: '0.78rem', color: palette.textMuted}}>{selectedMessageDetail.businessKey || selectedMessageDetail.documentId || selectedMessageDetail.messageId}</Typography>
                        <Divider sx={{my: 2}} />
                        {messageDetailLoading ? <Box sx={{display: 'flex', justifyContent: 'center', py: 6}}><CircularProgress size={28} /></Box> : (
                            <Grid container spacing={1.4}>
                                <Grid size={{xs: 12, lg: 7}}>
                                    <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                        <MetaRow label={t('Connector')} value={connectorLookup.get(selectedMessageDetail.connectorKey)?.connectorLabel || selectedMessageDetail.connectorKey} palette={palette} />
                                        <MetaRow label={t('Flow')} value={flowLookup.get(selectedMessageDetail.flowKey)?.flowLabel || selectedMessageDetail.flowKey} palette={palette} />
                                        <MetaRow label={t('Endpoint Mode')} value={connectorLookup.get(selectedMessageDetail.connectorKey)?.endpointMode || '-'} palette={palette} />
                                        <MetaRow label={t('Affected Lanes')} value={(connectorLookup.get(selectedMessageDetail.connectorKey)?.affectedReconLabels || []).join(', ') || selectedReconLabel || '-'} palette={palette} />
                                        <MetaRow label={t('Correlation Id')} value={selectedMessageDetail.traceId} palette={palette} />
                                        <MetaRow label={t('Status')} value={selectedMessageDetail.messageStatus} palette={palette} />
                                        <MetaRow label={t('Captured')} value={formatDateTimeValue(selectedMessageDetail.createdAt)} palette={palette} />
                                        <MetaRow label={t('Updated')} value={formatDateTimeValue(selectedMessageDetail.updatedAt)} palette={palette} />
                                        <MetaRow label={t('Payload Ref')} value={selectedMessageDetail.payloadRef || '-'} palette={palette} />
                                        <MetaRow label={t('Retry Count')} value={String(selectedMessageDetail.retryCount ?? 0)} palette={palette} />
                                    </Paper>
                                    <Paper elevation={0} sx={{mt: 1.4, p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                        <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted, mb: 0.8}}>{t('Payload Snapshot')}</Typography>
                                        <Box component="pre" sx={{m: 0, maxHeight: 260, overflow: 'auto', fontSize: '0.74rem', lineHeight: 1.45, color: palette.text, whiteSpace: 'pre-wrap', wordBreak: 'break-word'}}>{selectedMessageDetail.payloadSnapshotJson || t('No payload snapshot was captured for this message.')}</Box>
                                    </Paper>
                                </Grid>
                                <Grid size={{xs: 12, lg: 5}}>
                                    <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                        <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted, mb: 0.8}}>{t('Related Errors')}</Typography>
                                        <Stack spacing={1}>
                                            {selectedMessageDetail.relatedErrors?.length ? selectedMessageDetail.relatedErrors.map((item) => (
                                                <Box key={item.id} sx={{p: 1.1, borderRadius: '14px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                    <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center"><Chip size="small" label={item.errorStatus} sx={{fontWeight: 700, ...statusChip(item.errorStatus, palette)}} />{item.errorStatus === 'OPEN' ? <Button variant="outlined" size="small" onClick={() => resolveError(item)} disabled={resolvingErrorId === item.id}>{resolvingErrorId === item.id ? t('Resolving...') : t('Resolve')}</Button> : null}</Stack>
                                                    <Typography sx={{mt: 0.7, fontSize: '0.78rem', color: palette.text}}>{item.errorType}</Typography>
                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.errorMessage}</Typography>
                                                </Box>
                                            )) : <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>{t('No related errors were found for this message.')}</Typography>}
                                        </Stack>
                                    </Paper>
                                    <Paper elevation={0} sx={{mt: 1.4, p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                        <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted, mb: 0.8}}>{t('Related Replay Requests')}</Typography>
                                        <Stack spacing={1}>
                                            {selectedMessageDetail.relatedReplayRequests?.length ? selectedMessageDetail.relatedReplayRequests.map((item) => (
                                                <Box key={item.id} sx={{p: 1.1, borderRadius: '14px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                    <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center"><Chip size="small" label={item.replayStatus} sx={{fontWeight: 700, ...statusChip(item.replayStatus, palette)}} />{canReplay && executableReplayConnectors.has(item.connectorKey) && ['REQUESTED', 'FAILED'].includes((item.replayStatus || '').toUpperCase()) ? <Button variant="outlined" size="small" onClick={() => openReplayExecutionDialog(item)} disabled={executingReplayId === item.id}>{executingReplayId === item.id ? t('Running...') : t('Review Execute')}</Button> : null}</Stack>
                                                    <Typography sx={{mt: 0.7, fontSize: '0.78rem', color: palette.text}}>{formatReplayWindow(item)}</Typography>
                                                    <Typography sx={{fontSize: '0.72rem', color: palette.textMuted}}>{item.reason || '-'}</Typography>
                                                </Box>
                                            )) : <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>{t('No replay requests have been raised for this trace yet.')}</Typography>}
                                        </Stack>
                                    </Paper>
                                    <Button sx={{mt: 1.4}} variant="contained" onClick={() => applyReplayScope({connectorKey: selectedMessageDetail.connectorKey, flowKey: selectedMessageDetail.flowKey, businessKey: selectedMessageDetail.businessKey, documentId: selectedMessageDetail.documentId, timestamp: selectedMessageDetail.createdAt, reason: `Replay requested from Integration Hub trace ${selectedMessageDetail.messageId}`})}>{t('Use This Trace for Replay')}</Button>
                                </Grid>
                            </Grid>
                        )}
                    </Box>
                ) : null}
            </Drawer>

            <Dialog
                open={replayDialog.open}
                onClose={closeReplayDialog}
                maxWidth="sm"
                fullWidth
                sx={{
                    '& .MuiDialog-container': {
                        alignItems: 'flex-start',
                        pt: `calc(${SHELL_HEADER_HEIGHT}px + 16px)`,
                        pb: 2,
                    },
                    '& .MuiPaper-root': {
                        mt: 0,
                        maxHeight: `calc(100% - ${SHELL_HEADER_HEIGHT + 32}px)`,
                    },
                }}
            >
                <DialogTitle>{replayDialog.mode === 'execute' ? t('Review Replay Execution') : t('Review Replay Request')}</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={1.4}>
                        <Alert severity="warning">{replayDialog.mode === 'execute' ? t('Replay execution will instruct the connector runtime to republish the selected window or business scope. Confirm the impact before continuing.') : t('Replay requests should only be raised after confirming connector, flow, time window, and duplicate risk. All actions are captured in the audit trail.')}</Alert>
                        <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                            <MetaRow label={t('Connector')} value={replayConnector?.connectorLabel || replayDialog.payload?.connectorKey} palette={palette} />
                            <MetaRow label={t('Flow')} value={replayFlow?.flowLabel || replayDialog.payload?.flowKey} palette={palette} />
                            <MetaRow label={t('Endpoint Mode')} value={replayConnector?.endpointMode || replayDialog.payload?.endpointMode || '-'} palette={palette} />
                            <MetaRow label={t('Affected Lanes')} value={(replayConnector?.affectedReconLabels || replayDialog.payload?.affectedReconLabels || []).join(', ') || selectedReconLabel || '-'} palette={palette} />
                            <MetaRow label={t('Scope')} value={formatReplayWindow(replayDialog.payload || {})} palette={palette} />
                            <MetaRow label={t('Business Key')} value={replayDialog.payload?.businessKey || '-'} palette={palette} />
                            <MetaRow label={t('Document Id')} value={replayDialog.payload?.documentId || '-'} palette={palette} />
                            <MetaRow label={t('Requested From')} value={formatDateTimeValue(replayDialog.payload?.requestedFrom)} palette={palette} />
                            <MetaRow label={t('Requested To')} value={formatDateTimeValue(replayDialog.payload?.requestedTo)} palette={palette} />
                            <MetaRow label={t('Reason')} value={replayDialog.payload?.reason || '-'} palette={palette} />
                        </Paper>
                        {replayConnector?.sharedAsset && (replayConnector.affectedReconLabels || []).length > 1 ? (
                            <Alert severity="warning">
                                {t('Shared runtime impact')}: {(replayConnector.affectedReconLabels || []).join(', ')}
                            </Alert>
                        ) : null}
                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{t('Estimated message count is determined by the connector runtime at execution time. Use a narrow business key or date window whenever possible, and confirm downstream teams are ready for republished traffic.')}</Typography>
                    </Stack>
                </DialogContent>
                <DialogActions sx={{px: 3, py: 2}}>
                    <Button variant="outlined" onClick={closeReplayDialog} disabled={saving || Boolean(executingReplayId)}>{t('Cancel')}</Button>
                    <Button variant="contained" onClick={confirmReplayAction} disabled={replayDialog.mode === 'request' ? saving : Boolean(executingReplayId)}>
                        {replayDialog.mode === 'execute' ? (executingReplayId ? t('Executing...') : t('Execute Replay')) : (saving ? t('Submitting...') : t('Submit Replay Request'))}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    )
}
