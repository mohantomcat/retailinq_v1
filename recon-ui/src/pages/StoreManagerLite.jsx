import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    FormControl,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import StorefrontIcon from '@mui/icons-material/Storefront'
import AssignmentTurnedInIcon from '@mui/icons-material/AssignmentTurnedIn'
import KnownIssueGuidanceCard from '../components/KnownIssueGuidanceCard'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {RECON_VIEW_OPTIONS_WITH_ALL} from '../constants/reconViews'

const MODULE_OPTIONS = RECON_VIEW_OPTIONS_WITH_ALL

function formatCurrencyValue(amount, currencyCode) {
    if (amount === null || amount === undefined || amount === '') return '-'
    const numeric = Number(amount)
    if (!Number.isFinite(numeric)) return `${currencyCode ? `${currencyCode} ` : ''}${amount}`
    if (!currencyCode) return numeric.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})
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

function formatDateValue(value) {
    if (!value) return '-'
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value.replace('T', ' ') : parsed.toLocaleString()
}

function chipStyles(band, palette) {
    switch ((band || '').toUpperCase()) {
        case 'CRITICAL':
        case 'ACTION_OVERDUE':
        case 'UNOWNED':
        case 'OWNERSHIP_GAP':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'HIGH':
        case 'NO_NEXT_ACTION':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        case 'ACTION_DUE_SOON':
        case 'MEDIUM':
            return {backgroundColor: palette.blueChipBg, color: palette.blueChipText}
        default:
            return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
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

function SummaryCard({label, value, palette, tone}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.7, fontSize: '1.45rem', fontWeight: 800, color: tone}}>{value}</Typography>
        </Paper>
    )
}

function createEmptyIncidentAction() {
    return {
        incidentKey: '',
        mode: '',
        reconView: '',
        storeId: '',
        incidentTitle: '',
        ticketChannelId: '',
        ticketSummary: '',
        ticketDescription: '',
        communicationChannelId: '',
        communicationSubject: '',
        communicationMessage: '',
    }
}

function filterChannelsForIncident(channels, reconView) {
    return (channels || []).filter((channel) => !channel.reconView || channel.reconView === reconView)
}

export default function StoreManagerLite({palette, t}) {
    const {hasPermission, user, getAccessibleStores} = useAuth()
    const canView = hasPermission('EXCEPTION_QUEUE_VIEW')
    const canEdit = hasPermission('EXCEPTION_EDIT')
    const accessibleStores = getAccessibleStores()
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState('')
    const [feedbackSubmittingKey, setFeedbackSubmittingKey] = useState('')
    const [incidentActionSaving, setIncidentActionSaving] = useState(false)
    const [data, setData] = useState(null)
    const [filters, setFilters] = useState({reconView: '', storeId: '', search: ''})
    const [incidentAction, setIncidentAction] = useState(createEmptyIncidentAction())

    const loadView = async (nextFilters = filters) => {
        try {
            setLoading(true)
            setError('')
            setFeedback('')
            const response = await exceptionApi.getStoreManagerLite(nextFilters)
            setData(response)
            setFilters((current) => {
                if (!current.storeId && response?.storeOptions?.length === 1) {
                    return {...current, storeId: response.storeOptions[0]}
                }
                return current
            })
        } catch (err) {
            setError(err.message || 'Failed to load store manager view')
        } finally {
            setLoading(false)
        }
    }

    const openIncidentAction = (incident, mode) => {
        const ticketChannels = filterChannelsForIncident(data?.ticketChannels, incident?.reconView)
        const communicationChannels = filterChannelsForIncident(data?.communicationChannels, incident?.reconView)
        const defaultTicketChannelId = ticketChannels[0]?.id || ''
        const defaultCommunicationChannelId = communicationChannels[0]?.id || ''
        setIncidentAction({
            incidentKey: incident?.incidentKey || '',
            mode,
            reconView: incident?.reconView || '',
            storeId: incident?.storeId || '',
            incidentTitle: incident?.incidentTitle || '',
            ticketChannelId: defaultTicketChannelId,
            ticketSummary: `${incident?.storeId || t('Store')} | ${incident?.incidentTitle || t('Incident')}`,
            ticketDescription: incident?.incidentSummary || incident?.priorityReason || '',
            communicationChannelId: defaultCommunicationChannelId,
            communicationSubject: `${incident?.storeId || t('Store')} | ${incident?.incidentTitle || t('Incident')}`,
            communicationMessage: incident?.nextAction
                ? `${t('Next Action')}: ${incident.nextAction}`
                : (incident?.incidentSummary || ''),
        })
        setError('')
        setFeedback('')
    }

    const closeIncidentAction = () => {
        setIncidentAction(createEmptyIncidentAction())
    }

    const handleIncidentActionFieldChange = (field, value) => {
        setIncidentAction((current) => ({...current, [field]: value}))
    }

    const handleCreateIncidentTicket = async () => {
        if (!incidentAction.ticketChannelId) {
            setError('Select a ticketing channel before creating an external ticket')
            return
        }
        try {
            setIncidentActionSaving(true)
            setError('')
            setFeedback('')
            const response = await exceptionApi.createIncidentExternalTicket({
                reconView: incidentAction.reconView,
                channelId: incidentAction.ticketChannelId,
                incidentKey: incidentAction.incidentKey,
                incidentTitle: incidentAction.incidentTitle,
                storeId: incidentAction.storeId,
                ticketSummary: incidentAction.ticketSummary.trim(),
                ticketDescription: incidentAction.ticketDescription.trim(),
            })
            await loadView(filters)
            closeIncidentAction()
            setFeedback(
                response?.deliveryStatus === 'FAILED'
                    ? (response?.errorMessage || 'Ticket delivery failed')
                    : (response?.externalReference ? `External ticket created: ${response.externalReference}` : 'External ticket created.')
            )
        } catch (err) {
            setError(err.message || 'Failed to create incident ticket')
        } finally {
            setIncidentActionSaving(false)
        }
    }

    const handleSendIncidentUpdate = async () => {
        if (!incidentAction.communicationChannelId) {
            setError('Select a communication channel before sending an update')
            return
        }
        try {
            setIncidentActionSaving(true)
            setError('')
            setFeedback('')
            const response = await exceptionApi.sendIncidentCommunication({
                reconView: incidentAction.reconView,
                channelId: incidentAction.communicationChannelId,
                incidentKey: incidentAction.incidentKey,
                incidentTitle: incidentAction.incidentTitle,
                storeId: incidentAction.storeId,
                subject: incidentAction.communicationSubject.trim(),
                messageBody: incidentAction.communicationMessage.trim(),
            })
            await loadView(filters)
            closeIncidentAction()
            setFeedback(
                response?.deliveryStatus === 'FAILED'
                    ? (response?.errorMessage || 'Operational update delivery failed')
                    : 'Operational update sent.'
            )
        } catch (err) {
            setError(err.message || 'Failed to send operational update')
        } finally {
            setIncidentActionSaving(false)
        }
    }

    const handleKnownIssueFeedback = async (incident, helpful) => {
        if (!incident?.matchedKnownIssue?.id) {
            return
        }
        const requestKey = `${incident.incidentKey || incident.storeId}:${helpful ? 'HELPFUL' : 'NOT_HELPFUL'}`
        setFeedbackSubmittingKey(requestKey)
        setError('')
        setFeedback('')
        try {
            const response = await exceptionApi.submitKnownIssueFeedback({
                knownIssueId: incident.matchedKnownIssue.id,
                helpful,
                incidentKey: incident.incidentKey,
                storeId: incident.storeId,
                reconView: incident.reconView,
                sourceView: 'STORE_MANAGER_LITE',
            })
            setData((current) => current ? ({
                ...current,
                incidents: (current.incidents || []).map((item) => item?.matchedKnownIssue?.id === response.knownIssueId ? {
                    ...item,
                    matchedKnownIssue: {
                        ...item.matchedKnownIssue,
                        helpfulCount: response.helpfulCount,
                        notHelpfulCount: response.notHelpfulCount,
                    },
                } : item),
            }) : current)
            setFeedback(response.message || 'Known issue feedback saved.')
        } catch (err) {
            setError(err.message || 'Failed to save known issue feedback')
        } finally {
            setFeedbackSubmittingKey('')
        }
    }

    useEffect(() => {
        if (canView) loadView(filters)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canView])

    const storeOptions = useMemo(() => data?.storeOptions || [], [data])
    const actionItems = useMemo(() => data?.actionItems || [], [data])
    const incidents = useMemo(() => data?.incidents || [], [data])

    if (!canView) return null

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 42, height: 42, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.tealChipBg, color: palette.tealChipText, flexShrink: 0}}>
                        <StorefrontIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Store Manager Lite')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 960}}>
                            {t('A simplified store-first view of active incidents, impacted transactions, and the next actions the store team must complete now.')}
                        </Typography>
                        <Typography sx={{mt: 0.55, fontSize: '0.8rem', color: palette.blueChipText}}>
                            {accessibleStores?.length
                                ? `${t('Scoped to your assigned stores')}: ${accessibleStores.join(', ')}`
                                : t('No store restriction is configured on this account, so the view can be filtered across all stores.')}
                        </Typography>
                        {data?.operatingModel ? (
                            <Typography sx={{mt: 0.45, fontSize: '0.8rem', color: palette.textMuted}}>
                                {`${t('Local Time')}: ${data.operatingModel.currentLocalTime || '-'} | ${t('Business Date')}: ${data.operatingModel.currentBusinessDate || '-'} | ${t('Timezone')}: ${data.operatingModel.timezone || '-'}`}
                            </Typography>
                        ) : null}
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}
            {!error && feedback ? <Alert severity="success" sx={{mb: 2}}>{feedback}</Alert> : null}

            <Paper elevation={0} sx={{p: 2.25, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr)) auto'}, gap: 1.5, alignItems: 'end'}}>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Module')}</InputLabel>
                        <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}>
                            {MODULE_OPTIONS.map((option) => <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Store')}</InputLabel>
                        <Select value={filters.storeId} label={t('Store')} onChange={(event) => setFilters((current) => ({...current, storeId: event.target.value}))}>
                            <MenuItem value="">{t('All Accessible Stores')}</MenuItem>
                            {storeOptions.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <TextField size="small" label={t('Search')} value={filters.search} onChange={(event) => setFilters((current) => ({...current, search: event.target.value}))} placeholder={t('Incident, store, owner')} fullWidth/>
                    <Stack direction={{xs: 'column', lg: 'row'}} spacing={1}>
                        <Button variant="contained" onClick={() => loadView(filters)} disabled={loading}>{t('Apply')}</Button>
                        <Button variant="outlined" onClick={() => {
                            const cleared = {reconView: '', storeId: '', search: ''}
                            setFilters(cleared)
                            loadView(cleared)
                        }} disabled={loading}>{t('Clear')}</Button>
                    </Stack>
                </Box>
            </Paper>

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(7, minmax(0, 1fr))'}, gap: 1.5, mb: 3}}>
                        <SummaryCard label={t('Stores In View')} value={data?.summary?.storesInView || 0} tone={palette.text} palette={palette}/>
                        <SummaryCard label={t('Active Incidents')} value={data?.summary?.activeIncidents || 0} tone="#DC2626" palette={palette}/>
                        <SummaryCard label={t('Affected Transactions')} value={data?.summary?.affectedTransactions || 0} tone="#D97706" palette={palette}/>
                        <SummaryCard label={t('Today Impacted')} value={data?.summary?.todayAffectedTransactions || 0} tone={palette.blueChipText} palette={palette}/>
                        <SummaryCard label={t('Open Cases')} value={data?.summary?.openCases || 0} tone={palette.tealChipText} palette={palette}/>
                        <SummaryCard label={t('Action Required')} value={data?.summary?.actionRequiredIncidents || 0} tone="#7C3AED" palette={palette}/>
                        <SummaryCard label={t('Value At Risk')} value={formatCurrencyValue(data?.summary?.businessValue?.valueAtRisk, data?.summary?.businessValue?.currencyCode)} tone="#D97706" palette={palette}/>
                    </Box>

                    <Paper elevation={0} sx={{p: 2.25, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.25, mb: 1.5}}>
                            <AssignmentTurnedInIcon sx={{fontSize: 20, color: palette.blueChipText, mt: 0.2}}/>
                            <Box>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('What The Store Must Do Now')}</Typography>
                                <Typography sx={{mt: 0.35, fontSize: '0.84rem', color: palette.textMuted}}>
                                    {t('These are the highest-priority actions the store team should complete or escalate next.')}
                                </Typography>
                            </Box>
                        </Box>
                        <Stack spacing={1.25}>
                            {actionItems.length ? actionItems.map((item) => (
                                <Paper key={`${item.incidentKey || item.storeId}`} elevation={0} sx={{p: 1.5, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                    <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1.5, flexWrap: 'wrap'}}>
                                        <Box sx={{flex: '1 1 320px'}}>
                                            <Typography sx={{fontSize: '0.88rem', fontWeight: 800, color: palette.text}}>
                                                {item.storeId || '-'} | {item.incidentTitle}
                                            </Typography>
                                            <Typography sx={{mt: 0.45, fontSize: '0.82rem', color: palette.text}}>
                                                {item.actionLabel}
                                            </Typography>
                                            <Typography sx={{mt: 0.35, fontSize: '0.76rem', color: palette.textMuted}}>
                                                {item.actionReason}
                                            </Typography>
                                            <Typography sx={{mt: 0.35, fontSize: '0.76rem', color: palette.textMuted}}>
                                                {t('Owner')}: {item.ownerSummary || '-'} | {t('Due')}: {formatDateValue(item.dueAt)}
                                            </Typography>
                                        </Box>
                                        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                            <Chip size="small" label={ownershipLabel(item.ownershipStatus, t)} sx={{fontWeight: 700, ...chipStyles(item.ownershipStatus, palette)}}/>
                                            <Chip size="small" label={item.impactBand || t('Low')} sx={{fontWeight: 700, ...chipStyles(item.impactBand, palette)}}/>
                                            {item.businessValue?.valueAtRisk ? <Chip size="small" label={formatCurrencyValue(item.businessValue.valueAtRisk, item.businessValue.currencyCode)} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText}}/> : null}
                                        </Stack>
                                    </Box>
                                </Paper>
                            )) : (
                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No immediate store actions are required for the current filters.')}</Typography>
                            )}
                        </Stack>
                    </Paper>

                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                        {incidents.length ? incidents.map((incident) => {
                            const incidentTicketChannels = filterChannelsForIncident(data?.ticketChannels, incident.reconView)
                            const incidentCommunicationChannels = filterChannelsForIncident(data?.communicationChannels, incident.reconView)

                            return (
                            <Paper key={incident.incidentKey} elevation={0} sx={{p: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1.5, flexWrap: 'wrap'}}>
                                    <Box>
                                        <Typography sx={{fontSize: '0.96rem', fontWeight: 800, color: palette.text}}>
                                            {incident.storeId || '-'} | {incident.incidentTitle}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {incident.reconView} | {incident.incidentSummary || '-'}
                                        </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                        {incident.activeToday ? <Chip size="small" label={t('Active Today')} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}/> : null}
                                        <Chip size="small" label={incident.impactBand || t('Low')} sx={{fontWeight: 700, ...chipStyles(incident.impactBand, palette)}}/>
                                        <Chip size="small" label={ownershipLabel(incident.ownershipStatus, t)} sx={{fontWeight: 700, ...chipStyles(incident.ownershipStatus, palette)}}/>
                                    </Stack>
                                </Box>

                                <Box sx={{mt: 1.35, display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1}}>
                                    <Paper elevation={0} sx={{p: 1.1, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                        <Typography sx={{fontSize: '0.72rem', fontWeight: 700, color: palette.textMuted}}>{t('Impacted Transactions')}</Typography>
                                        <Typography sx={{mt: 0.4, fontSize: '1.02rem', fontWeight: 800, color: palette.text}}>{incident.impactedTransactions || 0}</Typography>
                                    </Paper>
                                    <Paper elevation={0} sx={{p: 1.1, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                        <Typography sx={{fontSize: '0.72rem', fontWeight: 700, color: palette.textMuted}}>{t('Open Cases')}</Typography>
                                        <Typography sx={{mt: 0.4, fontSize: '1.02rem', fontWeight: 800, color: palette.text}}>{incident.openCases || 0}</Typography>
                                    </Paper>
                                    <Paper elevation={0} sx={{p: 1.1, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                        <Typography sx={{fontSize: '0.72rem', fontWeight: 700, color: palette.textMuted}}>{t('Owner')}</Typography>
                                        <Typography sx={{mt: 0.4, fontSize: '0.92rem', fontWeight: 700, color: palette.text}}>{incident.ownerSummary || '-'}</Typography>
                                    </Paper>
                                    <Paper elevation={0} sx={{p: 1.1, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                        <Typography sx={{fontSize: '0.72rem', fontWeight: 700, color: palette.textMuted}}>{t('Value At Risk')}</Typography>
                                        <Typography sx={{mt: 0.4, fontSize: '0.92rem', fontWeight: 700, color: palette.text}}>
                                            {formatCurrencyValue(incident.businessValue?.valueAtRisk, incident.businessValue?.currencyCode)}
                                        </Typography>
                                    </Paper>
                                </Box>

                                <Typography sx={{mt: 1.25, fontSize: '0.84rem', color: palette.text}}>
                                    {t('Recommended Action')}: {incident.recommendedAction || t('Review incident')}
                                </Typography>
                                <Typography sx={{mt: 0.45, fontSize: '0.78rem', color: palette.textMuted}}>
                                    {t('Next Action')}: {incident.nextAction || t('Not set')} | {t('Due')}: {formatDateValue(incident.nextActionDueAt)}
                                </Typography>
                                <Typography sx={{mt: 0.35, fontSize: '0.78rem', color: palette.textMuted}}>
                                    {incident.priorityReason || '-'} | {t('Updated')}: {incident.latestUpdatedAt || '-'}
                                </Typography>

                                <Paper elevation={0} sx={{mt: 1.35, p: 1.4, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                    <Stack spacing={1.1}>
                                        <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.25, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                            <Box>
                                                <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                                    {t('Ticketing & Communication')}
                                                </Typography>
                                                <Typography sx={{mt: 0.35, fontSize: '0.78rem', color: palette.textMuted}}>
                                                    {t('Share this incident with support teams without leaving the store manager view.')}
                                                </Typography>
                                            </Box>
                                            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                <Chip size="small" label={`${t('Tickets')}: ${(incident.externalTickets || []).length}`} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}/>
                                                <Chip size="small" label={`${t('Updates')}: ${(incident.communications || []).length}`} sx={{backgroundColor: palette.tealChipBg, color: palette.tealChipText, fontWeight: 700}}/>
                                            </Stack>
                                        </Box>

                                        {canEdit ? (
                                            <Stack direction={{xs: 'column', sm: 'row'}} spacing={1}>
                                                <Button
                                                    variant="outlined"
                                                    onClick={() => openIncidentAction(incident, 'TICKET')}
                                                    disabled={incidentActionSaving || !incidentTicketChannels.length}
                                                    sx={{textTransform: 'none', fontWeight: 700}}
                                                >
                                                    {t('Create Ticket')}
                                                </Button>
                                                <Button
                                                    variant="outlined"
                                                    onClick={() => openIncidentAction(incident, 'COMMUNICATION')}
                                                    disabled={incidentActionSaving || !incidentCommunicationChannels.length}
                                                    sx={{textTransform: 'none', fontWeight: 700}}
                                                >
                                                    {t('Send Update')}
                                                </Button>
                                            </Stack>
                                        ) : null}

                                        {canEdit && !incidentTicketChannels.length && !incidentCommunicationChannels.length ? (
                                            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                {t('No ticketing or communication channels are configured for this module yet.')}
                                            </Typography>
                                        ) : null}

                                        {incidentAction.incidentKey === incident.incidentKey ? (
                                            <Paper elevation={0} sx={{p: 1.4, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                <Stack spacing={1.2}>
                                                    <Typography sx={{fontSize: '0.84rem', color: palette.text, fontWeight: 800}}>
                                                        {incidentAction.mode === 'TICKET' ? t('Create External Ticket') : t('Send Operational Update')}
                                                    </Typography>

                                                    {incidentAction.mode === 'TICKET' ? (
                                                        <>
                                                            <TextField
                                                                select
                                                                size="small"
                                                                label={t('Ticket Channel')}
                                                                value={incidentAction.ticketChannelId}
                                                                onChange={(event) => handleIncidentActionFieldChange('ticketChannelId', event.target.value)}
                                                                disabled={incidentActionSaving}
                                                                fullWidth
                                                            >
                                                                {incidentTicketChannels.map((channel) => (
                                                                    <MenuItem key={channel.id} value={channel.id}>
                                                                        {channel.channelName} | {channel.channelType}
                                                                    </MenuItem>
                                                                ))}
                                                            </TextField>
                                                            <TextField
                                                                size="small"
                                                                label={t('Ticket Summary')}
                                                                value={incidentAction.ticketSummary}
                                                                onChange={(event) => handleIncidentActionFieldChange('ticketSummary', event.target.value)}
                                                                disabled={incidentActionSaving}
                                                                fullWidth
                                                            />
                                                            <TextField
                                                                size="small"
                                                                label={t('Ticket Description')}
                                                                value={incidentAction.ticketDescription}
                                                                onChange={(event) => handleIncidentActionFieldChange('ticketDescription', event.target.value)}
                                                                disabled={incidentActionSaving}
                                                                multiline
                                                                minRows={3}
                                                                fullWidth
                                                            />
                                                            <Stack direction={{xs: 'column', sm: 'row'}} spacing={1}>
                                                                <Button variant="contained" onClick={handleCreateIncidentTicket} disabled={incidentActionSaving} sx={{textTransform: 'none', fontWeight: 700}}>
                                                                    {incidentActionSaving ? t('Creating...') : t('Create Ticket')}
                                                                </Button>
                                                                <Button variant="text" onClick={closeIncidentAction} disabled={incidentActionSaving} sx={{textTransform: 'none', fontWeight: 700}}>
                                                                    {t('Cancel')}
                                                                </Button>
                                                            </Stack>
                                                        </>
                                                    ) : (
                                                        <>
                                                            <TextField
                                                                select
                                                                size="small"
                                                                label={t('Communication Channel')}
                                                                value={incidentAction.communicationChannelId}
                                                                onChange={(event) => handleIncidentActionFieldChange('communicationChannelId', event.target.value)}
                                                                disabled={incidentActionSaving}
                                                                fullWidth
                                                            >
                                                                {incidentCommunicationChannels.map((channel) => (
                                                                    <MenuItem key={channel.id} value={channel.id}>
                                                                        {channel.channelName} | {channel.channelType}
                                                                    </MenuItem>
                                                                ))}
                                                            </TextField>
                                                            <TextField
                                                                size="small"
                                                                label={t('Subject')}
                                                                value={incidentAction.communicationSubject}
                                                                onChange={(event) => handleIncidentActionFieldChange('communicationSubject', event.target.value)}
                                                                disabled={incidentActionSaving}
                                                                fullWidth
                                                            />
                                                            <TextField
                                                                size="small"
                                                                label={t('Message')}
                                                                value={incidentAction.communicationMessage}
                                                                onChange={(event) => handleIncidentActionFieldChange('communicationMessage', event.target.value)}
                                                                disabled={incidentActionSaving}
                                                                multiline
                                                                minRows={3}
                                                                fullWidth
                                                            />
                                                            <Stack direction={{xs: 'column', sm: 'row'}} spacing={1}>
                                                                <Button variant="contained" onClick={handleSendIncidentUpdate} disabled={incidentActionSaving} sx={{textTransform: 'none', fontWeight: 700}}>
                                                                    {incidentActionSaving ? t('Sending...') : t('Send Update')}
                                                                </Button>
                                                                <Button variant="text" onClick={closeIncidentAction} disabled={incidentActionSaving} sx={{textTransform: 'none', fontWeight: 700}}>
                                                                    {t('Cancel')}
                                                                </Button>
                                                            </Stack>
                                                        </>
                                                    )}
                                                </Stack>
                                            </Paper>
                                        ) : null}

                                        <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1}}>
                                            <Paper elevation={0} sx={{p: 1.15, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted, fontWeight: 700}}>{t('Recent Tickets')}</Typography>
                                                <Stack spacing={0.9} sx={{mt: 0.9}}>
                                                    {(incident.externalTickets || []).slice(0, 2).length ? (incident.externalTickets || []).slice(0, 2).map((ticket) => (
                                                        <Box key={ticket.id}>
                                                            <Typography sx={{fontSize: '0.8rem', color: palette.text, fontWeight: 700}}>
                                                                {ticket.ticketSummary || t('Ticket created')}
                                                            </Typography>
                                                            <Typography sx={{mt: 0.25, fontSize: '0.72rem', color: palette.textMuted}}>
                                                                {ticket.externalReference || ticket.channelName} | {formatDateValue(ticket.createdAt)}
                                                            </Typography>
                                                            {ticket.externalStatus ? (
                                                                <Typography sx={{mt: 0.2, fontSize: '0.72rem', color: palette.textMuted}}>
                                                                    {t('Status')}: {ticket.externalStatus}
                                                                </Typography>
                                                            ) : null}
                                                        </Box>
                                                    )) : (
                                                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{t('No tickets yet.')}</Typography>
                                                    )}
                                                </Stack>
                                            </Paper>
                                            <Paper elevation={0} sx={{p: 1.15, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted, fontWeight: 700}}>{t('Recent Updates')}</Typography>
                                                <Stack spacing={0.9} sx={{mt: 0.9}}>
                                                    {(incident.communications || []).slice(0, 2).length ? (incident.communications || []).slice(0, 2).map((entry) => (
                                                        <Box key={entry.id}>
                                                            <Typography sx={{fontSize: '0.8rem', color: palette.text, fontWeight: 700}}>
                                                                {entry.subject || t('Operational update')}
                                                            </Typography>
                                                            <Typography sx={{mt: 0.25, fontSize: '0.72rem', color: palette.textMuted}}>
                                                                {entry.recipient || entry.channelName} | {formatDateValue(entry.createdAt)}
                                                            </Typography>
                                                        </Box>
                                                    )) : (
                                                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{t('No updates yet.')}</Typography>
                                                    )}
                                                </Stack>
                                            </Paper>
                                        </Box>
                                    </Stack>
                                </Paper>

                                <Box sx={{mt: 1.25}}>
                                    <KnownIssueGuidanceCard
                                        match={incident.matchedKnownIssue}
                                        palette={palette}
                                        t={t}
                                        title={t('Guided Resolution')}
                                        subtitle={t('Suggested guidance for store teams based on a matching known issue.')}
                                        onFeedback={(helpful) => handleKnownIssueFeedback(incident, helpful)}
                                        feedbackSubmitting={feedbackSubmittingKey === `${incident.incidentKey || incident.storeId}:HELPFUL`
                                            || feedbackSubmittingKey === `${incident.incidentKey || incident.storeId}:NOT_HELPFUL`}
                                    />
                                </Box>
                            </Paper>
                            )
                        }) : (
                            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No active store incidents were found for the current filters.')}</Typography>
                            </Paper>
                        )}
                    </Box>
                </>
            )}
        </Box>
    )
}
