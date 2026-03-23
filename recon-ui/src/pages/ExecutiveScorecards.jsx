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
import AssessmentIcon from '@mui/icons-material/Assessment'
import dayjs from 'dayjs'
import {reconApi} from '../services/reconApi'
import {useAuth} from '../context/AuthContext'
import {RECON_VIEW_OPTIONS} from '../constants/reconViews'

const MODULE_OPTIONS = RECON_VIEW_OPTIONS

function formatPercent(value) {
    return `${Number(value || 0).toFixed(2)}%`
}

function healthStyles(palette, band) {
    switch ((band || '').toUpperCase()) {
        case 'EXCELLENT':
            return {bg: '#ECFDF5', color: '#15803D'}
        case 'HEALTHY':
            return {bg: palette.tealChipBg, color: palette.tealChipText}
        case 'WATCH':
            return {bg: '#FFF7ED', color: '#C2410C'}
        default:
            return {bg: '#FEF2F2', color: '#DC2626'}
    }
}

function SummaryMetric({label, value, accent, palette}) {
    return (
        <Paper
            elevation={0}
            sx={{
                p: 2,
                height: '100%',
                borderRadius: '20px',
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.cardBg,
            }}
        >
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>
                {label}
            </Typography>
            <Typography sx={{mt: 0.8, fontSize: '1.6rem', fontWeight: 800, color: accent}}>
                {value}
            </Typography>
        </Paper>
    )
}

function ModuleCard({item, palette, t}) {
    const band = healthStyles(palette, item.healthBand)

    return (
        <Paper
            elevation={0}
            sx={{
                p: 2.25,
                height: '100%',
                borderRadius: '22px',
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.cardBg,
            }}
        >
            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start', mb: 1.5}}>
                <Box>
                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                        {item.label}
                    </Typography>
                    <Typography sx={{mt: 0.4, fontSize: '0.82rem', color: palette.textMuted}}>
                        {t('Health score')} {item.healthScore}
                    </Typography>
                </Box>
                <Chip
                    label={item.healthBand}
                    size="small"
                    sx={{
                        backgroundColor: band.bg,
                        color: band.color,
                        fontWeight: 800,
                    }}
                />
            </Box>

            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
                    gap: 1.25,
                }}
            >
                {[
                    {label: t('Total Transactions'), value: item.totalTransactions},
                    {label: t('Match Rate'), value: formatPercent(item.matchRate)},
                    {label: t('Exception Rate'), value: formatPercent(item.exceptionRate)},
                    {label: t('Duplicate Rate'), value: formatPercent(item.duplicateRate)},
                    {label: t('Open Exceptions'), value: item.activeExceptions},
                    {label: t('SLA Breach Rate'), value: formatPercent(item.slaBreachRate)},
                ].map((metric) => (
                    <Box
                        key={metric.label}
                        sx={{
                            p: 1.25,
                            borderRadius: 2.5,
                            border: `1px solid ${palette.borderSoft}`,
                            backgroundColor: palette.cardBgAlt,
                        }}
                    >
                        <Typography sx={{fontSize: '0.74rem', fontWeight: 700, color: palette.textMuted}}>
                            {metric.label}
                        </Typography>
                        <Typography sx={{mt: 0.45, fontSize: '1.05rem', fontWeight: 800, color: palette.text}}>
                            {metric.value}
                        </Typography>
                    </Box>
                ))}
            </Box>
        </Paper>
    )
}

export default function ExecutiveScorecards({palette, t}) {
    const {hasPermission} = useAuth()
    const defaultFromDate = useMemo(() => dayjs().subtract(29, 'day').format('YYYY-MM-DD'), [])
    const defaultToDate = useMemo(() => dayjs().format('YYYY-MM-DD'), [])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [data, setData] = useState(null)
    const [selectedReconView, setSelectedReconView] = useState('ALL')
    const [fromDate, setFromDate] = useState(defaultFromDate)
    const [toDate, setToDate] = useState(defaultToDate)

    const allowedModules = useMemo(
        () => MODULE_OPTIONS.filter((option) => hasPermission(option.permission)),
        [hasPermission]
    )

    const moduleOptions = useMemo(() => {
        if (allowedModules.length > 1) {
            return [{value: 'ALL', label: t('All Modules')}, ...allowedModules]
        }
        return allowedModules
    }, [allowedModules, t])

    useEffect(() => {
        if (moduleOptions.length === 1 && selectedReconView !== moduleOptions[0].value) {
            setSelectedReconView(moduleOptions[0].value)
        }
    }, [moduleOptions, selectedReconView])

    const loadData = async (nextReconView = selectedReconView, nextFromDate = fromDate, nextToDate = toDate) => {
        try {
            setLoading(true)
            setError('')
            const response = await reconApi.getScorecards({
                reconView: nextReconView === 'ALL' ? null : nextReconView,
                fromBusinessDate: nextFromDate,
                toBusinessDate: nextToDate,
            })
            setData(response)
        } catch (err) {
            setError(err.message || 'Failed to load executive scorecards')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadData(moduleOptions.length === 1 ? moduleOptions[0].value : 'ALL', defaultFromDate, defaultToDate)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const executive = data?.executiveSummary
    const executiveBand = healthStyles(palette, executive?.healthBand)

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper
                elevation={0}
                sx={{
                    p: 3,
                    mb: 3,
                    borderRadius: '24px',
                    border: `1px solid ${palette.border}`,
                    background: palette.heroBg,
                }}
            >
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box
                        sx={{
                            mt: 0.3,
                            width: 42,
                            height: 42,
                            borderRadius: 3,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            backgroundColor: palette.blueChipBg,
                            color: palette.blueChipText,
                            flexShrink: 0,
                        }}
                    >
                        <AssessmentIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>
                            {t('Executive Scorecards')}
                        </Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 940}}>
                            {t('Track enterprise reconciliation health with a concise executive summary and a ranked view of each reconciliation lane.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            <Paper
                elevation={0}
                sx={{
                    p: 2,
                    mb: 3,
                    borderRadius: '20px',
                    border: `1px solid ${palette.border}`,
                    backgroundColor: palette.cardBg,
                }}
            >
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {xs: '1fr', md: 'repeat(4, minmax(0, 1fr)) auto'},
                        gap: 1.5,
                        alignItems: 'end',
                    }}
                >
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Module')}</InputLabel>
                        <Select
                            value={selectedReconView}
                            label={t('Module')}
                            onChange={(event) => setSelectedReconView(event.target.value)}
                        >
                            {moduleOptions.map((option) => (
                                <MenuItem key={option.value} value={option.value}>
                                    {option.label}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    <TextField
                        label={t('From Business Date')}
                        type="date"
                        value={fromDate}
                        onChange={(event) => setFromDate(event.target.value)}
                        InputLabelProps={{shrink: true}}
                        fullWidth
                    />

                    <TextField
                        label={t('To Business Date')}
                        type="date"
                        value={toDate}
                        onChange={(event) => setToDate(event.target.value)}
                        InputLabelProps={{shrink: true}}
                        fullWidth
                    />

                    <Stack direction={{xs: 'column', lg: 'row'}} spacing={1}>
                        <Button
                            variant="contained"
                            onClick={() => loadData()}
                            disabled={loading}
                        >
                            {t('Apply')}
                        </Button>
                        <Button
                            variant="outlined"
                            onClick={() => {
                                const resetModule = moduleOptions.length === 1 ? moduleOptions[0].value : 'ALL'
                                setSelectedReconView(resetModule)
                                setFromDate(defaultFromDate)
                                setToDate(defaultToDate)
                                loadData(resetModule, defaultFromDate, defaultToDate)
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
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} lg={4}>
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 2.5,
                                    height: '100%',
                                    borderRadius: '22px',
                                    border: `1px solid ${palette.border}`,
                                    backgroundColor: palette.cardBg,
                                }}
                            >
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start'}}>
                                    <Box>
                                        <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.textMuted}}>
                                            {t('Enterprise Health')}
                                        </Typography>
                                        <Typography sx={{mt: 0.75, fontSize: '2.6rem', lineHeight: 1, fontWeight: 900, color: palette.text}}>
                                            {executive?.healthScore ?? 0}
                                        </Typography>
                                        <Typography sx={{mt: 0.75, fontSize: '0.9rem', color: palette.textMuted}}>
                                            {executive?.label || t('Executive Health')}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        label={executive?.healthBand || 'WATCH'}
                                        sx={{
                                            backgroundColor: executiveBand.bg,
                                            color: executiveBand.color,
                                            fontWeight: 800,
                                        }}
                                    />
                                </Box>
                            </Paper>
                        </Grid>

                        <Grid item xs={12} md={6} lg={2}>
                            <SummaryMetric label={t('Total Transactions')} value={executive?.totalTransactions ?? 0} accent={palette.text} palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={6} lg={2}>
                            <SummaryMetric label={t('Match Rate')} value={formatPercent(executive?.matchRate)} accent={palette.tealChipText} palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={6} lg={2}>
                            <SummaryMetric label={t('Exception Rate')} value={formatPercent(executive?.exceptionRate)} accent="#DC2626" palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={6} lg={2}>
                            <SummaryMetric label={t('Duplicate Rate')} value={formatPercent(executive?.duplicateRate)} accent="#7C3AED" palette={palette}/>
                        </Grid>
                    </Grid>

                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} md={6} xl={3}>
                            <SummaryMetric label={t('Missing Count')} value={executive?.missingCount ?? 0} accent="#D97706" palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={6} xl={3}>
                            <SummaryMetric label={t('Active Exceptions')} value={executive?.activeExceptions ?? 0} accent={palette.blueChipText} palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={6} xl={3}>
                            <SummaryMetric label={t('Breached Exceptions')} value={executive?.breachedExceptions ?? 0} accent="#DC2626" palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={6} xl={3}>
                            <SummaryMetric label={t('SLA Breach Rate')} value={formatPercent(executive?.slaBreachRate)} accent="#C2410C" palette={palette}/>
                        </Grid>
                    </Grid>

                    <Box sx={{mb: 1.5}}>
                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                            {t('Module Scorecards')}
                        </Typography>
                        <Typography sx={{mt: 0.35, fontSize: '0.86rem', color: palette.textMuted}}>
                            {t('Compare each reconciliation lane by health, exception pressure, and SLA risk.')}
                        </Typography>
                    </Box>

                    <Grid container spacing={2}>
                        {(data?.moduleScorecards || []).length ? (
                            data.moduleScorecards.map((item) => (
                                <Grid item xs={12} md={6} xl={4} key={item.key}>
                                    <ModuleCard item={item} palette={palette} t={t}/>
                                </Grid>
                            ))
                        ) : (
                            <Grid item xs={12}>
                                <Paper
                                    elevation={0}
                                    sx={{
                                        p: 3,
                                        borderRadius: '20px',
                                        border: `1px solid ${palette.border}`,
                                        backgroundColor: palette.cardBg,
                                    }}
                                >
                                    <Typography sx={{fontSize: '0.9rem', color: palette.textMuted}}>
                                        {t('No module scorecard data is available for the selected filters.')}
                                    </Typography>
                                </Paper>
                            </Grid>
                        )}
                    </Grid>
                </>
            )}
        </Box>
    )
}
