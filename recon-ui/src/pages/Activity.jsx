import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    FormControl,
    FormControlLabel,
    Grid,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    Switch,
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
import {useReconModules} from '../hooks/useReconModules'

const SOURCE_OPTIONS = [
    {value: '', label: 'All Sources'},
    {value: 'OPERATIONS', label: 'Operations'},
    {value: 'CONFIGURATION', label: 'Configurations'},
    {value: 'EXCEPTION', label: 'Exceptions'},
    {value: 'SECURITY', label: 'Security'},
    {value: 'SLA', label: 'SLA'},
    {value: 'COMPLIANCE', label: 'Compliance'},
    {value: 'ALERT', label: 'Alerts'},
]

function SummaryCard({label, value, palette, tone = 'blue'}) {
    const styles = {
        blue: {bg: palette.blueChipBg, color: palette.blueChipText},
        teal: {bg: palette.tealChipBg, color: palette.tealChipText},
        amber: {bg: '#FFF7ED', color: '#C2410C'},
        neutral: {bg: palette.neutralChipBg, color: palette.neutralChipText},
    }[tone]

    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.55rem', fontWeight: 800, color: styles.color}}>{value}</Typography>
        </Paper>
    )
}

function MetricGrid({palette, t, metrics}) {
    return (
        <Grid container spacing={2}>
            {metrics.map((metric) => (
                <Grid key={metric.label} item xs={12} sm={6} md={3}>
                    <SummaryCard label={t(metric.label)} value={metric.value || 0} tone={metric.tone} palette={palette}/>
                </Grid>
            ))}
        </Grid>
    )
}

export default function Activity({palette, t}) {
    const {moduleOptions} = useReconModules()
    const [data, setData] = useState(null)
    const [retentionCenter, setRetentionCenter] = useState(null)
    const [soxReport, setSoxReport] = useState(null)
    const [loading, setLoading] = useState(true)
    const [busyAction, setBusyAction] = useState('')
    const [error, setError] = useState('')
    const [message, setMessage] = useState('')
    const [filters, setFilters] = useState({
        sourceType: '',
        moduleKey: '',
        actor: '',
        fromDate: '',
        toDate: '',
        includeArchived: true,
    })
    const [retentionForm, setRetentionForm] = useState({
        policyName: '',
        minimumRetentionDays: 2555,
        archiveAfterDays: 90,
        legalHold: false,
        defaultExportFormat: 'CSV',
        notes: '',
    })
    const MODULE_OPTIONS = useMemo(
        () => [
            {value: '', label: 'All Modules'},
            ...moduleOptions,
            {value: 'CONFIGURATIONS', label: 'Configurations'},
            {value: 'SECURITY', label: 'Security'},
            {value: 'AUDIT', label: 'Audit'},
        ],
        [moduleOptions]
    )

    const loadAuditWorkspace = async (nextFilters = filters) => {
        try {
            setLoading(true)
            setError('')
            const [activityData, retentionData, soxData] = await Promise.all([
                activityApi.getActivity({...nextFilters, limit: 250}),
                activityApi.getRetentionCenter(),
                activityApi.getSoxReport({
                    moduleKey: nextFilters.moduleKey,
                    fromDate: nextFilters.fromDate,
                    toDate: nextFilters.toDate,
                }),
            ])
            setData(activityData)
            setRetentionCenter(retentionData)
            setSoxReport(soxData)
            if (retentionData?.policy) {
                setRetentionForm({
                    policyName: retentionData.policy.policyName || '',
                    minimumRetentionDays: retentionData.policy.minimumRetentionDays || 2555,
                    archiveAfterDays: retentionData.policy.archiveAfterDays || 90,
                    legalHold: !!retentionData.policy.legalHold,
                    defaultExportFormat: retentionData.policy.defaultExportFormat || 'CSV',
                    notes: retentionData.policy.notes || '',
                })
            }
        } catch (err) {
            setError(err.message || 'Failed to load audit workspace')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadAuditWorkspace(filters)
    }, [])

    const handleFilterChange = (key, value) => {
        setFilters((current) => ({...current, [key]: value}))
    }

    const handleRetentionChange = (key, value) => {
        setRetentionForm((current) => ({...current, [key]: value}))
    }

    const handleSaveRetentionPolicy = async () => {
        try {
            setBusyAction('save-policy')
            setError('')
            setMessage('')
            await activityApi.saveRetentionPolicy(retentionForm)
            setMessage('Audit retention policy saved')
            await loadAuditWorkspace(filters)
        } catch (err) {
            setError(err.message || 'Failed to save retention policy')
        } finally {
            setBusyAction('')
        }
    }

    const handleRunArchive = async () => {
        try {
            setBusyAction('archive')
            setError('')
            setMessage('')
            const result = await activityApi.runArchive({
                archiveReason: 'MANUAL_COMPLIANCE_ARCHIVE',
                exportFormat: retentionForm.defaultExportFormat || 'CSV',
            })
            setMessage(result?.archivedCount
                ? `Archived ${result.archivedCount} audit records`
                : 'No audit records were eligible for archive')
            await loadAuditWorkspace(filters)
        } catch (err) {
            setError(err.message || 'Failed to run archive')
        } finally {
            setBusyAction('')
        }
    }

    const handleExport = async (format) => {
        try {
            setBusyAction(`export-${format}`)
            setError('')
            setMessage('')
            const filename = await activityApi.exportActivity({
                moduleKey: filters.moduleKey,
                sourceType: filters.sourceType,
                actor: filters.actor,
                fromDate: filters.fromDate,
                toDate: filters.toDate,
                includeArchived: filters.includeArchived,
                format,
            })
            setMessage(`Audit export downloaded: ${filename}`)
            await loadAuditWorkspace(filters)
        } catch (err) {
            setError(err.message || 'Failed to export audit evidence')
        } finally {
            setBusyAction('')
        }
    }

    const summary = data?.summary || {}
    const records = useMemo(() => data?.records || [], [data])
    const controls = useMemo(() => soxReport?.controls || [], [soxReport])
    const recentEvidence = useMemo(() => soxReport?.recentEvidence || [], [soxReport])

    const activityMetrics = [
        {label: 'Total Records', value: summary.totalRecords, tone: 'blue'},
        {label: 'Operations', value: summary.operationsCount, tone: 'teal'},
        {label: 'Configurations', value: summary.configurationCount, tone: 'amber'},
        {label: 'Exceptions', value: summary.exceptionCount, tone: 'neutral'},
        {label: 'Security', value: summary.securityCount, tone: 'blue'},
        {label: 'Compliance', value: summary.complianceCount, tone: 'teal'},
        {label: 'SLA', value: summary.slaCount, tone: 'amber'},
        {label: 'Archived', value: summary.archivedCount, tone: 'neutral'},
    ]

    const soxMetrics = [
        {label: 'Auditable Actions', value: soxReport?.summary?.totalAuditableActions, tone: 'blue'},
        {label: 'Security Changes', value: soxReport?.summary?.securityAdminChanges, tone: 'teal'},
        {label: 'Config Changes', value: soxReport?.summary?.configurationChanges, tone: 'amber'},
        {label: 'Ops Interventions', value: soxReport?.summary?.operationalInterventions, tone: 'neutral'},
        {label: 'Approval Decisions', value: soxReport?.summary?.approvalDecisions, tone: 'blue'},
        {label: 'Exports Generated', value: soxReport?.summary?.exportsGenerated, tone: 'teal'},
    ]

    const compactFilterFieldSx = {
        minWidth: {xs: '100%', sm: 180},
        flex: {lg: '0 0 auto'},
    }

    const dateFilterFieldSx = {
        minWidth: {xs: '100%', sm: 190},
        flex: {lg: '0 0 auto'},
        '& .MuiInputBase-input': {
            minWidth: 140,
        },
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#EEF4FF', color: '#4A79D8', flexShrink: 0}}>
                        <AssessmentIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Audit & Compliance')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 920}}>
                            {t('Manage immutable audit evidence, retention policy, archive/export packages, and SOX-ready control reporting from one compliance workspace.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}
            {message ? <Alert severity="success" sx={{mb: 2}}>{message}</Alert> : null}

            {loading ? <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box> : (
                <>
                    <MetricGrid palette={palette} t={t} metrics={activityMetrics}/>

                    <Paper elevation={0} sx={{p: 2.5, mt: 3, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Stack direction={{xs: 'column', lg: 'row'}} spacing={1.5} useFlexGap sx={{flexWrap: 'wrap', alignItems: {lg: 'center'}}}>
                            <FormControl size="small" sx={compactFilterFieldSx}>
                                <InputLabel>{t('Source')}</InputLabel>
                                <Select value={filters.sourceType} label={t('Source')} onChange={(event) => handleFilterChange('sourceType', event.target.value)}>
                                    {SOURCE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <FormControl size="small" sx={{...compactFilterFieldSx, minWidth: {xs: '100%', sm: 220}}}>
                                <InputLabel>{t('Module')}</InputLabel>
                                <Select value={filters.moduleKey} label={t('Module')} onChange={(event) => handleFilterChange('moduleKey', event.target.value)}>
                                    {MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <TextField size="small" label={t('Actor')} value={filters.actor} onChange={(event) => handleFilterChange('actor', event.target.value)} sx={compactFilterFieldSx}/>
                            <TextField size="small" label={t('From Date')} type="date" value={filters.fromDate} InputLabelProps={{shrink: true}} onChange={(event) => handleFilterChange('fromDate', event.target.value)} sx={dateFilterFieldSx}/>
                            <TextField size="small" label={t('To Date')} type="date" value={filters.toDate} InputLabelProps={{shrink: true}} onChange={(event) => handleFilterChange('toDate', event.target.value)} sx={dateFilterFieldSx}/>
                            <FormControlLabel
                                sx={{ml: {lg: 0.5}}}
                                control={<Switch checked={filters.includeArchived} onChange={(event) => handleFilterChange('includeArchived', event.target.checked)}/>}
                                label={t('Include Archived')}
                            />
                            <Stack direction="row" spacing={1}>
                                <Button variant="contained" onClick={() => loadAuditWorkspace(filters)}>{t('Apply')}</Button>
                                <Button variant="outlined" onClick={() => {
                                    const cleared = {sourceType: '', moduleKey: '', actor: '', fromDate: '', toDate: '', includeArchived: true}
                                    setFilters(cleared)
                                    loadAuditWorkspace(cleared)
                                }}>{t('Clear')}</Button>
                            </Stack>
                        </Stack>
                    </Paper>

                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} xl={7}>
                            <Paper elevation={0} sx={{p: 2.5, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                    {t('Retention & Archive Policy')}
                                </Typography>
                                <Grid container spacing={1.5}>
                                    <Grid item xs={12} md={6}>
                                        <TextField size="small" fullWidth label={t('Policy Name')} value={retentionForm.policyName} onChange={(event) => handleRetentionChange('policyName', event.target.value)}/>
                                    </Grid>
                                    <Grid item xs={12} md={3}>
                                        <TextField size="small" fullWidth type="number" label={t('Min Retention Days')} value={retentionForm.minimumRetentionDays} onChange={(event) => handleRetentionChange('minimumRetentionDays', Number(event.target.value || 0))}/>
                                    </Grid>
                                    <Grid item xs={12} md={3}>
                                        <TextField size="small" fullWidth type="number" label={t('Archive After Days')} value={retentionForm.archiveAfterDays} onChange={(event) => handleRetentionChange('archiveAfterDays', Number(event.target.value || 0))}/>
                                    </Grid>
                                    <Grid item xs={12} md={4}>
                                        <FormControl size="small" fullWidth>
                                            <InputLabel>{t('Default Export')}</InputLabel>
                                            <Select value={retentionForm.defaultExportFormat} label={t('Default Export')} onChange={(event) => handleRetentionChange('defaultExportFormat', event.target.value)}>
                                                <MenuItem value="CSV">CSV</MenuItem>
                                                <MenuItem value="JSON">JSON</MenuItem>
                                            </Select>
                                        </FormControl>
                                    </Grid>
                                    <Grid item xs={12} md={8}>
                                        <TextField size="small" fullWidth label={t('Notes')} value={retentionForm.notes} onChange={(event) => handleRetentionChange('notes', event.target.value)}/>
                                    </Grid>
                                    <Grid item xs={12}>
                                        <FormControlLabel control={<Switch checked={retentionForm.legalHold} onChange={(event) => handleRetentionChange('legalHold', event.target.checked)}/>} label={t('Legal Hold Active')}/>
                                    </Grid>
                                </Grid>
                                <Stack direction={{xs: 'column', sm: 'row'}} spacing={1.2} sx={{mt: 2}}>
                                    <Button variant="contained" onClick={handleSaveRetentionPolicy} disabled={busyAction === 'save-policy'}>
                                        {busyAction === 'save-policy' ? t('Saving...') : t('Save Policy')}
                                    </Button>
                                    <Button variant="outlined" onClick={handleRunArchive} disabled={busyAction === 'archive'}>
                                        {busyAction === 'archive' ? t('Archiving...') : t('Run Archive')}
                                    </Button>
                                    <Button variant="outlined" onClick={() => handleExport('CSV')} disabled={busyAction === 'export-CSV'}>
                                        {busyAction === 'export-CSV' ? t('Exporting...') : t('Export CSV')}
                                    </Button>
                                    <Button variant="outlined" onClick={() => handleExport('JSON')} disabled={busyAction === 'export-JSON'}>
                                        {busyAction === 'export-JSON' ? t('Exporting...') : t('Export JSON')}
                                    </Button>
                                </Stack>
                            </Paper>
                        </Grid>

                        <Grid item xs={12} xl={5}>
                            <Paper elevation={0} sx={{p: 2.5, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                    {t('Archive Readiness')}
                                </Typography>
                                <Grid container spacing={1.2} sx={{mb: 2}}>
                                    <Grid item xs={6}><SummaryCard label={t('Live Entries')} value={retentionCenter?.liveEntries || 0} tone="blue" palette={palette}/></Grid>
                                    <Grid item xs={6}><SummaryCard label={t('Archived Entries')} value={retentionCenter?.archivedEntries || 0} tone="teal" palette={palette}/></Grid>
                                    <Grid item xs={6}><SummaryCard label={t('Eligible For Archive')} value={retentionCenter?.eligibleForArchive || 0} tone="amber" palette={palette}/></Grid>
                                    <Grid item xs={6}><SummaryCard label={t('Last Archive')} value={retentionCenter?.lastArchiveAt ? t('Ready') : t('Pending')} tone="neutral" palette={palette}/></Grid>
                                </Grid>
                                <Typography sx={{fontSize: '0.8rem', color: palette.textMuted, mb: 1}}>
                                    {t('Last archive at')}: {retentionCenter?.lastArchiveAt || '-'}
                                </Typography>
                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>{t('When')}</TableCell>
                                                <TableCell>{t('Reason')}</TableCell>
                                                <TableCell>{t('Entries')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {(retentionCenter?.recentBatches || []).length > 0 ? retentionCenter.recentBatches.slice(0, 5).map((batch) => (
                                                <TableRow key={batch.id}>
                                                    <TableCell sx={{whiteSpace: 'nowrap'}}>{batch.createdAt || '-'}</TableCell>
                                                    <TableCell>{batch.archiveReason}</TableCell>
                                                    <TableCell>{batch.entryCount}</TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={3}>
                                                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                            {t('No archive packages created yet.')}
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

                    <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                            {t('SOX Control Evidence')}
                        </Typography>
                        <MetricGrid palette={palette} t={t} metrics={soxMetrics}/>
                        <Box sx={{mt: 2.5}}>
                            <TableContainer>
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('Control')}</TableCell>
                                            <TableCell>{t('Owner')}</TableCell>
                                            <TableCell>{t('Status')}</TableCell>
                                            <TableCell>{t('Evidence Count')}</TableCell>
                                            <TableCell>{t('Last Evidence')}</TableCell>
                                            <TableCell>{t('Narrative')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {controls.map((control) => (
                                            <TableRow key={control.controlId}>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{control.controlId}</Typography>
                                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{control.controlTitle}</Typography>
                                                </TableCell>
                                                <TableCell>{control.controlOwner}</TableCell>
                                                <TableCell>{control.status}</TableCell>
                                                <TableCell>{control.evidenceCount}</TableCell>
                                                <TableCell sx={{whiteSpace: 'nowrap'}}>{control.lastEvidenceAt || '-'}</TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>{control.narrative}</Typography>
                                                    {control.sampleReference ? <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{control.sampleReference}</Typography> : null}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Box>
                    </Paper>

                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} lg={8}>
                            <Paper elevation={0} sx={{p: 2.5, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                    {t('Immutable Audit Feed')}
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
                                                <TableCell>{t('Control')}</TableCell>
                                                <TableCell>{t('Integrity')}</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {records.length > 0 ? records.map((record, index) => (
                                                <TableRow key={`${record.eventHash || 'hash'}-${index}`}>
                                                    <TableCell sx={{whiteSpace: 'nowrap'}}>{record.eventTimestamp ? new Date(record.eventTimestamp).toLocaleString() : '-'}</TableCell>
                                                    <TableCell>{record.sourceType}</TableCell>
                                                    <TableCell>{record.moduleKey}</TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{record.title}</Typography>
                                                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{record.actionType}</Typography>
                                                    </TableCell>
                                                    <TableCell>{record.actor || '-'}</TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.82rem', color: palette.text}}>{record.summary || '-'}</Typography>
                                                        {record.reason ? <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{record.reason}</Typography> : null}
                                                        {record.referenceKey ? <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{record.referenceKey}</Typography> : null}
                                                    </TableCell>
                                                    <TableCell>{record.controlFamily || '-'}</TableCell>
                                                    <TableCell>
                                                        <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.text}}>
                                                            {record.archived ? t('Archived') : t('Live')}
                                                        </Typography>
                                                        <Typography sx={{fontSize: '0.72rem', color: palette.textMuted}}>
                                                            {(record.eventHash || '').slice(0, 12) || '-'}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )) : (
                                                <TableRow>
                                                    <TableCell colSpan={8}>
                                                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                            {t('No immutable audit records found for the selected filters.')}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Paper>
                        </Grid>

                        <Grid item xs={12} lg={4}>
                            <Paper elevation={0} sx={{p: 2.5, height: '100%', borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                    {t('Recent SOX Evidence')}
                                </Typography>
                                <Stack spacing={1.2}>
                                    {recentEvidence.length > 0 ? recentEvidence.map((item, index) => (
                                        <Paper key={`${item.eventHash || item.referenceKey || 'e'}-${index}`} elevation={0} sx={{p: 1.5, borderRadius: 2.5, border: `1px solid ${palette.border}`, backgroundColor: palette.cardBgAlt}}>
                                            <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>{item.title}</Typography>
                                            <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{item.sourceType} | {item.moduleKey}</Typography>
                                            <Typography sx={{mt: 0.6, fontSize: '0.78rem', color: palette.text}}>{item.summary || '-'}</Typography>
                                            <Typography sx={{mt: 0.4, fontSize: '0.74rem', color: palette.textMuted}}>
                                                {(item.eventTimestamp ? new Date(item.eventTimestamp).toLocaleString() : '-')} | {item.actor || '-'}
                                            </Typography>
                                        </Paper>
                                    )) : (
                                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                            {t('No recent control evidence available.')}
                                        </Typography>
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
