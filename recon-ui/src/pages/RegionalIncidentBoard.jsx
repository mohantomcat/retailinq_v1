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
import PublicIcon from '@mui/icons-material/Public'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {RECON_VIEW_OPTIONS_WITH_ALL} from '../constants/reconViews'

const MODULE_OPTIONS = RECON_VIEW_OPTIONS_WITH_ALL

const STATUS_OPTIONS = [
    {value: 'DETECTED', label: 'Detected Outbreaks'},
    {value: 'ALL', label: 'All Groups'},
    {value: 'SPREADING', label: 'Spreading'},
    {value: 'EMERGING', label: 'Emerging'},
    {value: 'CONTAINED', label: 'Contained'},
    {value: 'LOCALIZED', label: 'Localized'},
]

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

function formatDateTimeValue(value) {
    if (!value) return '-'
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value.replace('T', ' ') : parsed.toLocaleString()
}

function bandChipStyles(band, palette) {
    switch ((band || '').toUpperCase()) {
        case 'CRITICAL':
        case 'SPREADING':
        case 'UNOWNED':
        case 'OWNERSHIP_GAP':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'HIGH':
        case 'EMERGING':
        case 'NO_NEXT_ACTION':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        case 'ACTION_OVERDUE':
            return {backgroundColor: '#FEF3C7', color: '#B45309'}
        case 'MEDIUM':
        case 'ACTION_DUE_SOON':
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

function SummaryCard({label, value, palette, tone = '#2563EB'}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.7, fontSize: '1.45rem', fontWeight: 800, color: tone}}>{value}</Typography>
        </Paper>
    )
}

export default function RegionalIncidentBoard({palette, t}) {
    const {hasPermission} = useAuth()
    const canView = hasPermission('EXCEPTION_QUEUE_VIEW')
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [data, setData] = useState(null)
    const [selectedKey, setSelectedKey] = useState('')
    const [filters, setFilters] = useState({reconView: '', outbreakStatus: 'DETECTED', search: ''})

    const loadBoard = async (nextFilters = filters) => {
        try {
            setLoading(true)
            setError('')
            const response = await exceptionApi.getRegionalIncidentBoard(nextFilters)
            setData(response)
            setSelectedKey((current) => (response?.outbreaks || []).some((item) => item.outbreakKey === current)
                ? current
                : response?.outbreaks?.[0]?.outbreakKey || '')
        } catch (err) {
            setError(err.message || 'Failed to load regional incident board')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (canView) loadBoard(filters)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canView])

    const outbreaks = useMemo(() => data?.outbreaks || [], [data])
    const selected = useMemo(() => outbreaks.find((item) => item.outbreakKey === selectedKey) || null, [outbreaks, selectedKey])

    if (!canView) return null

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 42, height: 42, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.blueChipBg, color: palette.blueChipText, flexShrink: 0}}>
                        <PublicIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Regional Incident Board')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Spot multi-store outbreaks by cluster so HQ and regional teams can see what is spreading and where the biggest business value is at risk.')}
                        </Typography>
                        <Typography sx={{mt: 0.55, fontSize: '0.8rem', color: palette.blueChipText}}>
                            {t('Cluster grouping currently uses store numbering prefixes and can later switch to true region metadata without changing the workflow.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            <Paper elevation={0} sx={{p: 2.25, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr)) auto'}, gap: 1.5, alignItems: 'end'}}>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Module')}</InputLabel>
                        <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}>
                            {MODULE_OPTIONS.map((option) => <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Outbreak Status')}</InputLabel>
                        <Select value={filters.outbreakStatus} label={t('Outbreak Status')} onChange={(event) => setFilters((current) => ({...current, outbreakStatus: event.target.value}))}>
                            {STATUS_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <TextField size="small" label={t('Search')} value={filters.search} onChange={(event) => setFilters((current) => ({...current, search: event.target.value}))} placeholder={t('Cluster, store, incident, owner')} fullWidth/>
                    <Stack direction={{xs: 'column', lg: 'row'}} spacing={1}>
                        <Button variant="contained" onClick={() => loadBoard(filters)} disabled={loading}>{t('Apply')}</Button>
                        <Button variant="outlined" onClick={() => {
                            const cleared = {reconView: '', outbreakStatus: 'DETECTED', search: ''}
                            setFilters(cleared)
                            loadBoard(cleared)
                        }} disabled={loading}>{t('Clear')}</Button>
                    </Stack>
                </Box>
            </Paper>

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(4, minmax(0, 1fr))', xl: 'repeat(7, minmax(0, 1fr))'}, gap: 1.5, mb: 3}}>
                        <SummaryCard label={t('Regional Groups')} value={data?.summary?.regionalGroups || 0} tone={palette.text} palette={palette}/>
                        <SummaryCard label={t('Detected Outbreaks')} value={data?.summary?.detectedOutbreaks || 0} tone="#DC2626" palette={palette}/>
                        <SummaryCard label={t('Spreading')} value={data?.summary?.spreadingOutbreaks || 0} tone="#D97706" palette={palette}/>
                        <SummaryCard label={t('Clusters Impacted')} value={data?.summary?.impactedClusters || 0} tone="#7C3AED" palette={palette}/>
                        <SummaryCard label={t('Stores Impacted')} value={data?.summary?.impactedStores || 0} tone={palette.tealChipText} palette={palette}/>
                        <SummaryCard label={t('Open Cases')} value={data?.summary?.openCases || 0} tone={palette.blueChipText} palette={palette}/>
                        <SummaryCard label={t('Value At Risk')} value={formatCurrencyValue(data?.summary?.businessValue?.valueAtRisk, data?.summary?.businessValue?.currencyCode)} tone="#D97706" palette={palette}/>
                    </Box>

                    <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden', mb: 3}}>
                        <Box sx={{px: 2.5, py: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap'}}>
                            <Box>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Outbreak Groups')}</Typography>
                                <Typography sx={{mt: 0.35, fontSize: '0.85rem', color: palette.textMuted}}>{t('Each row shows the same incident pattern spreading across stores in one cluster.')}</Typography>
                            </Box>
                            {selected ? <Button variant="outlined" onClick={() => setSelectedKey('')} sx={{textTransform: 'none'}}>{t('Clear Focus')}</Button> : null}
                        </Box>
                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>{t('Cluster')}</TableCell>
                                        <TableCell>{t('Incident')}</TableCell>
                                        <TableCell>{t('Status')}</TableCell>
                                        <TableCell>{t('Stores')}</TableCell>
                                        <TableCell>{t('Impact')}</TableCell>
                                        <TableCell>{t('Ownership')}</TableCell>
                                        <TableCell>{t('Updated')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {outbreaks.length ? outbreaks.map((outbreak) => (
                                        <TableRow key={outbreak.outbreakKey} hover selected={selectedKey === outbreak.outbreakKey} onClick={() => setSelectedKey(outbreak.outbreakKey)} sx={{cursor: 'pointer'}}>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 800, color: palette.text}}>{outbreak.clusterLabel}</Typography>
                                                <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{outbreak.reconView}</Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{outbreak.incidentTitle}</Typography>
                                                <Typography sx={{mt: 0.35, fontSize: '0.75rem', color: palette.textMuted}}>{outbreak.priorityReason || '-'}</Typography>
                                                {outbreak.businessValue?.summary ? <Typography sx={{mt: 0.35, fontSize: '0.75rem', color: palette.blueChipText}}>{outbreak.businessValue.summary}</Typography> : null}
                                            </TableCell>
                                            <TableCell>
                                                <Chip size="small" label={outbreak.outbreakStatus || t('Localized')} sx={{fontWeight: 800, ...bandChipStyles(outbreak.outbreakStatus, palette)}}/>
                                                <Typography sx={{mt: 0.5, fontSize: '0.74rem', color: palette.textMuted}}>
                                                    {outbreak.outbreakDetected ? t('Outbreak detected') : t('Single-store signal')}
                                                </Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{outbreak.affectedStores || 0} {t('stores')}</Typography>
                                                <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{outbreak.impactedTransactions || 0} {t('transactions')} | {outbreak.openCases || 0} {t('cases')}</Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>{outbreak.impactScore ?? 0}</Typography>
                                                <Stack direction="row" spacing={0.75} sx={{mt: 0.6}} flexWrap="wrap" useFlexGap>
                                                    <Chip size="small" label={outbreak.impactBand || t('Low')} sx={{fontWeight: 700, ...bandChipStyles(outbreak.impactBand, palette)}}/>
                                                    {outbreak.businessValue?.valueAtRisk ? <Chip size="small" label={formatCurrencyValue(outbreak.businessValue.valueAtRisk, outbreak.businessValue.currencyCode)} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText}}/> : null}
                                                </Stack>
                                            </TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{outbreak.ownerSummary || '-'}</Typography>
                                                <Typography sx={{mt: 0.35, fontSize: '0.74rem', color: palette.textMuted}}>{t('Next')}: {outbreak.nextAction || t('Not set')}</Typography>
                                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{t('Ownership Gaps')}: {outbreak.ownershipGapCases || 0}</Typography>
                                            </TableCell>
                                            <TableCell>{outbreak.latestUpdatedAt || '-'}</TableCell>
                                        </TableRow>
                                    )) : (
                                        <TableRow><TableCell colSpan={7}><Typography sx={{py: 2, fontSize: '0.88rem', color: palette.textMuted}}>{t('No regional incident groups are available for the selected filters.')}</Typography></TableCell></TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </Paper>

                    {selected ? (
                        <Paper elevation={0} sx={{borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                            <Box sx={{px: 2.5, py: 2}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                    {selected.clusterLabel} | {selected.reconView} | {selected.incidentTitle}
                                </Typography>
                                <Typography sx={{mt: 0.35, fontSize: '0.84rem', color: palette.textMuted}}>
                                    {selected.incidentSummary || '-'}
                                </Typography>
                                <Stack direction="row" spacing={0.75} sx={{mt: 1}} flexWrap="wrap" useFlexGap>
                                    <Chip size="small" label={selected.outbreakStatus} sx={{fontWeight: 800, ...bandChipStyles(selected.outbreakStatus, palette)}}/>
                                    <Chip size="small" label={`${t('Stores')}: ${selected.affectedStores || 0}`} sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}/>
                                    <Chip size="small" label={`${t('Cases')}: ${selected.openCases || 0}`} sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}/>
                                    <Chip size="small" label={`${t('Value At Risk')}: ${formatCurrencyValue(selected.businessValue?.valueAtRisk, selected.businessValue?.currencyCode)}`} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText}}/>
                                </Stack>
                            </Box>
                            <TableContainer>
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('Store')}</TableCell>
                                            <TableCell>{t('Impact')}</TableCell>
                                            <TableCell>{t('Cases')}</TableCell>
                                            <TableCell>{t('Ownership')}</TableCell>
                                            <TableCell>{t('Next Action')}</TableCell>
                                            <TableCell>{t('Updated')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {(selected.storeSignals || []).map((signal) => (
                                            <TableRow key={`${signal.incidentKey || signal.storeId}`}>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{signal.storeId || '-'}</Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{signal.incidentTitle || '-'}</Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>{signal.impactScore ?? 0}</Typography>
                                                    <Stack direction="row" spacing={0.75} sx={{mt: 0.6}} flexWrap="wrap" useFlexGap>
                                                        <Chip size="small" label={signal.impactBand || t('Low')} sx={{fontWeight: 700, ...bandChipStyles(signal.impactBand, palette)}}/>
                                                        {signal.businessValue?.valueAtRisk ? <Chip size="small" label={formatCurrencyValue(signal.businessValue.valueAtRisk, signal.businessValue.currencyCode)} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText}}/> : null}
                                                    </Stack>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{signal.openCaseCount || 0} {t('cases')}</Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{signal.impactedTransactions || 0} {t('transactions')}</Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>{signal.ownerSummary || '-'}</Typography>
                                                    <Chip size="small" label={ownershipLabel(signal.ownershipStatus, t)} sx={{mt: 0.65, fontWeight: 700, ...bandChipStyles(signal.ownershipStatus, palette)}}/>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.8rem', color: palette.text}}>{signal.nextAction || '-'}</Typography>
                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{t('Due')}: {formatDateTimeValue(signal.nextActionDueAt)}</Typography>
                                                </TableCell>
                                                <TableCell>{signal.latestUpdatedAt || '-'}</TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Paper>
                    ) : null}
                </>
            )}
        </Box>
    )
}
