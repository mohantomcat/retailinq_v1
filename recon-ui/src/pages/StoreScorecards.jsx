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
import StorefrontIcon from '@mui/icons-material/Storefront'
import dayjs from 'dayjs'
import {reconApi} from '../services/reconApi'
import {useAuth} from '../context/AuthContext'
import {useReconModules} from '../hooks/useReconModules'

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

function SummaryStat({label, value, palette, accent}) {
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
            <Typography sx={{mt: 0.7, fontSize: '1.45rem', fontWeight: 800, color: accent}}>
                {value}
            </Typography>
        </Paper>
    )
}

export default function StoreScorecards({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptions: MODULE_OPTIONS} = useReconModules()
    const defaultFromDate = useMemo(() => dayjs().subtract(29, 'day').format('YYYY-MM-DD'), [])
    const defaultToDate = useMemo(() => dayjs().format('YYYY-MM-DD'), [])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [data, setData] = useState(null)
    const [selectedReconView, setSelectedReconView] = useState('ALL')
    const [fromDate, setFromDate] = useState(defaultFromDate)
    const [toDate, setToDate] = useState(defaultToDate)
    const [searchText, setSearchText] = useState('')

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
            setError(err.message || 'Failed to load store scorecards')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadData(moduleOptions.length === 1 ? moduleOptions[0].value : 'ALL', defaultFromDate, defaultToDate)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const filteredStores = useMemo(() => {
        const query = searchText.trim().toLowerCase()
        return (data?.storeScorecards || []).filter((item) => {
            if (!query) {
                return true
            }
            return (item.label || '').toLowerCase().includes(query) || (item.key || '').toLowerCase().includes(query)
        })
    }, [data, searchText])

    const summary = useMemo(() => {
        const stores = filteredStores
        const critical = stores.filter((item) => item.healthBand === 'CRITICAL').length
        const healthy = stores.filter((item) => item.healthBand === 'HEALTHY' || item.healthBand === 'EXCELLENT').length
        const breaches = stores.reduce((sum, item) => sum + (item.breachedExceptions || 0), 0)
        const averageScore = stores.length
            ? Math.round(stores.reduce((sum, item) => sum + (item.healthScore || 0), 0) / stores.length)
            : 0
        return {
            stores: stores.length,
            critical,
            healthy,
            breaches,
            averageScore,
        }
    }, [filteredStores])

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
                        <StorefrontIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>
                            {t('Store Scorecards')}
                        </Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 940}}>
                            {t('Rank stores by reconciliation health, open exception pressure, duplicate exposure, and SLA risk.')}
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

                    <TextField
                        label={t('Search Store')}
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
                        placeholder={t('Store id or name')}
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
                                setSearchText('')
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
                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: {xs: '1fr', md: 'repeat(5, minmax(0, 1fr))'},
                            gap: 1.5,
                            mb: 3,
                        }}
                    >
                        <SummaryStat label={t('Stores')} value={summary.stores} accent={palette.text} palette={palette}/>
                        <SummaryStat label={t('Average Health Score')} value={summary.averageScore} accent={palette.blueChipText} palette={palette}/>
                        <SummaryStat label={t('Healthy Stores')} value={summary.healthy} accent={palette.tealChipText} palette={palette}/>
                        <SummaryStat label={t('Critical Stores')} value={summary.critical} accent="#DC2626" palette={palette}/>
                        <SummaryStat label={t('Breached Exceptions')} value={summary.breaches} accent="#C2410C" palette={palette}/>
                    </Box>

                    <Paper
                        elevation={0}
                        sx={{
                            borderRadius: '22px',
                            border: `1px solid ${palette.border}`,
                            backgroundColor: palette.cardBg,
                            overflow: 'hidden',
                        }}
                    >
                        <Box sx={{px: 2.5, py: 2}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                {t('Store Health Ranking')}
                            </Typography>
                            <Typography sx={{mt: 0.35, fontSize: '0.85rem', color: palette.textMuted}}>
                                {t('Stores are ranked by health score first, then by exception volume.')}
                            </Typography>
                        </Box>

                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>{t('Store')}</TableCell>
                                        <TableCell>{t('Health')}</TableCell>
                                        <TableCell>{t('Total')}</TableCell>
                                        <TableCell>{t('Match Rate')}</TableCell>
                                        <TableCell>{t('Exceptions')}</TableCell>
                                        <TableCell>{t('Missing')}</TableCell>
                                        <TableCell>{t('Duplicates')}</TableCell>
                                        <TableCell>{t('Open Cases')}</TableCell>
                                        <TableCell>{t('Breached')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {filteredStores.length ? filteredStores.map((item) => {
                                        const band = healthStyles(palette, item.healthBand)
                                        return (
                                            <TableRow key={item.key}>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>
                                                        {item.label}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Stack direction="row" spacing={1} alignItems="center">
                                                        <Chip
                                                            label={item.healthBand}
                                                            size="small"
                                                            sx={{
                                                                backgroundColor: band.bg,
                                                                color: band.color,
                                                                fontWeight: 800,
                                                            }}
                                                        />
                                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                                            {item.healthScore}
                                                        </Typography>
                                                    </Stack>
                                                </TableCell>
                                                <TableCell>{item.totalTransactions}</TableCell>
                                                <TableCell>{formatPercent(item.matchRate)}</TableCell>
                                                <TableCell>{item.exceptionCount}</TableCell>
                                                <TableCell>{item.missingCount}</TableCell>
                                                <TableCell>{item.duplicateCount}</TableCell>
                                                <TableCell>{item.activeExceptions}</TableCell>
                                                <TableCell>{item.breachedExceptions}</TableCell>
                                            </TableRow>
                                        )
                                    }) : (
                                        <TableRow>
                                            <TableCell colSpan={9}>
                                                <Typography sx={{py: 2, fontSize: '0.88rem', color: palette.textMuted}}>
                                                    {t('No store scorecard data is available for the selected filters.')}
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
