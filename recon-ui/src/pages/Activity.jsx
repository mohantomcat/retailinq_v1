import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
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
import AssessmentIcon from '@mui/icons-material/Assessment'
import {activityApi} from '../services/activityApi'

const SOURCE_OPTIONS = [
    {value: '', label: 'All Sources'},
    {value: 'OPERATIONS', label: 'Operations'},
    {value: 'CONFIGURATION', label: 'Configurations'},
    {value: 'EXCEPTION', label: 'Exceptions'},
    {value: 'ALERT', label: 'Alerts'},
]

const MODULE_OPTIONS = [
    {value: '', label: 'All Modules'},
    {value: 'XSTORE_SIM', label: 'Xstore vs SIM'},
    {value: 'XSTORE_SIOCS', label: 'Xstore vs SIOCS'},
    {value: 'XSTORE_XOCS', label: 'Xstore vs XOCS'},
    {value: 'CONFIGURATIONS', label: 'Configurations'},
    {value: 'OPERATIONS', label: 'Operations'},
]

function SummaryCard({label, value, palette, tone}) {
    const styles = {
        blue: {bg: palette.blueChipBg, color: palette.blueChipText},
        teal: {bg: palette.tealChipBg, color: palette.tealChipText},
        amber: {bg: '#FFF7ED', color: '#C2410C'},
        neutral: {bg: palette.neutralChipBg, color: palette.neutralChipText},
    }[tone]

    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.7rem', fontWeight: 800, color: styles.color}}>{value}</Typography>
        </Paper>
    )
}

export default function Activity({palette, t}) {
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [filters, setFilters] = useState({
        sourceType: '',
        moduleKey: '',
        actor: '',
        fromDate: '',
        toDate: '',
    })

    const loadActivity = async (nextFilters = filters) => {
        try {
            setLoading(true)
            setError('')
            setData(await activityApi.getActivity({...nextFilters, limit: 250}))
        } catch (err) {
            setError(err.message || 'Failed to load activity feed')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadActivity(filters)
    }, [])

    const handleFilterChange = (key, value) => {
        setFilters((current) => ({...current, [key]: value}))
    }

    const summary = data?.summary || {}
    const records = useMemo(() => data?.records || [], [data])

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#ECFEFF', color: '#0F7C86', flexShrink: 0}}>
                        <AssessmentIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Audit & Activity')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 920}}>
                            {t('Track operational actions, configuration changes, exception work, and alert activity in one enterprise audit feed.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            {loading ? <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box> : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Total Records')} value={summary.totalRecords || 0} tone="blue" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Operations')} value={summary.operationsCount || 0} tone="teal" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Configurations')} value={summary.configurationCount || 0} tone="amber" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Exceptions')} value={summary.exceptionCount || 0} tone="neutral" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Alerts')} value={summary.alertCount || 0} tone="blue" palette={palette}/></Grid>
                    </Grid>

                    <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Stack direction={{xs: 'column', lg: 'row'}} spacing={1.5}>
                            <FormControl size="small" sx={{minWidth: 180}}>
                                <InputLabel>{t('Source')}</InputLabel>
                                <Select value={filters.sourceType} label={t('Source')} onChange={(event) => handleFilterChange('sourceType', event.target.value)}>
                                    {SOURCE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <FormControl size="small" sx={{minWidth: 220}}>
                                <InputLabel>{t('Module')}</InputLabel>
                                <Select value={filters.moduleKey} label={t('Module')} onChange={(event) => handleFilterChange('moduleKey', event.target.value)}>
                                    {MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <TextField
                                size="small"
                                label={t('Actor')}
                                value={filters.actor}
                                onChange={(event) => handleFilterChange('actor', event.target.value)}
                            />
                            <TextField
                                size="small"
                                label={t('From Date')}
                                type="date"
                                value={filters.fromDate}
                                InputLabelProps={{shrink: true}}
                                onChange={(event) => handleFilterChange('fromDate', event.target.value)}
                            />
                            <TextField
                                size="small"
                                label={t('To Date')}
                                type="date"
                                value={filters.toDate}
                                InputLabelProps={{shrink: true}}
                                onChange={(event) => handleFilterChange('toDate', event.target.value)}
                            />
                            <Stack direction="row" spacing={1}>
                                <Box component="button" onClick={() => loadActivity(filters)} style={{border: 'none', cursor: 'pointer', borderRadius: 12, padding: '0 18px', background: '#2563EB', color: '#fff', fontWeight: 700}}>
                                    {t('Apply')}
                                </Box>
                                <Box component="button" onClick={() => {
                                    const cleared = {sourceType: '', moduleKey: '', actor: '', fromDate: '', toDate: ''}
                                    setFilters(cleared)
                                    loadActivity(cleared)
                                }} style={{border: '1px solid #CBD5E1', cursor: 'pointer', borderRadius: 12, padding: '0 18px', background: '#fff', color: '#334155', fontWeight: 700}}>
                                    {t('Clear')}
                                </Box>
                            </Stack>
                        </Stack>
                    </Paper>

                    <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                            {t('Unified Activity Feed')}
                        </Typography>
                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>{t('When')}</TableCell>
                                        <TableCell>{t('Source')}</TableCell>
                                        <TableCell>{t('Module')}</TableCell>
                                        <TableCell>{t('Action')}</TableCell>
                                        <TableCell>{t('Actor')}</TableCell>
                                        <TableCell>{t('Details')}</TableCell>
                                        <TableCell>{t('Status')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {records.length > 0 ? records.map((record, index) => (
                                        <TableRow key={`${record.sourceType}-${record.referenceKey || 'ref'}-${record.eventTimestamp}-${index}`}>
                                            <TableCell sx={{whiteSpace: 'nowrap'}}>{record.eventTimestamp ? new Date(record.eventTimestamp).toLocaleString() : '-'}</TableCell>
                                            <TableCell>{record.sourceType}</TableCell>
                                            <TableCell>{record.moduleKey}</TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{record.title}</Typography>
                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{record.actionType}</Typography>
                                            </TableCell>
                                            <TableCell>{record.actor || '-'}</TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.82rem', color: palette.text}}>{record.summary}</Typography>
                                                {record.referenceKey ? <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{record.referenceKey}</Typography> : null}
                                            </TableCell>
                                            <TableCell>{record.status || '-'}</TableCell>
                                        </TableRow>
                                    )) : (
                                        <TableRow>
                                            <TableCell colSpan={7}>
                                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                    {t('No activity records found for the selected filters.')}
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
