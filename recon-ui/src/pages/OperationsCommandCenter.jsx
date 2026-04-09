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
    Typography,
} from '@mui/material'
import DashboardRoundedIcon from '@mui/icons-material/DashboardRounded'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {useReconModules} from '../hooks/useReconModules'

function SummaryCard({label, value, supporting, tone, palette}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.65, fontSize: '1.5rem', fontWeight: 800, color: tone}}>{value}</Typography>
            <Typography sx={{mt: 0.45, fontSize: '0.76rem', color: palette.textMuted}}>{supporting}</Typography>
        </Paper>
    )
}

function formatCurrencyValue(amount, currencyCode) {
    if (amount === null || amount === undefined || amount === '') return '-'
    const numeric = Number(amount)
    if (!Number.isFinite(numeric)) return `${currencyCode ? `${currencyCode} ` : ''}${amount}`
    if (!currencyCode) return numeric.toLocaleString(undefined, {maximumFractionDigits: 2})
    try {
        return new Intl.NumberFormat(undefined, {
            style: 'currency',
            currency: currencyCode,
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        }).format(numeric)
    } catch {
        return `${currencyCode} ${numeric.toLocaleString(undefined, {maximumFractionDigits: 2})}`
    }
}

function formatDateTimeValue(value) {
    if (!value) return '-'
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value.replace('T', ' ') : parsed.toLocaleString()
}

function bandChipStyles(band, palette) {
    switch ((band || '').toUpperCase()) {
        case 'CRITICAL':
        case 'SPREADING':
        case 'HIGH':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'WARNING':
        case 'EMERGING':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        case 'MEDIUM':
        case 'STALE':
            return {backgroundColor: palette.blueChipBg, color: palette.blueChipText}
        case 'HEALTHY':
        case 'LOCALIZED':
        case 'CONTAINED':
            return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
        default:
            return {backgroundColor: palette.cardBgAlt, color: palette.textMuted}
    }
}

function targetTabLabel(targetTab, t) {
    switch (targetTab) {
        case 'operations':
            return t('Operations')
        case 'regional-incident-board':
            return t('Regional Incident Board')
        case 'exception-queues':
            return t('Exception Queues')
        case 'ticketing-comms':
            return t('Ticketing & Comms')
        case 'noise-suppression':
            return t('Noise Suppression')
        default:
            return targetTab || t('Dashboard')
    }
}

export default function OperationsCommandCenter({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptionsWithAll: MODULE_OPTIONS} = useReconModules()
    const canView = hasPermission('REPORTS_VIEW')
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [data, setData] = useState(null)
    const [filters, setFilters] = useState({reconView: ''})

    const loadData = async (nextFilters = filters) => {
        try {
            setLoading(true)
            setError('')
            const response = await exceptionApi.getOperationsCommandCenter({
                reconView: nextFilters.reconView,
            })
            setData(response)
        } catch (err) {
            setError(err.message || 'Failed to load operations command center')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (canView) {
            loadData(filters)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canView])

    const summary = data?.summary || {}
    const priorityActions = useMemo(() => data?.priorityActions || [], [data])
    const integrationHealth = useMemo(() => data?.integrationHealth || [], [data])
    const outbreaks = useMemo(() => data?.outbreaks || [], [data])
    const queueLanes = useMemo(() => data?.queueLanes || [], [data])
    const recentAutomation = useMemo(() => data?.recentAutomation || [], [data])

    if (!canView) {
        return null
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 42, height: 42, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.blueChipBg, color: palette.blueChipText, flexShrink: 0}}>
                        <DashboardRoundedIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Operations Command Center')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('One leadership view for queue pressure, spreading incidents, connector health, automation load, and the business value at risk across the retail operation.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            <Paper elevation={0} sx={{p: 2.25, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'minmax(0, 1fr) auto'}, gap: 1.5, alignItems: 'end'}}>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Module')}</InputLabel>
                        <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}>
                            {MODULE_OPTIONS.map((option) => (
                                <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <Stack direction={{xs: 'column', sm: 'row'}} spacing={1}>
                        <Button variant="contained" onClick={() => loadData(filters)} disabled={loading}>{t('Apply')}</Button>
                        <Button
                            variant="outlined"
                            onClick={() => {
                                const cleared = {reconView: ''}
                                setFilters(cleared)
                                loadData(cleared)
                            }}
                            disabled={loading}
                        >
                            {t('Clear')}
                        </Button>
                    </Stack>
                </Box>
            </Paper>

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(6, minmax(0, 1fr))'}, gap: 1.5, mb: 3}}>
                        <SummaryCard label={t('Open Cases')} value={summary.openCases || 0} supporting={t('Current queue volume')} tone={palette.text} palette={palette}/>
                        <SummaryCard label={t('Overdue Cases')} value={summary.overdueCases || 0} supporting={t('Already outside SLA')} tone="#B91C1C" palette={palette}/>
                        <SummaryCard label={t('Active Incidents')} value={summary.activeIncidents || 0} supporting={t('Store incidents being worked')} tone={palette.blueChipText} palette={palette}/>
                        <SummaryCard label={t('Stores At Risk')} value={summary.storesAtRisk || 0} supporting={t('Distinct stores impacted')} tone="#7C3AED" palette={palette}/>
                        <SummaryCard label={t('Unhealthy Integrations')} value={summary.unhealthyIntegrations || 0} supporting={t('Modules needing intervention')} tone="#D97706" palette={palette}/>
                        <SummaryCard label={t('Value At Risk')} value={formatCurrencyValue(summary.businessValue?.valueAtRisk, summary.businessValue?.currencyCode)} supporting={summary.businessValue?.summary || t('Aggregated business exposure')} tone="#15803D" palette={palette}/>
                    </Box>

                    <Grid container spacing={2.2} sx={{mb: 3}}>
                        <Grid size={{xs: 12, lg: 5}}>
                            <Paper elevation={0} sx={{p: 2.25, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '0.98rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                                    {t('Priority Actions')}
                                </Typography>
                                {priorityActions.length ? (
                                    <Stack spacing={1.15}>
                                        {priorityActions.map((action, index) => (
                                            <Box key={`${action.title}-${index}`} sx={{p: 1.35, borderRadius: 2.75, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                                                    <Typography sx={{fontSize: '0.88rem', fontWeight: 800, color: palette.text}}>
                                                        {action.title}
                                                    </Typography>
                                                    <Chip size="small" label={action.severity || t('Priority')} sx={{fontWeight: 700, ...bandChipStyles(action.severity, palette)}}/>
                                                </Box>
                                                <Typography sx={{mt: 0.55, fontSize: '0.8rem', color: palette.text}}>
                                                    {action.detail}
                                                </Typography>
                                                <Stack direction="row" spacing={0.75} sx={{mt: 1}} flexWrap="wrap" useFlexGap>
                                                    <Chip size="small" label={action.ownerLane || t('Operations')} sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}/>
                                                    <Chip size="small" label={targetTabLabel(action.targetTab, t)} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}/>
                                                </Stack>
                                            </Box>
                                        ))}
                                    </Stack>
                                ) : (
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('No priority actions are open right now.')}
                                    </Typography>
                                )}
                            </Paper>
                        </Grid>

                        <Grid size={{xs: 12, lg: 7}}>
                            <Paper elevation={0} sx={{p: 2.25, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '0.98rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                                    {t('Queue Pressure By Module')}
                                </Typography>
                                <Grid container spacing={1.4}>
                                    {queueLanes.length ? queueLanes.map((lane) => (
                                        <Grid size={{xs: 12, md: 6}} key={lane.reconView || lane.label}>
                                            <Paper elevation={0} sx={{p: 1.6, borderRadius: 3, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt, height: '100%'}}>
                                                <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>{lane.label}</Typography>
                                                <Stack direction="row" spacing={0.75} sx={{mt: 0.95}} flexWrap="wrap" useFlexGap>
                                                    <Chip size="small" label={`${lane.openCases || 0} ${t('open')}`} sx={{fontWeight: 700}}/>
                                                    <Chip size="small" label={`${lane.highImpactCases || 0} ${t('high impact')}`} sx={{backgroundColor: '#FEF2F2', color: '#B91C1C', fontWeight: 700}}/>
                                                    <Chip size="small" label={`${lane.overdueCases || 0} ${t('overdue')}`} sx={{backgroundColor: '#FFF7ED', color: '#C2410C', fontWeight: 700}}/>
                                                </Stack>
                                                <Typography sx={{mt: 0.85, fontSize: '0.78rem', color: palette.textMuted}}>
                                                    {lane.ownershipGapCases || 0} {t('ownership gaps')} | {lane.actionDueCases || 0} {t('actions due')}
                                                </Typography>
                                            </Paper>
                                        </Grid>
                                    )) : (
                                        <Grid size={{xs: 12}}>
                                            <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                {t('No queue pressure data is available.')}
                                            </Typography>
                                        </Grid>
                                    )}
                                </Grid>
                            </Paper>
                        </Grid>
                    </Grid>

                    <Grid container spacing={2.2} sx={{mb: 3}}>
                        <Grid size={{xs: 12, xl: 7}}>
                            <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                                <Box sx={{px: 2.5, py: 2}}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                        {t('Integration Health')}
                                    </Typography>
                                    <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                        {t('Proactive monitoring of connector reachability, data freshness, backlog, and case pressure.')}
                                    </Typography>
                                </Box>
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('Module')}</TableCell>
                                                <TableCell>{t('Health')}</TableCell>
                                                <TableCell>{t('Freshness')}</TableCell>
                                                <TableCell>{t('Backlog')}</TableCell>
                                                <TableCell>{t('Case Pressure')}</TableCell>
                                                <TableCell>{t('Recommended Action')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {integrationHealth.length ? integrationHealth.map((module) => (
                                                <TableRow key={module.moduleId} hover>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 800, color: palette.text}}>{module.moduleLabel}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{module.reconView}</Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                            <Chip size="small" label={module.healthStatus || t('Unknown')} sx={{fontWeight: 700, ...bandChipStyles(module.healthStatus, palette)}}/>
                                                            <Chip size="small" label={`${module.healthScore ?? 0}/100`} sx={{backgroundColor: palette.cardBgAlt, color: palette.textMuted}}/>
                                                        </Stack>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Chip size="small" label={module.freshnessStatus || t('Unknown')} sx={{fontWeight: 700, ...bandChipStyles(module.freshnessStatus, palette)}}/>
                                                        <Typography sx={{mt: 0.45, fontSize: '0.75rem', color: palette.textMuted}}>
                                                            {module.freshnessLagMinutes ? `${module.freshnessLagMinutes} ${t('minutes behind')}` : t('No lag signal')}
                                                        </Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{module.backlogCount || 0}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{t('Last success')}: {module.lastSuccessfulSyncAt || '-'}</Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{module.activeCaseCount || 0} {t('active')}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{module.breachedCaseCount || 0} {t('breached')}</Typography>
                                                    </TableCell>
                                                    <TableCell sx={{minWidth: 220}}>
                                                        <Typography sx={{fontSize: '0.8rem', color: palette.text}}>
                                                            {module.recommendedAction || '-'}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={6}>
                                                        <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>
                                                            {t('No integration health data is available.')}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Paper>
                        </Grid>

                        <Grid size={{xs: 12, xl: 5}}>
                            <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden', height: '100%'}}>
                                <Box sx={{px: 2.5, py: 2}}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                        {t('Spreading Incidents')}
                                    </Typography>
                                    <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                        {t('Regional patterns that are spreading and need coordinated action.')}
                                    </Typography>
                                </Box>
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('Cluster')}</TableCell>
                                                <TableCell>{t('Incident')}</TableCell>
                                                <TableCell>{t('Status')}</TableCell>
                                                <TableCell>{t('Impact')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {outbreaks.length ? outbreaks.map((outbreak) => (
                                                <TableRow key={outbreak.outbreakKey} hover>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{outbreak.clusterLabel}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{outbreak.reconView}</Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{outbreak.incidentTitle}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{outbreak.priorityReason || '-'}</Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Chip size="small" label={outbreak.outbreakStatus || t('Localized')} sx={{fontWeight: 700, ...bandChipStyles(outbreak.outbreakStatus, palette)}}/>
                                                        <Typography sx={{mt: 0.45, fontSize: '0.75rem', color: palette.textMuted}}>
                                                            {outbreak.affectedStores || 0} {t('stores')} | {outbreak.openCases || 0} {t('cases')}
                                                        </Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{outbreak.impactScore ?? 0}</Typography>
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                            {formatCurrencyValue(outbreak.businessValue?.valueAtRisk, outbreak.businessValue?.currencyCode)}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={4}>
                                                        <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>
                                                            {t('No outbreaks are open for the selected scope.')}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Paper>
                        </Grid>
                    </Grid>

                    <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                        <Box sx={{px: 2.5, py: 2, display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'center', flexWrap: 'wrap'}}>
                            <Box>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                    {t('Recent Automation')}
                                </Typography>
                                <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                    {t('Latest auto-resolve and queue-suppression activity from the exception automation layer.')}
                                </Typography>
                            </Box>
                            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                <Chip size="small" label={`${summary.autoResolvedLast7Days || 0} ${t('auto resolved / 7d')}`} sx={{backgroundColor: palette.tealChipBg, color: palette.tealChipText, fontWeight: 700}}/>
                                <Chip size="small" label={`${summary.suppressedLast7Days || 0} ${t('suppressed / 7d')}`} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}/>
                                <Chip size="small" label={`${summary.failedDeliveries || 0} ${t('failed deliveries')}`} sx={{backgroundColor: summary.failedDeliveries ? '#FEF2F2' : palette.cardBgAlt, color: summary.failedDeliveries ? '#B91C1C' : palette.textMuted, fontWeight: 700}}/>
                            </Stack>
                        </Box>
                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>{t('When')}</TableCell>
                                        <TableCell>{t('Rule')}</TableCell>
                                        <TableCell>{t('Case')}</TableCell>
                                        <TableCell>{t('Action')}</TableCell>
                                        <TableCell>{t('Result')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {recentAutomation.length ? recentAutomation.map((item) => (
                                        <TableRow key={item.id} hover>
                                            <TableCell>{formatDateTimeValue(item.createdAt)}</TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{item.ruleName || t('Automation')}</Typography>
                                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.reconView || '-'}</Typography>
                                            </TableCell>
                                            <TableCell>{item.transactionKey || '-'}</TableCell>
                                            <TableCell>
                                                <Chip size="small" label={item.actionType || t('Automation')} sx={{fontWeight: 700, ...bandChipStyles(item.actionType === 'AUTO_RESOLVE' ? 'HEALTHY' : 'MEDIUM', palette)}}/>
                                            </TableCell>
                                            <TableCell sx={{minWidth: 240}}>
                                                <Typography sx={{fontSize: '0.8rem', color: palette.text}}>{item.resultStatus || '-'}</Typography>
                                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.resultMessage || '-'}</Typography>
                                            </TableCell>
                                        </TableRow>
                                    )) : (
                                        <TableRow>
                                            <TableCell colSpan={5}>
                                                <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>
                                                    {t('No recent automation activity is available.')}
                                                </Typography>
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </Paper>
                </>
            )}
        </Box>
    )
}
