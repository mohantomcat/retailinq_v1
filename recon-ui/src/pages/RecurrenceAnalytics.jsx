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
import AutorenewRoundedIcon from '@mui/icons-material/AutorenewRounded'
import {CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts'
import dayjs from 'dayjs'
import {useAuth} from '../context/AuthContext'
import {exceptionApi} from '../services/exceptionApi'
import {reconApi} from '../services/reconApi'

const MODULE_OPTIONS = [
    {value: 'XSTORE_SIM', label: 'Xstore vs SIM', permission: 'RECON_XSTORE_SIM'},
    {value: 'XSTORE_SIOCS', label: 'Xstore vs SIOCS', permission: 'RECON_XSTORE_SIOCS'},
    {value: 'XSTORE_XOCS', label: 'Xstore vs XOCS', permission: 'RECON_XSTORE_XOCS'},
]

function formatPercent(value) {
    return `${Number(value || 0).toFixed(2)}%`
}

function formatMoney(businessValue) {
    const amount = Number(businessValue?.valueAtRisk || 0)
    if (!amount) {
        return 'n/a'
    }
    const prefix = businessValue?.currencyCode ? `${businessValue.currencyCode} ` : ''
    return `${prefix}${amount.toLocaleString(undefined, {maximumFractionDigits: 2})}`
}

function bandStyles(band) {
    switch ((band || '').toUpperCase()) {
        case 'CRITICAL':
            return {bg: '#FEF2F2', text: '#B91C1C'}
        case 'HIGH':
            return {bg: '#FFF7ED', text: '#C2410C'}
        case 'MEDIUM':
            return {bg: '#EFF6FF', text: '#1D4ED8'}
        default:
            return {bg: '#F1F5F9', text: '#475569'}
    }
}

function SummaryCard({label, value, supporting, accent, palette}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '18px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.7, fontSize: '1.55rem', fontWeight: 800, color: accent}}>{value}</Typography>
            <Typography sx={{mt: 0.5, fontSize: '0.76rem', color: palette.textMuted}}>{supporting}</Typography>
        </Paper>
    )
}

function TrendCard({title, data, dataKey, color, palette, formatter, t}) {
    return (
        <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, height: '100%'}}>
            <Typography sx={{fontSize: '0.96rem', fontWeight: 800, color: palette.text, mb: 1.5}}>{title}</Typography>
            {data?.length ? (
                <Box sx={{height: 240}}>
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={data} margin={{top: 8, right: 16, left: 0, bottom: 8}}>
                            <CartesianGrid stroke={palette.borderSoft} strokeDasharray="3 3"/>
                            <XAxis dataKey="label" stroke={palette.textMuted} tickLine={false} axisLine={false}/>
                            <YAxis stroke={palette.textMuted} tickLine={false} axisLine={false} width={60} tickFormatter={formatter}/>
                            <Tooltip
                                formatter={(value) => formatter ? formatter(value) : value}
                                contentStyle={{borderRadius: 14, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, color: palette.text}}
                            />
                            <Line type="monotone" dataKey={dataKey} stroke={color} strokeWidth={3} dot={{r: 3}} activeDot={{r: 5}} connectNulls/>
                        </LineChart>
                    </ResponsiveContainer>
                </Box>
            ) : (
                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                    {t('No repeat trend data available for this range.')}
                </Typography>
            )}
        </Paper>
    )
}

function BreakdownCard({title, rows, palette, t}) {
    return (
        <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, height: '100%'}}>
            <Typography sx={{fontSize: '0.96rem', fontWeight: 800, color: palette.text, mb: 1.5}}>{title}</Typography>
            {rows?.length ? (
                <Stack spacing={1.1}>
                    {rows.map((row) => (
                        <Box key={row.key} sx={{display: 'flex', justifyContent: 'space-between', gap: 1.2, p: 1.2, borderRadius: 2.5, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                            <Box sx={{minWidth: 0}}>
                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{row.label}</Typography>
                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                    {row.repeatCases} repeats | {row.recurringIncidentCount} patterns | {formatMoney({valueAtRisk: row.valueAtRisk})}
                                </Typography>
                            </Box>
                            <Chip size="small" label={formatPercent(row.repeatShare)} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700, flexShrink: 0}}/>
                        </Box>
                    ))}
                </Stack>
            ) : (
                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                    {t('No repeat activity found for this dimension.')}
                </Typography>
            )}
        </Paper>
    )
}

export default function RecurrenceAnalytics({palette, t}) {
    const {hasPermission} = useAuth()
    const defaultFromDate = useMemo(() => dayjs().subtract(59, 'day').format('YYYY-MM-DD'), [])
    const defaultToDate = useMemo(() => dayjs().format('YYYY-MM-DD'), [])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [data, setData] = useState(null)
    const [stores, setStores] = useState([])
    const [filters, setFilters] = useState({
        reconView: 'ALL',
        storeId: '',
        fromBusinessDate: defaultFromDate,
        toBusinessDate: defaultToDate,
    })

    const allowedModules = useMemo(() => MODULE_OPTIONS.filter((option) => hasPermission(option.permission)), [hasPermission])
    const moduleOptions = useMemo(() => allowedModules.length > 1 ? [{value: 'ALL', label: t('All Modules')}, ...allowedModules] : allowedModules, [allowedModules, t])

    useEffect(() => {
        if (moduleOptions.length === 1 && filters.reconView !== moduleOptions[0].value) {
            setFilters((current) => ({...current, reconView: moduleOptions[0].value}))
        }
    }, [filters.reconView, moduleOptions])

    const loadStores = async (reconView) => {
        try {
            const results = await reconApi.getStores(reconView === 'ALL' ? null : reconView)
            setStores(results || [])
        } catch {
            setStores([])
        }
    }

    const loadData = async (nextFilters = filters) => {
        try {
            setLoading(true)
            setError('')
            const response = await exceptionApi.getRecurrenceAnalytics({
                reconView: nextFilters.reconView === 'ALL' ? '' : nextFilters.reconView,
                storeId: nextFilters.storeId,
                fromBusinessDate: nextFilters.fromBusinessDate,
                toBusinessDate: nextFilters.toBusinessDate,
            })
            setData(response)
        } catch (err) {
            setError(err.message || 'Failed to load recurrence analytics')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        const initialModule = moduleOptions.length === 1 ? moduleOptions[0].value : 'ALL'
        const initialFilters = {
            reconView: initialModule,
            storeId: '',
            fromBusinessDate: defaultFromDate,
            toBusinessDate: defaultToDate,
        }
        setFilters(initialFilters)
        loadStores(initialModule)
        loadData(initialFilters)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        loadStores(filters.reconView)
        setFilters((current) => ({...current, storeId: ''}))
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filters.reconView])

    const summary = data?.summary || {}
    const trend = data?.trend || []
    const recurringIncidents = data?.recurringIncidents || []
    const preventionOpportunities = data?.preventionOpportunities || []

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 42, height: 42, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.tealChipBg, color: palette.tealChipText, flexShrink: 0}}>
                        <AutorenewRoundedIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>
                            {t('Recurrence & Prevention Analytics')}
                        </Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Track which store incident patterns keep coming back, how much repeat work they create, and where retailers should standardize fixes before the same operational issue hits again.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            <Paper elevation={0} sx={{p: 2, mb: 3, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(4, minmax(0, 1fr)) auto'}, gap: 1.5, alignItems: 'end'}}>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Module')}</InputLabel>
                        <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}>
                            {moduleOptions.map((option) => (
                                <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Store')}</InputLabel>
                        <Select value={filters.storeId} label={t('Store')} onChange={(event) => setFilters((current) => ({...current, storeId: event.target.value}))}>
                            <MenuItem value="">{t('All Stores')}</MenuItem>
                            {stores.map((store) => (
                                <MenuItem key={store} value={store}>{store}</MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    <TextField label={t('From Business Date')} type="date" value={filters.fromBusinessDate} onChange={(event) => setFilters((current) => ({...current, fromBusinessDate: event.target.value}))} InputLabelProps={{shrink: true}} fullWidth/>
                    <TextField label={t('To Business Date')} type="date" value={filters.toBusinessDate} onChange={(event) => setFilters((current) => ({...current, toBusinessDate: event.target.value}))} InputLabelProps={{shrink: true}} fullWidth/>

                    <Stack direction={{xs: 'column', lg: 'row'}} spacing={1}>
                        <Button variant="contained" onClick={() => loadData()} disabled={loading}>{t('Apply')}</Button>
                        <Button
                            variant="outlined"
                            onClick={() => {
                                const resetModule = moduleOptions.length === 1 ? moduleOptions[0].value : 'ALL'
                                const cleared = {reconView: resetModule, storeId: '', fromBusinessDate: defaultFromDate, toBusinessDate: defaultToDate}
                                setFilters(cleared)
                                loadStores(resetModule)
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
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}>
                    <CircularProgress/>
                </Box>
            ) : (
                <>
                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(6, minmax(0, 1fr))'}, gap: 1.5, mb: 3}}>
                        <SummaryCard label={t('Repeat Cases')} value={summary.repeatCases || 0} supporting={t('Cases that repeated within 30 days')} accent="#DC2626" palette={palette}/>
                        <SummaryCard label={t('Repeat Rate')} value={formatPercent(summary.repeatCaseRate)} supporting={t('Share of cases that were repeats')} accent="#2563EB" palette={palette}/>
                        <SummaryCard label={t('Returned After Resolve')} value={summary.repeatAfterResolvedCases || 0} supporting={t('Cases that came back after being closed')} accent="#B45309" palette={palette}/>
                        <SummaryCard label={t('7 Day Repeats')} value={summary.repeatWithin7DaysCases || 0} supporting={t('Fast repeats showing immediate instability')} accent="#0F7C86" palette={palette}/>
                        <SummaryCard label={t('Recurring Patterns')} value={summary.recurringIncidentPatterns || 0} supporting={t('Distinct recurring store incident themes')} accent="#7C3AED" palette={palette}/>
                        <SummaryCard label={t('Repeat Value At Risk')} value={formatMoney(summary.repeatBusinessValue)} supporting={t('Business exposure from repeat work')} accent="#15803D" palette={palette}/>
                    </Box>

                    <Grid container spacing={2.2} sx={{mb: 3}}>
                        <Grid item xs={12} lg={6}>
                            <TrendCard title={t('Repeat Operational Load')} data={trend} dataKey="repeatCases" color="#DC2626" palette={palette} formatter={(value) => Number(value || 0)} t={t}/>
                        </Grid>
                        <Grid item xs={12} lg={6}>
                            <TrendCard title={t('Repeat Value At Risk')} data={trend} dataKey="repeatValueAtRisk" color="#15803D" palette={palette} formatter={(value) => Number(value || 0).toLocaleString(undefined, {maximumFractionDigits: 0})} t={t}/>
                        </Grid>
                    </Grid>

                    <Grid container spacing={2.2} sx={{mb: 3}}>
                        <Grid item xs={12} xl={8}>
                            <Paper elevation={0} sx={{p: 2.25, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '0.96rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                                    {t('Top Recurring Incident Patterns')}
                                </Typography>
                                {recurringIncidents.length ? (
                                    <TableContainer>
                                        <Table size="small">
                                            <TableHead>
                                                <TableRow>
                                                    <TableCell>{t('Incident')}</TableCell>
                                                    <TableCell>{t('Repeat Profile')}</TableCell>
                                                    <TableCell>{t('Owner')}</TableCell>
                                                    <TableCell>{t('Value')}</TableCell>
                                                    <TableCell>{t('Last Seen')}</TableCell>
                                                </TableRow>
                                            </TableHead>
                                            <TableBody>
                                                {recurringIncidents.map((incident) => {
                                                    const styles = bandStyles(incident.businessValue?.businessValueBand)
                                                    return (
                                                        <TableRow key={incident.recurrenceKey} hover>
                                                            <TableCell sx={{minWidth: 260}}>
                                                                <Typography sx={{fontSize: '0.88rem', fontWeight: 700, color: palette.text}}>
                                                                    {incident.storeId} | {incident.incidentTitle}
                                                                </Typography>
                                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted, mt: 0.35}}>
                                                                    {incident.reconView} | {incident.incidentSummary}
                                                                </Typography>
                                                                <Stack direction="row" spacing={0.8} sx={{mt: 0.9, flexWrap: 'wrap'}}>
                                                                    <Chip size="small" label={incident.businessValue?.businessValueBand || t('Low Value')} sx={{backgroundColor: styles.bg, color: styles.text, fontWeight: 700}}/>
                                                                    {incident.matchedKnownIssue ? <Chip size="small" label={incident.matchedKnownIssue.title} sx={{backgroundColor: palette.tealChipBg, color: palette.tealChipText, fontWeight: 700}}/> : null}
                                                                </Stack>
                                                            </TableCell>
                                                            <TableCell sx={{minWidth: 190}}>
                                                                <Stack direction="row" spacing={0.7} sx={{flexWrap: 'wrap'}}>
                                                                    <Chip size="small" label={`${incident.repeatCases} / 30d`} sx={{fontWeight: 700}}/>
                                                                    <Chip size="small" label={`${incident.repeatWithin7DaysCases} / 7d`} sx={{fontWeight: 700}}/>
                                                                    {incident.repeatAfterResolvedCases ? <Chip size="small" label={`${incident.repeatAfterResolvedCases} after resolve`} sx={{backgroundColor: '#FEF2F2', color: '#B91C1C', fontWeight: 700}}/> : null}
                                                                </Stack>
                                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted, mt: 0.85}}>
                                                                    {incident.priorityReason}
                                                                </Typography>
                                                            </TableCell>
                                                            <TableCell sx={{minWidth: 150}}>
                                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{incident.ownerTeam}</Typography>
                                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                                    {incident.activeCases} active | {incident.latestCaseStatus}
                                                                </Typography>
                                                            </TableCell>
                                                            <TableCell sx={{minWidth: 130}}>
                                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{formatMoney(incident.businessValue)}</Typography>
                                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                                    {incident.businessValue?.summary || t('No business value detail')}
                                                                </Typography>
                                                            </TableCell>
                                                            <TableCell sx={{minWidth: 120}}>
                                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{incident.latestSeenAt || 'n/a'}</Typography>
                                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                                    {t('First seen')}: {incident.firstSeenAt || 'n/a'}
                                                                </Typography>
                                                            </TableCell>
                                                        </TableRow>
                                                    )
                                                })}
                                            </TableBody>
                                        </Table>
                                    </TableContainer>
                                ) : (
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('No recurring incident patterns found for the selected range.')}
                                    </Typography>
                                )}
                            </Paper>
                        </Grid>
                        <Grid item xs={12} xl={4}>
                            <Paper elevation={0} sx={{p: 2.25, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '0.96rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                                    {t('Prevention Opportunities')}
                                </Typography>
                                {preventionOpportunities.length ? (
                                    <Stack spacing={1.25}>
                                        {preventionOpportunities.map((item) => (
                                            <Box key={item.recurrenceKey} sx={{p: 1.35, borderRadius: 2.75, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 800, color: palette.text}}>{item.title}</Typography>
                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted, mt: 0.45}}>{item.opportunityReason}</Typography>
                                                <Typography sx={{fontSize: '0.8rem', color: palette.text, mt: 0.9}}>{item.recommendedAction}</Typography>
                                                <Stack direction="row" spacing={0.8} sx={{mt: 1, flexWrap: 'wrap'}}>
                                                    <Chip size="small" label={`${item.repeatCases} repeats`} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}/>
                                                    {item.matchedKnownIssue ? (
                                                        <Chip size="small" label={item.matchedKnownIssue.title} sx={{backgroundColor: palette.tealChipBg, color: palette.tealChipText, fontWeight: 700}}/>
                                                    ) : (
                                                        <Chip size="small" label={t('Document guidance')} sx={{backgroundColor: '#FFF7ED', color: '#C2410C', fontWeight: 700}}/>
                                                    )}
                                                </Stack>
                                                <Typography sx={{fontSize: '0.75rem', color: palette.textMuted, mt: 0.9}}>
                                                    {formatMoney(item.businessValue)} | {item.lastSeenAt || 'n/a'}
                                                </Typography>
                                            </Box>
                                        ))}
                                    </Stack>
                                ) : (
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('No prevention opportunities are open for this range.')}
                                    </Typography>
                                )}
                            </Paper>
                        </Grid>
                    </Grid>

                    <Grid container spacing={2.2}>
                        <Grid item xs={12} md={6} xl={2.4}>
                            <BreakdownCard title={t('Top Repeat Stores')} rows={data?.topStores} palette={palette} t={t}/>
                        </Grid>
                        <Grid item xs={12} md={6} xl={2.4}>
                            <BreakdownCard title={t('Top Repeat Modules')} rows={data?.topModules} palette={palette} t={t}/>
                        </Grid>
                        <Grid item xs={12} md={6} xl={2.4}>
                            <BreakdownCard title={t('Top Repeat Reasons')} rows={data?.topReasons} palette={palette} t={t}/>
                        </Grid>
                        <Grid item xs={12} md={6} xl={2.4}>
                            <BreakdownCard title={t('Top Known Issues')} rows={data?.topKnownIssues} palette={palette} t={t}/>
                        </Grid>
                        <Grid item xs={12} md={6} xl={2.4}>
                            <BreakdownCard title={t('Top Owner Teams')} rows={data?.topOwnerTeams} palette={palette} t={t}/>
                        </Grid>
                    </Grid>
                </>
            )}
        </Box>
    )
}
