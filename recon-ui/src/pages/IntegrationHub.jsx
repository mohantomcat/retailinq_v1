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
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from '@mui/material'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import {useAuth} from '../context/AuthContext'
import {integrationHubApi} from '../services/integrationHubApi'

function SummaryCard({label, value, supporting, palette, tone}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.65, fontSize: '1.5rem', fontWeight: 800, color: tone || palette.text}}>{value}</Typography>
            <Typography sx={{mt: 0.45, fontSize: '0.76rem', color: palette.textMuted}}>{supporting}</Typography>
        </Paper>
    )
}

function statusChip(status, palette) {
    switch ((status || '').toUpperCase()) {
        case 'COMPLETED':
        case 'PUBLISHED':
            return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
        case 'RUNNING':
        case 'QUEUED':
        case 'REQUESTED':
            return {backgroundColor: palette.blueChipBg, color: palette.blueChipText}
        case 'COMPLETED_WITH_ERRORS':
        case 'FAILED':
        case 'OPEN':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'UNSUPPORTED':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        default:
            return {backgroundColor: palette.cardBgAlt, color: palette.textMuted}
    }
}

function formatDateTimeValue(value) {
    if (!value) return '-'
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value.replace('T', ' ') : parsed.toLocaleString()
}

function formatReplayWindow(request) {
    if (request.requestedFrom && request.requestedTo) {
        return `${formatDateTimeValue(request.requestedFrom)} -> ${formatDateTimeValue(request.requestedTo)}`
    }
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
    const canView = hasPermission('INTEGRATION_HUB_VIEW')
    const canReplay = hasPermission('INTEGRATION_HUB_REPLAY')
    const [loading, setLoading] = useState(true)
    const [messagesLoading, setMessagesLoading] = useState(true)
    const [messageDetailLoading, setMessageDetailLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [executingReplayId, setExecutingReplayId] = useState('')
    const [resolvingErrorId, setResolvingErrorId] = useState('')
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')
    const [data, setData] = useState(null)
    const [messageTrace, setMessageTrace] = useState([])
    const [selectedMessageDetail, setSelectedMessageDetail] = useState(null)
    const [form, setForm] = useState({
        connectorKey: '',
        flowKey: '',
        businessKey: '',
        documentId: '',
        requestedFrom: '',
        requestedTo: '',
        reason: '',
    })
    const [messageFilters, setMessageFilters] = useState({
        connectorKey: '',
        flowKey: '',
        businessKey: '',
        documentId: '',
        messageStatus: '',
    })

    const loadMessageTrace = async (filters = messageFilters) => {
        try {
            setMessagesLoading(true)
            const response = await integrationHubApi.getMessages({...filters, limit: 20})
            setMessageTrace(response || [])
        } catch (err) {
            setError(err.message || 'Failed to load message trace')
        } finally {
            setMessagesLoading(false)
        }
    }

    const loadMessageDetail = async (messageId) => {
        if (!messageId) {
            setSelectedMessageDetail(null)
            return
        }
        try {
            setMessageDetailLoading(true)
            setError('')
            const response = await integrationHubApi.getMessageDetail(messageId)
            setSelectedMessageDetail(response)
        } catch (err) {
            setError(err.message || 'Failed to load message detail')
        } finally {
            setMessageDetailLoading(false)
        }
    }

    const loadData = async () => {
        try {
            setLoading(true)
            setError('')
            const response = await integrationHubApi.getHub()
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

    useEffect(() => {
        if (!canView) {
            return
        }
        loadData()
        loadMessageTrace({
            connectorKey: '',
            flowKey: '',
            businessKey: '',
            documentId: '',
            messageStatus: '',
        })
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canView])

    const summary = data?.summary || {}
    const connectors = useMemo(() => data?.connectors || [], [data])
    const flows = useMemo(() => data?.flows || [], [data])
    const recentRuns = useMemo(() => data?.recentRuns || [], [data])
    const openErrors = useMemo(() => data?.openErrors || [], [data])
    const replayRequests = useMemo(() => data?.replayRequests || [], [data])
    const executableReplayConnectors = useMemo(
        () => new Set(['siocs-cloud-main', 'mfcs-rds-main', 'xocs-cloud-main']),
        [],
    )

    if (!canView) {
        return null
    }

    const handleReplaySubmit = async () => {
        try {
            setSaving(true)
            setSuccess('')
            setError('')
            await integrationHubApi.createReplayRequest(form)
            setSuccess(t('Replay request created'))
            setForm((current) => ({
                ...current,
                businessKey: '',
                documentId: '',
                requestedFrom: '',
                requestedTo: '',
                reason: '',
            }))
            await loadData()
        } catch (err) {
            setError(err.message || 'Failed to create replay request')
        } finally {
            setSaving(false)
        }
    }

    const handleReplayExecution = async (requestId) => {
        try {
            setExecutingReplayId(requestId)
            setSuccess('')
            setError('')
            const response = await integrationHubApi.executeReplayRequest(requestId)
            setSuccess(response?.resolutionMessage || t('Replay execution updated'))
            await loadData()
        } catch (err) {
            setError(err.message || 'Failed to execute replay request')
        } finally {
            setExecutingReplayId('')
        }
    }

    const useReplayScope = ({connectorKey, flowKey, businessKey, documentId, timestamp, reason}) => {
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
        setSuccess(t('Replay request form populated from Integration Hub trace'))
    }

    const handleResolveError = async (errorItem) => {
        try {
            setResolvingErrorId(errorItem.id)
            setError('')
            setSuccess('')
            await integrationHubApi.resolveError(errorItem.id, {
                resolutionNotes: `Resolved from Integration Hub for ${errorItem.businessKey || errorItem.documentId || errorItem.connectorKey}`,
            })
            setSuccess(t('Integration error marked as resolved'))
            await loadData()
            if (selectedMessageDetail?.id) {
                await loadMessageDetail(selectedMessageDetail.id)
            }
        } catch (err) {
            setError(err.message || 'Failed to resolve integration error')
        } finally {
            setResolvingErrorId('')
        }
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 42, height: 42, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.blueChipBg, color: palette.blueChipText, flexShrink: 0}}>
                        <HubOutlinedIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Integration Hub')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Internal control plane for connector contracts, runtime monitoring, message traceability, and replay handling.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}
            {success ? <Alert severity="success" sx={{mb: 2}}>{success}</Alert> : null}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(6, minmax(0, 1fr))'}, gap: 1.5, mb: 3}}>
                        <SummaryCard label={t('Active Connectors')} value={summary.activeConnectors || 0} supporting={t('Registered connector runtimes')} tone={palette.text} palette={palette}/>
                        <SummaryCard label={t('Active Flows')} value={summary.activeFlows || 0} supporting={t('Canonical and runtime flows')} tone={palette.blueChipText} palette={palette}/>
                        <SummaryCard label={t('Running Runs')} value={summary.runningRuns || 0} supporting={t('Runs still in progress')} tone="#D97706" palette={palette}/>
                        <SummaryCard label={t('Failed / 24h')} value={summary.failedRunsLast24Hours || 0} supporting={t('Runs with failures in the last day')} tone="#B91C1C" palette={palette}/>
                        <SummaryCard label={t('Open Errors')} value={summary.openErrors || 0} supporting={t('Messages needing intervention')} tone="#B91C1C" palette={palette}/>
                        <SummaryCard label={t('Published / 24h')} value={summary.publishedMessagesLast24Hours || 0} supporting={t('Tracked messages published')} tone="#15803D" palette={palette}/>
                    </Box>

                    <Grid container spacing={2.2} sx={{mb: 3}}>
                        <Grid item xs={12} xl={7}>
                            <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                                <Box sx={{px: 2.5, py: 2}}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Connector Runtime')}</Typography>
                                </Box>
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('Connector')}</TableCell>
                                                <TableCell>{t('Systems')}</TableCell>
                                                <TableCell>{t('Latest Run')}</TableCell>
                                                <TableCell>{t('24h Volume')}</TableCell>
                                                <TableCell>{t('Open Errors')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {connectors.length ? connectors.map((connector) => (
                                                <TableRow key={connector.connectorKey} hover>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{connector.connectorLabel}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{connector.connectorKey}</Typography>
                                                    </TableCell>
                                                    <TableCell>{connector.sourceSystem} {'->'} {connector.targetSystem || 'RECON'}</TableCell>
                                                    <TableCell>
                                                        <Chip size="small" label={connector.latestRunStatus || t('No runs')} sx={{fontWeight: 700, ...statusChip(connector.latestRunStatus, palette)}}/>
                                                        <Typography sx={{mt: 0.45, fontSize: '0.75rem', color: palette.textMuted}}>
                                                            {formatDateTimeValue(connector.latestRunStartedAt)}
                                                        </Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{connector.publishedMessagesLast24Hours || 0} {t('published')}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{connector.failedMessagesLast24Hours || 0} {t('failed')}</Typography>
                                                    </TableCell>
                                                    <TableCell>{connector.openErrorCount || 0}</TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={5}>
                                                        <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>{t('No connector runtime has registered with the Integration Hub yet.')}</Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Paper>
                        </Grid>

                        <Grid item xs={12} xl={5}>
                            <Paper elevation={0} sx={{p: 2.25, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 1.5}}>{t('Replay & Reprocessing')}</Typography>
                                <Stack spacing={1.2}>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Connector')}</InputLabel>
                                        <Select value={form.connectorKey} label={t('Connector')} onChange={(event) => setForm((current) => ({...current, connectorKey: event.target.value}))}>
                                            {connectors.map((connector) => (
                                                <MenuItem key={connector.connectorKey} value={connector.connectorKey}>{connector.connectorLabel}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Flow')}</InputLabel>
                                        <Select value={form.flowKey} label={t('Flow')} onChange={(event) => setForm((current) => ({...current, flowKey: event.target.value}))}>
                                            {flows.map((flow) => (
                                                <MenuItem key={flow.flowKey} value={flow.flowKey}>{flow.flowLabel}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <TextField size="small" label={t('Business Key')} value={form.businessKey} onChange={(event) => setForm((current) => ({...current, businessKey: event.target.value}))}/>
                                    <TextField size="small" label={t('Document Id')} value={form.documentId} onChange={(event) => setForm((current) => ({...current, documentId: event.target.value}))}/>
                                    <TextField size="small" type="datetime-local" label={t('Requested From')} InputLabelProps={{shrink: true}} value={form.requestedFrom} onChange={(event) => setForm((current) => ({...current, requestedFrom: event.target.value}))}/>
                                    <TextField size="small" type="datetime-local" label={t('Requested To')} InputLabelProps={{shrink: true}} value={form.requestedTo} onChange={(event) => setForm((current) => ({...current, requestedTo: event.target.value}))}/>
                                    <TextField size="small" label={t('Reason')} value={form.reason} onChange={(event) => setForm((current) => ({...current, reason: event.target.value}))} multiline minRows={2}/>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={handleReplaySubmit} disabled={!canReplay || saving || !form.connectorKey || !form.flowKey}>{t('Request Replay')}</Button>
                                        <Button variant="outlined" onClick={loadData} disabled={loading || saving}>{t('Refresh')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>
                        </Grid>
                    </Grid>

                    <Grid container spacing={2.2} sx={{mb: 3}}>
                        <Grid item xs={12} xl={6}>
                            <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                                <Box sx={{px: 2.5, py: 2}}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Flows')}</Typography>
                                </Box>
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('Flow')}</TableCell>
                                                <TableCell>{t('Message Type')}</TableCell>
                                                <TableCell>{t('24h')}</TableCell>
                                                <TableCell>{t('Latest Run')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {flows.length ? flows.map((flow) => (
                                                <TableRow key={flow.flowKey} hover>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{flow.flowLabel}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{flow.flowKey}</Typography>
                                                    </TableCell>
                                                    <TableCell>{flow.messageType}</TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{flow.messagesLast24Hours || 0} {t('messages')}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{flow.errorsLast24Hours || 0} {t('errors')}</Typography>
                                                    </TableCell>
                                                    <TableCell><Chip size="small" label={flow.latestRunStatus || t('No runs')} sx={{fontWeight: 700, ...statusChip(flow.latestRunStatus, palette)}}/></TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={4}>
                                                        <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>{t('No flows are registered yet.')}</Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Paper>
                        </Grid>

                        <Grid item xs={12} xl={6}>
                            <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                                <Box sx={{px: 2.5, py: 2}}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Recent Runs')}</Typography>
                                </Box>
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('When')}</TableCell>
                                                <TableCell>{t('Connector')}</TableCell>
                                                <TableCell>{t('Status')}</TableCell>
                                                <TableCell>{t('Counts')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {recentRuns.length ? recentRuns.map((run) => (
                                                <TableRow key={run.id} hover>
                                                    <TableCell>{formatDateTimeValue(run.startedAt)}</TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{run.connectorKey}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{run.flowKey}</Typography>
                                                    </TableCell>
                                                    <TableCell><Chip size="small" label={run.runStatus} sx={{fontWeight: 700, ...statusChip(run.runStatus, palette)}}/></TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{run.publishedRecordCount || 0} / {run.sourceRecordCount || 0}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{run.errorCount || 0} {t('errors')}</Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={4}>
                                                        <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>{t('No integration runs are available yet.')}</Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Paper>
                        </Grid>
                    </Grid>

                    <Paper elevation={0} sx={{mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                        <Box sx={{px: 2.5, py: 2}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Message Trace')}</Typography>
                            <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                {t('Search journaled messages by connector, flow, business key, document, or status before deciding to replay.')}
                            </Typography>
                        </Box>
                        <Box sx={{px: 2.5, pb: 2}}>
                            <Stack direction={{xs: 'column', lg: 'row'}} spacing={1.2} sx={{flexWrap: 'wrap'}}>
                                <FormControl size="small" sx={{minWidth: 180}}>
                                    <InputLabel>{t('Connector')}</InputLabel>
                                    <Select value={messageFilters.connectorKey} label={t('Connector')} onChange={(event) => setMessageFilters((current) => ({...current, connectorKey: event.target.value}))}>
                                        <MenuItem value="">{t('All')}</MenuItem>
                                        {connectors.map((connector) => (
                                            <MenuItem key={connector.connectorKey} value={connector.connectorKey}>{connector.connectorLabel}</MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>
                                <FormControl size="small" sx={{minWidth: 220}}>
                                    <InputLabel>{t('Flow')}</InputLabel>
                                    <Select value={messageFilters.flowKey} label={t('Flow')} onChange={(event) => setMessageFilters((current) => ({...current, flowKey: event.target.value}))}>
                                        <MenuItem value="">{t('All')}</MenuItem>
                                        {flows.map((flow) => (
                                            <MenuItem key={flow.flowKey} value={flow.flowKey}>{flow.flowLabel}</MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>
                                <TextField size="small" label={t('Business Key')} value={messageFilters.businessKey} onChange={(event) => setMessageFilters((current) => ({...current, businessKey: event.target.value}))}/>
                                <TextField size="small" label={t('Document Id')} value={messageFilters.documentId} onChange={(event) => setMessageFilters((current) => ({...current, documentId: event.target.value}))}/>
                                <FormControl size="small" sx={{minWidth: 160}}>
                                    <InputLabel>{t('Status')}</InputLabel>
                                    <Select value={messageFilters.messageStatus} label={t('Status')} onChange={(event) => setMessageFilters((current) => ({...current, messageStatus: event.target.value}))}>
                                        <MenuItem value="">{t('All')}</MenuItem>
                                        <MenuItem value="PUBLISHED">{t('Published')}</MenuItem>
                                        <MenuItem value="FAILED">{t('Failed')}</MenuItem>
                                    </Select>
                                </FormControl>
                                <Button variant="contained" onClick={() => loadMessageTrace()} disabled={messagesLoading}>{t('Load Trace')}</Button>
                                <Button
                                    variant="outlined"
                                    onClick={() => {
                                        const cleared = {connectorKey: '', flowKey: '', businessKey: '', documentId: '', messageStatus: ''}
                                        setMessageFilters(cleared)
                                        loadMessageTrace(cleared)
                                    }}
                                    disabled={messagesLoading}
                                >
                                    {t('Clear')}
                                </Button>
                            </Stack>
                        </Box>
                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                            <TableRow>
                                                <TableCell>{t('When')}</TableCell>
                                                <TableCell>{t('Connector')}</TableCell>
                                                <TableCell>{t('Message')}</TableCell>
                                                <TableCell>{t('Business Key')}</TableCell>
                                                <TableCell>{t('Status')}</TableCell>
                                                <TableCell>{t('Actions')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {messagesLoading ? (
                                                <TableRow>
                                            <TableCell colSpan={6}>
                                                <Box sx={{display: 'flex', justifyContent: 'center', py: 2}}><CircularProgress size={22}/></Box>
                                            </TableCell>
                                        </TableRow>
                                    ) : messageTrace.length ? messageTrace.map((message) => (
                                        <TableRow
                                            key={message.id}
                                            hover
                                            selected={selectedMessageDetail?.id === message.id}
                                            onClick={() => loadMessageDetail(message.id)}
                                            sx={{cursor: 'pointer'}}
                                        >
                                            <TableCell>{formatDateTimeValue(message.createdAt)}</TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{message.connectorKey}</Typography>
                                                <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{message.flowKey}</Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{message.messageType}</Typography>
                                                <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{message.documentId || message.messageId}</Typography>
                                            </TableCell>
                                            <TableCell>{message.businessKey || '-'}</TableCell>
                                            <TableCell><Chip size="small" label={message.messageStatus} sx={{fontWeight: 700, ...statusChip(message.messageStatus, palette)}}/></TableCell>
                                            <TableCell>
                                                <Stack direction="row" spacing={1}>
                                                    <Button
                                                        variant="outlined"
                                                        size="small"
                                                        onClick={(event) => {
                                                            event.stopPropagation()
                                                            loadMessageDetail(message.id)
                                                        }}
                                                    >
                                                        {t('Trace')}
                                                    </Button>
                                                    <Button
                                                        variant="outlined"
                                                        size="small"
                                                        onClick={(event) => {
                                                            event.stopPropagation()
                                                            useReplayScope({
                                                                connectorKey: message.connectorKey,
                                                                flowKey: message.flowKey,
                                                                businessKey: message.businessKey,
                                                                documentId: message.documentId,
                                                                timestamp: message.createdAt,
                                                                reason: `Replay requested from Integration Hub trace for ${message.businessKey || message.documentId || message.messageId}`,
                                                            })
                                                        }}
                                                    >
                                                        {t('Use for Replay')}
                                                    </Button>
                                                </Stack>
                                            </TableCell>
                                        </TableRow>
                                    )) : (
                                        <TableRow>
                                            <TableCell colSpan={6}>
                                                <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>{t('No journaled messages matched the current trace filters.')}</Typography>
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </TableContainer>
                        {messageDetailLoading || selectedMessageDetail ? (
                            <Box sx={{borderTop: `1px solid ${palette.border}`, px: 2.5, py: 2.25, backgroundColor: palette.cardBgAlt}}>
                                {messageDetailLoading ? (
                                    <Box sx={{display: 'flex', justifyContent: 'center', py: 2}}><CircularProgress size={22}/></Box>
                                ) : selectedMessageDetail ? (
                                    <Grid container spacing={2}>
                                        <Grid item xs={12} lg={7}>
                                            <Stack spacing={1.2}>
                                                <Box sx={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1}}>
                                                    <Box>
                                                        <Typography sx={{fontSize: '0.98rem', fontWeight: 800, color: palette.text}}>
                                                            {t('Selected Message Detail')}
                                                        </Typography>
                                                        <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                            {selectedMessageDetail.messageId}
                                                        </Typography>
                                                    </Box>
                                                    <Stack direction="row" spacing={1}>
                                                        <Button
                                                            variant="outlined"
                                                            size="small"
                                                            onClick={() => useReplayScope({
                                                                connectorKey: selectedMessageDetail.connectorKey,
                                                                flowKey: selectedMessageDetail.flowKey,
                                                                businessKey: selectedMessageDetail.businessKey,
                                                                documentId: selectedMessageDetail.documentId,
                                                                timestamp: selectedMessageDetail.createdAt || selectedMessageDetail.runStartedAt,
                                                                reason: `Replay requested from traced message ${selectedMessageDetail.messageId}`,
                                                            })}
                                                        >
                                                            {t('Use for Replay')}
                                                        </Button>
                                                    </Stack>
                                                </Box>
                                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1}}>
                                                    <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                        <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted}}>{t('Routing')}</Typography>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{selectedMessageDetail.connectorKey}</Typography>
                                                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{selectedMessageDetail.flowKey}</Typography>
                                                        <Typography sx={{mt: 0.6, fontSize: '0.76rem', color: palette.textMuted}}>
                                                            {selectedMessageDetail.sourceSystem} {'->'} {selectedMessageDetail.targetSystem || 'RECON'}
                                                        </Typography>
                                                    </Paper>
                                                    <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                        <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted}}>{t('Run')}</Typography>
                                                        <Chip size="small" label={selectedMessageDetail.runStatus || t('No run')} sx={{mt: 0.4, fontWeight: 700, ...statusChip(selectedMessageDetail.runStatus, palette)}}/>
                                                        <Typography sx={{mt: 0.55, fontSize: '0.76rem', color: palette.textMuted}}>
                                                            {formatDateTimeValue(selectedMessageDetail.runStartedAt)}
                                                        </Typography>
                                                        {selectedMessageDetail.runSummary ? (
                                                            <Typography sx={{mt: 0.4, fontSize: '0.76rem', color: palette.textMuted}}>
                                                                {selectedMessageDetail.runSummary}
                                                            </Typography>
                                                        ) : null}
                                                    </Paper>
                                                </Box>
                                                <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                    <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted, mb: 0.7}}>{t('Payload Snapshot')}</Typography>
                                                    <Box component="pre" sx={{m: 0, maxHeight: 260, overflow: 'auto', fontSize: '0.74rem', lineHeight: 1.45, color: palette.text, whiteSpace: 'pre-wrap', wordBreak: 'break-word'}}>
                                                        {selectedMessageDetail.payloadSnapshotJson || t('No payload snapshot was captured for this message.')}
                                                    </Box>
                                                </Paper>
                                            </Stack>
                                        </Grid>
                                        <Grid item xs={12} lg={5}>
                                            <Stack spacing={1.2}>
                                                <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                    <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted, mb: 0.8}}>{t('Related Errors')}</Typography>
                                                    <Stack spacing={1}>
                                                        {selectedMessageDetail.relatedErrors?.length ? selectedMessageDetail.relatedErrors.map((item) => (
                                                            <Box key={item.id} sx={{p: 1.1, borderRadius: '14px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                                                <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center">
                                                                    <Chip size="small" label={item.errorStatus} sx={{fontWeight: 700, ...statusChip(item.errorStatus, palette)}}/>
                                                                    {item.errorStatus === 'OPEN' ? (
                                                                        <Button
                                                                            variant="outlined"
                                                                            size="small"
                                                                            onClick={() => handleResolveError(item)}
                                                                            disabled={resolvingErrorId === item.id}
                                                                        >
                                                                            {resolvingErrorId === item.id ? t('Resolving...') : t('Resolve')}
                                                                        </Button>
                                                                    ) : null}
                                                                </Stack>
                                                                <Typography sx={{mt: 0.7, fontSize: '0.8rem', fontWeight: 700, color: palette.text}}>{item.errorType}</Typography>
                                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{item.errorMessage}</Typography>
                                                                <Typography sx={{mt: 0.45, fontSize: '0.72rem', color: palette.textMuted}}>
                                                                    {formatDateTimeValue(item.createdAt)}
                                                                </Typography>
                                                            </Box>
                                                        )) : (
                                                            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>{t('No related errors were found for this message.')}</Typography>
                                                        )}
                                                    </Stack>
                                                </Paper>
                                                <Paper elevation={0} sx={{p: 1.5, borderRadius: '16px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                                    <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted, mb: 0.8}}>{t('Related Replay Requests')}</Typography>
                                                    <Stack spacing={1}>
                                                        {selectedMessageDetail.relatedReplayRequests?.length ? selectedMessageDetail.relatedReplayRequests.map((request) => (
                                                            <Box key={request.id} sx={{p: 1.1, borderRadius: '14px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                                                <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center">
                                                                    <Chip size="small" label={request.replayStatus} sx={{fontWeight: 700, ...statusChip(request.replayStatus, palette)}}/>
                                                                    {canReplay && executableReplayConnectors.has(request.connectorKey) && ['REQUESTED', 'FAILED'].includes((request.replayStatus || '').toUpperCase()) ? (
                                                                        <Button
                                                                            variant="outlined"
                                                                            size="small"
                                                                            onClick={() => handleReplayExecution(request.id)}
                                                                            disabled={executingReplayId === request.id}
                                                                        >
                                                                            {executingReplayId === request.id ? t('Running...') : t('Execute')}
                                                                        </Button>
                                                                    ) : null}
                                                                </Stack>
                                                                <Typography sx={{mt: 0.7, fontSize: '0.78rem', color: palette.text}}>
                                                                    {formatReplayWindow(request)}
                                                                </Typography>
                                                                <Typography sx={{fontSize: '0.72rem', color: palette.textMuted}}>
                                                                    {request.reason || '-'}
                                                                </Typography>
                                                            </Box>
                                                        )) : (
                                                            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>{t('No replay requests have been raised for this trace yet.')}</Typography>
                                                        )}
                                                    </Stack>
                                                </Paper>
                                            </Stack>
                                        </Grid>
                                    </Grid>
                                ) : null}
                            </Box>
                        ) : null}
                    </Paper>

                    <Grid container spacing={2.2}>
                        <Grid item xs={12} xl={6}>
                            <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                                <Box sx={{px: 2.5, py: 2}}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Open Error Queue')}</Typography>
                                </Box>
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('Connector')}</TableCell>
                                                <TableCell>{t('Business Key')}</TableCell>
                                                <TableCell>{t('Error')}</TableCell>
                                                <TableCell>{t('Actions')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {openErrors.length ? openErrors.map((item) => (
                                                <TableRow key={item.id} hover>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{item.connectorKey}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{formatDateTimeValue(item.createdAt)}</Typography>
                                                    </TableCell>
                                                    <TableCell>{item.businessKey || item.documentId || '-'}</TableCell>
                                                    <TableCell sx={{minWidth: 260}}>
                                                        <Chip size="small" label={item.errorType} sx={{fontWeight: 700, ...statusChip(item.errorStatus, palette)}}/>
                                                        <Typography sx={{mt: 0.45, fontSize: '0.75rem', color: palette.textMuted}}>{item.errorMessage}</Typography>
                                                    </TableCell>
                                                    <TableCell sx={{minWidth: 220}}>
                                                        <Stack direction="row" spacing={1}>
                                                            {item.integrationMessageId ? (
                                                                <Button variant="outlined" size="small" onClick={() => loadMessageDetail(item.integrationMessageId)}>
                                                                    {t('Open Trace')}
                                                                </Button>
                                                            ) : null}
                                                            <Button
                                                                variant="outlined"
                                                                size="small"
                                                                onClick={() => useReplayScope({
                                                                    connectorKey: item.connectorKey,
                                                                    flowKey: item.flowKey,
                                                                    businessKey: item.businessKey,
                                                                    documentId: item.documentId,
                                                                    timestamp: item.createdAt,
                                                                    reason: `Replay requested from Integration Hub error queue for ${item.businessKey || item.documentId || item.connectorKey}`,
                                                                })}
                                                            >
                                                                {t('Use for Replay')}
                                                            </Button>
                                                            <Button
                                                                variant="outlined"
                                                                size="small"
                                                                onClick={() => handleResolveError(item)}
                                                                disabled={resolvingErrorId === item.id}
                                                            >
                                                                {resolvingErrorId === item.id ? t('Resolving...') : t('Resolve')}
                                                            </Button>
                                                        </Stack>
                                                    </TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={4}>
                                                        <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>{t('No open integration errors are currently queued.')}</Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Paper>
                        </Grid>

                        <Grid item xs={12} xl={6}>
                            <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                                <Box sx={{px: 2.5, py: 2}}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Replay Requests')}</Typography>
                                </Box>
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('When')}</TableCell>
                                                <TableCell>{t('Flow')}</TableCell>
                                                <TableCell>{t('Scope')}</TableCell>
                                                <TableCell>{t('Status')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {replayRequests.length ? replayRequests.map((request) => (
                                                <TableRow key={request.id} hover>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{formatDateTimeValue(request.requestedAt)}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{request.requestedBy}</Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{request.connectorKey}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{request.flowKey}</Typography>
                                                    </TableCell>
                                                    <TableCell sx={{minWidth: 220}}>
                                                        <Typography sx={{fontSize: '0.8rem', color: palette.text}}>{formatReplayWindow(request)}</Typography>
                                                        <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{request.reason || '-'}</Typography>
                                                        {request.resolutionMessage ? <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{request.resolutionMessage}</Typography> : null}
                                                    </TableCell>
                                                    <TableCell sx={{minWidth: 180}}>
                                                        <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                                                            <Chip size="small" label={request.replayStatus} sx={{fontWeight: 700, ...statusChip(request.replayStatus, palette)}}/>
                                                            {canReplay && executableReplayConnectors.has(request.connectorKey) && ['REQUESTED', 'FAILED'].includes((request.replayStatus || '').toUpperCase()) ? (
                                                                <Button variant="outlined" size="small" onClick={() => handleReplayExecution(request.id)} disabled={executingReplayId === request.id}>
                                                                    {executingReplayId === request.id ? t('Running...') : t('Execute')}
                                                                </Button>
                                                            ) : null}
                                                        </Stack>
                                                    </TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={4}>
                                                        <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>{t('No replay requests have been captured yet.')}</Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Paper>
                        </Grid>
                    </Grid>
                </>
            )}
        </Box>
    )
}
