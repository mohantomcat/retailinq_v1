import {useEffect, useMemo, useState} from 'react'
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
    Paper,
    Select,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {useReconModules} from '../hooks/useReconModules'

const CHANNEL_GROUP_OPTIONS = [
    {value: 'TICKETING', label: 'Ticketing'},
    {value: 'COMMUNICATION', label: 'Communication'},
    {value: 'BOTH', label: 'Both'},
]

const CHANNEL_TYPE_OPTIONS = [
    {value: 'SERVICENOW', label: 'ServiceNow', groups: ['TICKETING']},
    {value: 'JIRA', label: 'Jira', groups: ['TICKETING']},
    {value: 'GENERIC_WEBHOOK', label: 'Generic Webhook', groups: ['TICKETING', 'COMMUNICATION', 'BOTH']},
    {value: 'EMAIL', label: 'Email', groups: ['COMMUNICATION']},
    {value: 'MICROSOFT_TEAMS', label: 'Microsoft Teams', groups: ['COMMUNICATION']},
]

function SummaryCard({label, value, tone, palette}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.65, fontSize: '1.55rem', fontWeight: 800, color: tone}}>{value}</Typography>
        </Paper>
    )
}

function formatDateTimeValue(value) {
    if (!value) {
        return '-'
    }
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value.replace('T', ' ') : parsed.toLocaleString()
}

function channelToForm(channel) {
    return {
        channelName: channel?.channelName || '',
        channelType: channel?.channelType || 'SERVICENOW',
        channelGroup: channel?.channelGroup || 'TICKETING',
        reconView: channel?.reconView || '',
        endpointUrl: channel?.endpointUrl || '',
        recipientEmail: channel?.recipientEmail || '',
        headersJson: channel?.headersJson || '',
        defaultProjectKey: channel?.defaultProjectKey || '',
        defaultIssueType: channel?.defaultIssueType || '',
        description: channel?.description || '',
        active: channel?.active ?? true,
        inboundSyncEnabled: channel?.inboundSyncEnabled ?? false,
        inboundSharedSecret: '',
        autoCreateOnCaseOpen: channel?.autoCreateOnCaseOpen ?? false,
        autoCreateOnEscalation: channel?.autoCreateOnEscalation ?? false,
    }
}

function getSupportedTypes(channelGroup) {
    return CHANNEL_TYPE_OPTIONS.filter((option) => option.groups.includes(channelGroup || 'TICKETING'))
}

function formatChannelSubtitle(channel, t) {
    const target = channel.channelType === 'EMAIL'
        ? channel.recipientEmail
        : channel.endpointUrl
    return [
        channel.reconView || t('All Modules'),
        target || '-',
    ].join(' | ')
}

export default function TicketingCommunications({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptionsWithAll: MODULE_OPTIONS} = useReconModules()
    const canView = hasPermission('EXCEPTION_QUEUE_VIEW')
    const canEdit = hasPermission('EXCEPTION_EDIT')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState('')
    const [selectedChannelId, setSelectedChannelId] = useState('')
    const [filters, setFilters] = useState({reconView: ''})
    const [data, setData] = useState(null)
    const [form, setForm] = useState(channelToForm(null))

    const loadCenter = async (nextFilters = filters, preferredChannelId = selectedChannelId) => {
        try {
            setLoading(true)
            setError('')
            const response = await exceptionApi.getTicketingCenter({reconView: nextFilters.reconView})
            setData(response)
            const channels = response?.channels || []
            const selected = channels.find((channel) => channel.id === preferredChannelId) || null
            if (selected) {
                setSelectedChannelId(selected.id)
                setForm(channelToForm(selected))
            } else if (channels[0]) {
                setSelectedChannelId(channels[0].id)
                setForm(channelToForm(channels[0]))
            } else {
                setSelectedChannelId('')
                setForm(channelToForm(null))
            }
        } catch (err) {
            setError(err.message || 'Failed to load ticketing and communication center')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (canView) {
            loadCenter(filters)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canView])

    const channels = useMemo(() => data?.channels || [], [data])
    const recentTickets = useMemo(() => data?.recentTickets || [], [data])
    const recentCommunications = useMemo(() => data?.recentCommunications || [], [data])
    const supportedTypes = useMemo(() => getSupportedTypes(form.channelGroup), [form.channelGroup])

    if (!canView) {
        return null
    }

    const handleSelectChannel = (channel) => {
        setSelectedChannelId(channel.id)
        setForm(channelToForm(channel))
        setFeedback('')
    }

    const handleNewChannel = () => {
        setSelectedChannelId('')
        setForm(channelToForm(null))
        setFeedback('')
    }

    const handleFormChange = (field, value) => {
        if (field === 'channelGroup') {
            const supported = getSupportedTypes(value)
            setForm((current) => ({
                ...current,
                channelGroup: value,
                channelType: supported.some((option) => option.value === current.channelType)
                    ? current.channelType
                    : supported[0]?.value || '',
            }))
            return
        }
        setForm((current) => ({...current, [field]: value}))
    }

    const handleSave = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                channelName: form.channelName,
                channelType: form.channelType,
                channelGroup: form.channelGroup,
                reconView: form.reconView,
                endpointUrl: form.endpointUrl,
                recipientEmail: form.recipientEmail,
                headersJson: form.headersJson,
                defaultProjectKey: form.defaultProjectKey,
                defaultIssueType: form.defaultIssueType,
                description: form.description,
                active: form.active === true || form.active === 'true',
                inboundSyncEnabled: form.inboundSyncEnabled === true || form.inboundSyncEnabled === 'true',
                inboundSharedSecret: form.inboundSharedSecret,
                autoCreateOnCaseOpen: form.autoCreateOnCaseOpen === true || form.autoCreateOnCaseOpen === 'true',
                autoCreateOnEscalation: form.autoCreateOnEscalation === true || form.autoCreateOnEscalation === 'true',
            }
            const saved = selectedChannelId
                ? await exceptionApi.updateIntegrationChannel(selectedChannelId, payload)
                : await exceptionApi.createIntegrationChannel(payload)
            setSelectedChannelId(saved.id)
            setForm(channelToForm(saved))
            await loadCenter(filters, saved.id)
            setFeedback(selectedChannelId ? 'Integration channel updated.' : 'Integration channel created.')
        } catch (err) {
            setError(err.message || 'Failed to save integration channel')
        } finally {
            setSaving(false)
        }
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.blueChipBg, color: palette.blueChipText, flexShrink: 0}}>
                        <HubOutlinedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Ticketing & Communications')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Configure the retailer-facing channels used to create external tickets and send operational updates from exception cases and store incidents. Keep the setup lightweight and aligned to the teams that actually respond.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}
            {!error && feedback ? <Alert severity="success" sx={{mb: 2}}>{feedback}</Alert> : null}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid size={{xs: 12, md: 3}}><SummaryCard label={t('Channels')} value={data?.summary?.channelCount || 0} tone={palette.text} palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 3}}><SummaryCard label={t('Active Channels')} value={data?.summary?.activeChannelCount || 0} tone={palette.tealChipText} palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 3}}><SummaryCard label={t('Recent Tickets')} value={data?.summary?.recentTicketCount || 0} tone={palette.blueChipText} palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 3}}><SummaryCard label={t('Failed Deliveries')} value={data?.summary?.failedDeliveries || 0} tone="#DC2626" palette={palette}/></Grid>
                    </Grid>

                    <Paper elevation={0} sx={{p: 2.25, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'minmax(0, 1fr) auto'}, gap: 1.5, alignItems: 'end'}}>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Module')}</InputLabel>
                                <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}>
                                    {MODULE_OPTIONS.map((option) => <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <Stack direction={{xs: 'column', lg: 'row'}} spacing={1}>
                                <Button variant="contained" onClick={() => loadCenter(filters, selectedChannelId)}>{t('Apply')}</Button>
                                <Button variant="outlined" onClick={() => {
                                    const cleared = {reconView: ''}
                                    setFilters(cleared)
                                    loadCenter(cleared, selectedChannelId)
                                }}>{t('Clear')}</Button>
                            </Stack>
                        </Box>
                    </Paper>

                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid size={{xs: 12, lg: 5}}>
                            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'center', mb: 1.5}}>
                                    <Box>
                                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Configured Channels')}</Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('Select a channel to review or update its connection details.')}
                                        </Typography>
                                    </Box>
                                    {canEdit ? <Button variant="outlined" onClick={handleNewChannel}>{t('New Channel')}</Button> : null}
                                </Box>
                                <Stack spacing={1.2}>
                                    {channels.length ? channels.map((channel) => (
                                        <Paper
                                            key={channel.id}
                                            elevation={0}
                                            onClick={() => handleSelectChannel(channel)}
                                            sx={{
                                                p: 1.5,
                                                borderRadius: 3,
                                                border: `1px solid ${selectedChannelId === channel.id ? palette.blueChipText : palette.border}`,
                                                backgroundColor: selectedChannelId === channel.id ? palette.selectedBg : palette.cardBg,
                                                cursor: 'pointer',
                                            }}
                                        >
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 260px'}}>
                                                    <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>{channel.channelName}</Typography>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {formatChannelSubtitle(channel, t)}
                                                    </Typography>
                                                </Box>
                                                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                    <Chip size="small" label={channel.channelGroup} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}/>
                                                    <Chip size="small" label={channel.channelType} sx={{backgroundColor: palette.tealChipBg, color: palette.tealChipText, fontWeight: 700}}/>
                                                    {channel.inboundSyncEnabled ? <Chip size="small" label={t('Bidirectional')} sx={{backgroundColor: palette.cardBg, color: palette.text, fontWeight: 700, border: `1px solid ${palette.border}`}}/> : null}
                                                    {channel.autoCreateOnCaseOpen || channel.autoCreateOnEscalation ? <Chip size="small" label={t('Auto Push')} sx={{backgroundColor: palette.cardBg, color: palette.text, fontWeight: 700, border: `1px solid ${palette.border}`}}/> : null}
                                                    <Chip size="small" label={channel.active ? t('Active') : t('Inactive')} sx={{backgroundColor: channel.active ? palette.cardBg : '#FEF2F2', color: channel.active ? palette.textMuted : '#B91C1C', fontWeight: 700, border: `1px solid ${palette.border}`}}/>
                                                </Stack>
                                            </Box>
                                            {channel.description ? (
                                                <Typography sx={{mt: 0.8, fontSize: '0.82rem', color: palette.text}}>
                                                    {channel.description}
                                                </Typography>
                                            ) : null}
                                            {channel.inboundSyncEnabled ? (
                                                <Typography sx={{mt: 0.4, fontSize: '0.74rem', color: palette.blueChipText}}>
                                                    {t('Callback')}: {channel.callbackUrl || '-'}
                                                </Typography>
                                            ) : null}
                                            <Typography sx={{mt: 0.45, fontSize: '0.74rem', color: palette.textMuted}}>
                                                {t('Updated')}: {formatDateTimeValue(channel.updatedAt)}
                                            </Typography>
                                        </Paper>
                                    )) : (
                                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No channels are configured for the current filters.')}</Typography>
                                    )}
                                </Stack>
                            </Paper>
                        </Grid>

                        <Grid size={{xs: 12, lg: 7}}>
                            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start', flexWrap: 'wrap', mb: 2}}>
                                    <Box>
                                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                            {selectedChannelId ? t('Edit Channel') : t('Create Channel')}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('Set only the channel details your retailer support teams actually need. Avoid over-configuring niche paths until customers ask for them.')}
                                        </Typography>
                                    </Box>
                                    {selectedChannelId ? (
                                        <Chip size="small" label={t('Editing existing channel')} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}/>
                                    ) : null}
                                </Box>

                                {!canEdit ? (
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('You have view access only. Channel setup can be changed by users with exception edit permission.')}
                                    </Typography>
                                ) : (
                                    <Stack spacing={1.5}>
                                        <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))'}, gap: 1.5}}>
                                            <TextField size="small" label={t('Channel Name')} value={form.channelName} onChange={(event) => handleFormChange('channelName', event.target.value)} fullWidth/>
                                            <TextField select size="small" label={t('Channel Group')} value={form.channelGroup} onChange={(event) => handleFormChange('channelGroup', event.target.value)} fullWidth>
                                                {CHANNEL_GROUP_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                            </TextField>
                                            <TextField select size="small" label={t('Channel Type')} value={form.channelType} onChange={(event) => handleFormChange('channelType', event.target.value)} fullWidth>
                                                {supportedTypes.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                                            </TextField>
                                        </Box>

                                        <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                            <TextField select size="small" label={t('Module Scope')} value={form.reconView} onChange={(event) => handleFormChange('reconView', event.target.value)} fullWidth>
                                                {MODULE_OPTIONS.map((option) => <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>)}
                                            </TextField>
                                            <TextField select size="small" label={t('Channel Status')} value={form.active ? 'true' : 'false'} onChange={(event) => handleFormChange('active', event.target.value)} fullWidth>
                                                <MenuItem value="true">{t('Active')}</MenuItem>
                                                <MenuItem value="false">{t('Inactive')}</MenuItem>
                                            </TextField>
                                        </Box>

                                        {form.channelType === 'EMAIL' ? (
                                            <TextField
                                                size="small"
                                                label={t('Recipient Email')}
                                                value={form.recipientEmail}
                                                onChange={(event) => handleFormChange('recipientEmail', event.target.value)}
                                                helperText={t('This channel will send outbound updates to the configured mailbox or distribution list.')}
                                                fullWidth
                                            />
                                        ) : (
                                            <TextField
                                                size="small"
                                                label={t('Endpoint URL')}
                                                value={form.endpointUrl}
                                                onChange={(event) => handleFormChange('endpointUrl', event.target.value)}
                                                helperText={t('Use the inbound webhook or API endpoint for ServiceNow, Jira, Teams, or your generic integration target.')}
                                                fullWidth
                                            />
                                        )}

                                        {form.channelType === 'JIRA' ? (
                                            <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                                <TextField size="small" label={t('Default Project Key')} value={form.defaultProjectKey} onChange={(event) => handleFormChange('defaultProjectKey', event.target.value)} fullWidth/>
                                                <TextField size="small" label={t('Default Issue Type')} value={form.defaultIssueType} onChange={(event) => handleFormChange('defaultIssueType', event.target.value)} fullWidth/>
                                            </Box>
                                        ) : null}

                                        {form.channelType !== 'EMAIL' ? (
                                            <TextField
                                                size="small"
                                                label={t('Request Headers JSON')}
                                                value={form.headersJson}
                                                onChange={(event) => handleFormChange('headersJson', event.target.value)}
                                                multiline
                                                minRows={2}
                                                fullWidth
                                                helperText={t('Optional JSON object for static headers such as authorization tokens or content routing hints.')}
                                            />
                                        ) : null}

                                        <Paper elevation={0} sx={{p: 1.5, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                            <Stack spacing={1.2}>
                                                <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                                                    {t('Bidirectional Sync')}
                                                </Typography>
                                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                                    <TextField select size="small" label={t('Inbound Sync')} value={form.inboundSyncEnabled ? 'true' : 'false'} onChange={(event) => handleFormChange('inboundSyncEnabled', event.target.value)} fullWidth>
                                                        <MenuItem value="false">{t('Disabled')}</MenuItem>
                                                        <MenuItem value="true">{t('Enabled')}</MenuItem>
                                                    </TextField>
                                                    <TextField
                                                        size="small"
                                                        label={t('Inbound Shared Secret')}
                                                        value={form.inboundSharedSecret}
                                                        onChange={(event) => handleFormChange('inboundSharedSecret', event.target.value)}
                                                        fullWidth
                                                        helperText={selectedChannelId && !form.inboundSharedSecret
                                                            ? (channels.find((channel) => channel.id === selectedChannelId)?.inboundSharedSecretConfigured
                                                                ? t('A shared secret is already configured. Enter a new value only if you want to rotate it.')
                                                                : t('Required when inbound sync is enabled.'))
                                                            : t('Use this secret in the X-RetailINQ-Sync-Secret header for ServiceNow or Jira callbacks.')}
                                                    />
                                                </Box>
                                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                                    <TextField select size="small" label={t('Auto Create On Open')} value={form.autoCreateOnCaseOpen ? 'true' : 'false'} onChange={(event) => handleFormChange('autoCreateOnCaseOpen', event.target.value)} fullWidth>
                                                        <MenuItem value="false">{t('Disabled')}</MenuItem>
                                                        <MenuItem value="true">{t('Enabled')}</MenuItem>
                                                    </TextField>
                                                    <TextField select size="small" label={t('Auto Push On Escalation')} value={form.autoCreateOnEscalation ? 'true' : 'false'} onChange={(event) => handleFormChange('autoCreateOnEscalation', event.target.value)} fullWidth>
                                                        <MenuItem value="false">{t('Disabled')}</MenuItem>
                                                        <MenuItem value="true">{t('Enabled')}</MenuItem>
                                                    </TextField>
                                                </Box>
                                                {selectedChannelId && channels.find((channel) => channel.id === selectedChannelId)?.callbackUrl ? (
                                                    <Typography sx={{fontSize: '0.78rem', color: palette.blueChipText}}>
                                                        {t('Callback URL')}: {channels.find((channel) => channel.id === selectedChannelId)?.callbackUrl}
                                                    </Typography>
                                                ) : (
                                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {t('Save the channel first to generate the callback URL you will configure in ServiceNow, Jira, or your outbound webhook rule.')}
                                                    </Typography>
                                                )}
                                            </Stack>
                                        </Paper>

                                        <TextField size="small" label={t('Description')} value={form.description} onChange={(event) => handleFormChange('description', event.target.value)} multiline minRows={2} fullWidth/>

                                        <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, flexWrap: 'wrap', alignItems: 'center'}}>
                                            <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                                {t('Use generic webhook channels for the widest compatibility across customer environments.')}
                                            </Typography>
                                            <Button variant="contained" onClick={handleSave} disabled={saving} sx={{textTransform: 'none', fontWeight: 700}}>
                                                {saving ? t('Saving...') : selectedChannelId ? t('Save Changes') : t('Create Channel')}
                                            </Button>
                                        </Box>
                                    </Stack>
                                )}
                            </Paper>
                        </Grid>
                    </Grid>

                    <Grid container spacing={2}>
                        <Grid size={{xs: 12, lg: 6}}>
                            <Paper elevation={0} sx={{p: 2.25, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Recent External Tickets')}</Typography>
                                <Typography sx={{mt: 0.35, mb: 1.5, fontSize: '0.82rem', color: palette.textMuted}}>
                                    {t('The latest retailer-facing tickets created from exception cases and store incidents.')}
                                </Typography>
                                <Stack spacing={1.2}>
                                    {recentTickets.length ? recentTickets.map((ticket) => (
                                        <Paper key={ticket.id} elevation={0} sx={{p: 1.5, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 280px'}}>
                                                    <Typography sx={{fontSize: '0.88rem', fontWeight: 800, color: palette.text}}>
                                                        {ticket.ticketSummary || t('Ticket created')}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {ticket.channelName} | {ticket.channelType}
                                                    </Typography>
                                                    {ticket.externalReference ? (
                                                        <Typography sx={{mt: 0.45, fontSize: '0.8rem', color: palette.blueChipText}}>
                                                            {t('Reference')}: {ticket.externalReference}
                                                        </Typography>
                                                    ) : null}
                                                    {ticket.externalStatus ? (
                                                        <Typography sx={{mt: 0.35, fontSize: '0.78rem', color: palette.text}}>
                                                            {t('External Status')}: {ticket.externalStatus}
                                                        </Typography>
                                                    ) : null}
                                                    {ticket.lastExternalComment ? (
                                                        <Typography sx={{mt: 0.35, fontSize: '0.78rem', color: palette.textMuted}}>
                                                            {ticket.lastExternalComment}
                                                        </Typography>
                                                    ) : null}
                                                    {ticket.errorMessage ? (
                                                        <Typography sx={{mt: 0.45, fontSize: '0.78rem', color: '#B91C1C'}}>
                                                            {ticket.errorMessage}
                                                        </Typography>
                                                    ) : null}
                                                </Box>
                                                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                    <Chip size="small" label={ticket.deliveryStatus || 'CREATED'} sx={{backgroundColor: ticket.deliveryStatus === 'FAILED' ? '#FEF2F2' : palette.tealChipBg, color: ticket.deliveryStatus === 'FAILED' ? '#B91C1C' : palette.tealChipText, fontWeight: 700}}/>
                                                    {ticket.externalUrl ? (
                                                        <Button size="small" href={ticket.externalUrl} target="_blank" rel="noreferrer" sx={{textTransform: 'none'}}>
                                                            {t('Open')}
                                                        </Button>
                                                    ) : null}
                                                </Stack>
                                            </Box>
                                            <Typography sx={{mt: 0.7, fontSize: '0.74rem', color: palette.textMuted}}>
                                                {ticket.createdBy || t('System')} | {formatDateTimeValue(ticket.createdAt)} | {t('Last Sync')}: {formatDateTimeValue(ticket.lastExternalUpdateAt || ticket.lastSyncedAt)}
                                            </Typography>
                                        </Paper>
                                    )) : (
                                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No external ticket activity has been recorded yet.')}</Typography>
                                    )}
                                </Stack>
                            </Paper>
                        </Grid>

                        <Grid size={{xs: 12, lg: 6}}>
                            <Paper elevation={0} sx={{p: 2.25, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Recent Operational Updates')}</Typography>
                                <Typography sx={{mt: 0.35, mb: 1.5, fontSize: '0.82rem', color: palette.textMuted}}>
                                    {t('Outbound communications sent from cases and incidents to store, support, or enterprise channels.')}
                                </Typography>
                                <Stack spacing={1.2}>
                                    {recentCommunications.length ? recentCommunications.map((record) => (
                                        <Paper key={record.id} elevation={0} sx={{p: 1.5, borderRadius: 3, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 280px'}}>
                                                    <Typography sx={{fontSize: '0.88rem', fontWeight: 800, color: palette.text}}>
                                                        {record.subject || t('Operational update')}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {record.channelName} | {record.channelType}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.45, fontSize: '0.8rem', color: palette.text}}>
                                                        {record.recipient || t('Webhook delivery')}
                                                    </Typography>
                                                    {record.errorMessage ? (
                                                        <Typography sx={{mt: 0.45, fontSize: '0.78rem', color: '#B91C1C'}}>
                                                            {record.errorMessage}
                                                        </Typography>
                                                    ) : null}
                                                </Box>
                                                <Chip size="small" label={record.deliveryStatus || 'SENT'} sx={{backgroundColor: record.deliveryStatus === 'FAILED' ? '#FEF2F2' : palette.blueChipBg, color: record.deliveryStatus === 'FAILED' ? '#B91C1C' : palette.blueChipText, fontWeight: 700}}/>
                                            </Box>
                                            <Typography sx={{mt: 0.7, fontSize: '0.74rem', color: palette.textMuted}}>
                                                {record.createdBy || t('System')} | {formatDateTimeValue(record.createdAt)}
                                            </Typography>
                                        </Paper>
                                    )) : (
                                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No outbound communication activity has been recorded yet.')}</Typography>
                                    )}
                                </Stack>
                            </Paper>
                        </Grid>
                    </Grid>
                </>
            )}
        </Box>
    )
}
