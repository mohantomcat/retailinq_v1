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
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from '@mui/material'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import dayjs from 'dayjs'
import {useAuth} from '../context/AuthContext'
import {exceptionApi} from '../services/exceptionApi'
import {reconApi} from '../services/reconApi'
import {RECON_VIEW_OPTIONS} from '../constants/reconViews'

const MODULE_OPTIONS = RECON_VIEW_OPTIONS

function formatPercent(value) {
    return `${Number(value || 0).toFixed(2)}%`
}

function SummaryCard({label, value, accent, palette}) {
    return (
        <Paper
            elevation={0}
            sx={{
                p: 2,
                borderRadius: '18px',
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.cardBg,
            }}
        >
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>
                {label}
            </Typography>
            <Typography sx={{mt: 0.7, fontSize: '1.55rem', fontWeight: 800, color: accent}}>
                {value}
            </Typography>
        </Paper>
    )
}

function TrendChip({current, previous}) {
    const delta = Number(current || 0) - Number(previous || 0)
    const positive = delta > 0
    const neutral = delta === 0

    return (
        <Chip
            size="small"
            label={
                neutral
                    ? 'Stable'
                    : positive
                        ? `+${delta} vs prev 7d`
                        : `${delta} vs prev 7d`
            }
            sx={{
                backgroundColor: neutral ? '#F1F5F9' : positive ? '#FEF2F2' : '#ECFDF5',
                color: neutral ? '#475569' : positive ? '#DC2626' : '#15803D',
                fontWeight: 700,
            }}
        />
    )
}

function CompactBreakdownList({title, rows, palette, t}) {
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
            <Typography sx={{fontSize: '0.96rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                {title}
            </Typography>
            {(rows || []).length ? (
                <Stack spacing={1.1}>
                    {rows.map((row) => (
                        <Box
                            key={row.key}
                            sx={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                gap: 1.5,
                                p: 1.2,
                                borderRadius: 2.5,
                                border: `1px solid ${palette.borderSoft}`,
                                backgroundColor: palette.cardBgAlt,
                            }}
                        >
                            <Box>
                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>
                                    {row.label}
                                </Typography>
                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                    {formatPercent(row.percent)}
                                </Typography>
                            </Box>
                            <Chip
                                label={row.count}
                                size="small"
                                sx={{
                                    backgroundColor: palette.blueChipBg,
                                    color: palette.blueChipText,
                                    fontWeight: 700,
                                }}
                            />
                        </Box>
                    ))}
                </Stack>
            ) : (
                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                    {t('No analytics data available.')}
                </Typography>
            )}
        </Paper>
    )
}

export default function RootCauseAnalytics({palette, t}) {
    const {hasPermission} = useAuth()
    const defaultFromDate = useMemo(() => dayjs().subtract(29, 'day').format('YYYY-MM-DD'), [])
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
            const response = await exceptionApi.getRootCauseAnalytics({
                reconView: nextFilters.reconView === 'ALL' ? '' : nextFilters.reconView,
                storeId: nextFilters.storeId,
                fromBusinessDate: nextFilters.fromBusinessDate,
                toBusinessDate: nextFilters.toBusinessDate,
            })
            setData(response)
        } catch (err) {
            setError(err.message || 'Failed to load root cause analytics')
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
                            backgroundColor: palette.tealChipBg,
                            color: palette.tealChipText,
                            flexShrink: 0,
                        }}
                    >
                        <FactCheckOutlinedIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>
                            {t('Root Cause Analytics')}
                        </Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 960}}>
                            {t('Identify the recurring reasons driving reconciliation exceptions, monitor their recent momentum, and isolate the stores or modules causing the most operational drag.')}
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
                            value={filters.reconView}
                            label={t('Module')}
                            onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}
                        >
                            {moduleOptions.map((option) => (
                                <MenuItem key={option.value} value={option.value}>
                                    {option.label}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Store')}</InputLabel>
                        <Select
                            value={filters.storeId}
                            label={t('Store')}
                            onChange={(event) => setFilters((current) => ({...current, storeId: event.target.value}))}
                        >
                            <MenuItem value="">{t('All Stores')}</MenuItem>
                            {stores.map((store) => (
                                <MenuItem key={store} value={store}>{store}</MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    <TextField
                        label={t('From Business Date')}
                        type="date"
                        value={filters.fromBusinessDate}
                        onChange={(event) => setFilters((current) => ({...current, fromBusinessDate: event.target.value}))}
                        InputLabelProps={{shrink: true}}
                        fullWidth
                    />

                    <TextField
                        label={t('To Business Date')}
                        type="date"
                        value={filters.toBusinessDate}
                        onChange={(event) => setFilters((current) => ({...current, toBusinessDate: event.target.value}))}
                        InputLabelProps={{shrink: true}}
                        fullWidth
                    />

                    <Stack direction={{xs: 'column', lg: 'row'}} spacing={1}>
                        <Button variant="contained" onClick={() => loadData()} disabled={loading}>
                            {t('Apply')}
                        </Button>
                        <Button
                            variant="outlined"
                            onClick={() => {
                                const resetModule = moduleOptions.length === 1 ? moduleOptions[0].value : 'ALL'
                                const cleared = {
                                    reconView: resetModule,
                                    storeId: '',
                                    fromBusinessDate: defaultFromDate,
                                    toBusinessDate: defaultToDate,
                                }
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
                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: {xs: '1fr', md: 'repeat(5, minmax(0, 1fr))'},
                            gap: 1.5,
                            mb: 3,
                        }}
                    >
                        <SummaryCard label={t('Total Cases')} value={data?.summary?.totalCases || 0} accent={palette.text} palette={palette}/>
                        <SummaryCard label={t('Classified')} value={formatPercent(data?.summary?.classificationRate)} accent={palette.tealChipText} palette={palette}/>
                        <SummaryCard label={t('Unclassified')} value={data?.summary?.unclassifiedCases || 0} accent="#D97706" palette={palette}/>
                        <SummaryCard label={t('Active Cases')} value={data?.summary?.activeCases || 0} accent={palette.blueChipText} palette={palette}/>
                        <SummaryCard label={t('Breached')} value={data?.summary?.breachedCases || 0} accent="#DC2626" palette={palette}/>
                    </Box>

                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: {xs: '1fr', lg: '1.35fr 0.95fr'},
                            gap: 2,
                            mb: 3,
                        }}
                    >
                        <Paper
                            elevation={0}
                            sx={{
                                p: 2.25,
                                borderRadius: '22px',
                                border: `1px solid ${palette.border}`,
                                backgroundColor: palette.cardBg,
                            }}
                        >
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 1.5}}>
                                {t('Top Recurring Causes')}
                            </Typography>
                            <TableContainer>
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('Reason')}</TableCell>
                                            <TableCell>{t('Share')}</TableCell>
                                            <TableCell>{t('Active')}</TableCell>
                                            <TableCell>{t('Breached')}</TableCell>
                                            <TableCell>{t('Recent Momentum')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {(data?.topReasons || []).length ? data.topReasons.map((row) => (
                                            <TableRow key={row.key}>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>
                                                        {row.label}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {row.count} {t('cases')}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>{formatPercent(row.percent)}</TableCell>
                                                <TableCell>{row.activeCases}</TableCell>
                                                <TableCell>{row.breachedCases}</TableCell>
                                                <TableCell>
                                                    <Stack spacing={0.75}>
                                                        <TrendChip current={row.last7DaysCount} previous={row.previous7DaysCount}/>
                                                        <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                                            {t('Last 7d')}: {row.last7DaysCount} | {t('Prev 7d')}: {row.previous7DaysCount}
                                                        </Typography>
                                                    </Stack>
                                                </TableCell>
                                            </TableRow>
                                        )) : (
                                            <TableRow>
                                                <TableCell colSpan={5}>
                                                    <Typography sx={{py: 2, fontSize: '0.86rem', color: palette.textMuted}}>
                                                        {t('No root cause data is available for the selected filters.')}
                                                    </Typography>
                                                </TableCell>
                                            </TableRow>
                                        )}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Paper>

                        <Paper
                            elevation={0}
                            sx={{
                                p: 2.25,
                                borderRadius: '22px',
                                border: `1px solid ${palette.border}`,
                                backgroundColor: palette.cardBg,
                            }}
                        >
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 1.25}}>
                                {t('Pareto of Causes')}
                            </Typography>
                            <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, mb: 1.4}}>
                                {t('Use the cumulative curve to see which causes drive the majority of exceptions.')}
                            </Typography>
                            {(data?.paretoReasons || []).length ? (
                                <Stack spacing={1.15}>
                                    {data.paretoReasons.map((row) => (
                                        <Box key={row.key}>
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, mb: 0.4}}>
                                                <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>
                                                    {row.label}
                                                </Typography>
                                                <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                    {formatPercent(row.cumulativePercent)}
                                                </Typography>
                                            </Box>
                                            <Box
                                                sx={{
                                                    width: '100%',
                                                    height: 10,
                                                    borderRadius: 999,
                                                    backgroundColor: palette.cardBgAlt,
                                                    overflow: 'hidden',
                                                }}
                                            >
                                                <Box
                                                    sx={{
                                                        width: `${Math.min(row.cumulativePercent || 0, 100)}%`,
                                                        height: '100%',
                                                        borderRadius: 999,
                                                        background: 'linear-gradient(90deg, #0F7C86, #2563EB)',
                                                    }}
                                                />
                                            </Box>
                                            <Typography sx={{mt: 0.35, fontSize: '0.74rem', color: palette.textMuted}}>
                                                {row.count} {t('cases')} | {formatPercent(row.percent)} {t('share')}
                                            </Typography>
                                        </Box>
                                    ))}
                                </Stack>
                            ) : (
                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                    {t('No Pareto data available.')}
                                </Typography>
                            )}
                        </Paper>
                    </Box>

                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: {xs: '1fr', xl: 'repeat(3, minmax(0, 1fr))'},
                            gap: 2,
                        }}
                    >
                        <CompactBreakdownList title={t('Top Categories')} rows={data?.topCategories} palette={palette} t={t}/>
                        <CompactBreakdownList title={t('Top Stores')} rows={data?.topStores} palette={palette} t={t}/>
                        <CompactBreakdownList title={t('Top Registers')} rows={data?.topRegisters} palette={palette} t={t}/>
                        <CompactBreakdownList title={t('Module Distribution')} rows={data?.topModules} palette={palette} t={t}/>
                        <CompactBreakdownList title={t('Severity Mix')} rows={data?.topSeverities} palette={palette} t={t}/>
                        <Paper
                            elevation={0}
                            sx={{
                                p: 2.25,
                                borderRadius: '22px',
                                border: `1px solid ${palette.border}`,
                                backgroundColor: palette.cardBg,
                            }}
                        >
                            <Typography sx={{fontSize: '0.96rem', fontWeight: 800, color: palette.text, mb: 1.25}}>
                                {t('Leading Signals')}
                            </Typography>
                            <Stack spacing={1.1}>
                                <Box
                                    sx={{
                                        p: 1.4,
                                        borderRadius: 2.5,
                                        border: `1px solid ${palette.borderSoft}`,
                                        backgroundColor: palette.cardBgAlt,
                                    }}
                                >
                                    <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted}}>
                                        {t('Top Reason')}
                                    </Typography>
                                    <Typography sx={{mt: 0.45, fontSize: '0.95rem', fontWeight: 800, color: palette.text}}>
                                        {data?.summary?.topReasonLabel || t('None')}
                                    </Typography>
                                </Box>
                                <Box
                                    sx={{
                                        p: 1.4,
                                        borderRadius: 2.5,
                                        border: `1px solid ${palette.borderSoft}`,
                                        backgroundColor: palette.cardBgAlt,
                                    }}
                                >
                                    <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted}}>
                                        {t('Top Category')}
                                    </Typography>
                                    <Typography sx={{mt: 0.45, fontSize: '0.95rem', fontWeight: 800, color: palette.text}}>
                                        {data?.summary?.topCategoryLabel || t('None')}
                                    </Typography>
                                </Box>
                                <Box
                                    sx={{
                                        p: 1.4,
                                        borderRadius: 2.5,
                                        border: `1px solid ${palette.borderSoft}`,
                                        backgroundColor: palette.cardBgAlt,
                                    }}
                                >
                                    <Typography sx={{fontSize: '0.76rem', fontWeight: 700, color: palette.textMuted}}>
                                        {t('Classification Discipline')}
                                    </Typography>
                                    <Typography sx={{mt: 0.45, fontSize: '0.95rem', fontWeight: 800, color: palette.text}}>
                                        {formatPercent(data?.summary?.classificationRate)}
                                    </Typography>
                                </Box>
                            </Stack>
                        </Paper>
                    </Box>
                </>
            )}
        </Box>
    )
}
