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
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined'
import {slaApi} from '../services/slaApi'
import {useAuth} from '../context/AuthContext'
import {useReconModules} from '../hooks/useReconModules'

const SEVERITY_ORDER = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const WEEK_START_OPTIONS = ['MONDAY', 'SUNDAY', 'SATURDAY']
const BUSINESS_DAY_OPTIONS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

function createOperatingModelDraft(model = null) {
    return {
        timezone: model?.timezone || 'UTC',
        countryCode: model?.countryCode || 'US',
        currencyCode: model?.currencyCode || 'USD',
        localeCode: model?.localeCode || 'en-US',
        dateDisplayFormat: model?.dateDisplayFormat || 'dd-MMM-yyyy',
        weekStartDay: model?.weekStartDay || 'MONDAY',
        businessDays: model?.businessDays?.length ? model.businessDays : [...BUSINESS_DAY_OPTIONS],
        workdayStartTime: model?.workdayStartTime || '06:00',
        workdayEndTime: model?.workdayEndTime || '22:00',
        holidayDatesText: (model?.holidayDates || []).join(', '),
    }
}

function businessDayLabel(day) {
    return day
        .toLowerCase()
        .replace(/^\w/, (char) => char.toUpperCase())
}

function SummaryCard({label, value, palette, accent}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.7rem', fontWeight: 800, color: accent}}>{value}</Typography>
        </Paper>
    )
}

function AgingTable({title, rows, palette, t}) {
    return (
        <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.96rem', fontWeight: 800, color: palette.text, mb: 1.5}}>{title}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Name')}</TableCell>
                            <TableCell>{t('Open')}</TableCell>
                            <TableCell>{t('Breached')}</TableCell>
                            <TableCell>{t('Due Soon')}</TableCell>
                            <TableCell>{t('Avg Age (hrs)')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rows?.length ? rows.map((row) => (
                            <TableRow key={row.key}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>
                                        {row.label}
                                    </Typography>
                                </TableCell>
                                <TableCell>{row.openCases}</TableCell>
                                <TableCell>{row.breachedCases}</TableCell>
                                <TableCell>{row.dueSoonCases}</TableCell>
                                <TableCell>{row.averageAgeHours}</TableCell>
                            </TableRow>
                        )) : (
                            <TableRow>
                                <TableCell colSpan={5}>
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('No aging data available.')}
                                    </Typography>
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

export default function SlaManagement({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptions} = useReconModules()
    const canEdit = hasPermission('SLA_EDIT')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState('')
    const [data, setData] = useState(null)
    const [selectedReconView, setSelectedReconView] = useState('')
    const [drafts, setDrafts] = useState({})
    const [operatingModelDraft, setOperatingModelDraft] = useState(createOperatingModelDraft())
    const availableModuleOptions = useMemo(
        () => moduleOptions.filter((option) => option?.value),
        [moduleOptions]
    )

    const loadData = async () => {
        try {
            setLoading(true)
            setError('')
            const next = await slaApi.getManagement()
            setData(next)
            setOperatingModelDraft(createOperatingModelDraft(next?.operatingModel))
        } catch (err) {
            setError(err.message || 'Failed to load SLA data')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadData()
    }, [])

    useEffect(() => {
        if (!availableModuleOptions.length) {
            if (selectedReconView) {
                setSelectedReconView('')
            }
            return
        }
        if (!availableModuleOptions.some((option) => option.value === selectedReconView)) {
            setSelectedReconView(availableModuleOptions[0].value)
        }
    }, [availableModuleOptions, selectedReconView])

    const filteredRules = useMemo(() => {
        const rules = data?.rules || []
        return rules
            .filter((rule) => !selectedReconView || rule.reconView === selectedReconView)
            .sort((left, right) => SEVERITY_ORDER.indexOf(left.severity) - SEVERITY_ORDER.indexOf(right.severity))
    }, [data, selectedReconView])

    const handleDraftChange = (severity, field, value) => {
        setDrafts((current) => ({
            ...current,
            [severity]: {
                targetMinutes: current[severity]?.targetMinutes ?? filteredRules.find((rule) => rule.severity === severity)?.targetMinutes ?? '',
                description: current[severity]?.description ?? filteredRules.find((rule) => rule.severity === severity)?.description ?? '',
                [field]: value,
            },
        }))
    }

    const resolveDraft = (severity, field, fallback) => {
        return drafts[severity]?.[field] ?? fallback ?? ''
    }

    const handleOperatingModelFieldChange = (field, value) => {
        setOperatingModelDraft((current) => ({...current, [field]: value}))
    }

    const saveOperatingModel = async () => {
        try {
            setSaving(true)
            setError('')
            await slaApi.saveOperatingModel({
                timezone: operatingModelDraft.timezone.trim(),
                countryCode: operatingModelDraft.countryCode.trim().toUpperCase(),
                currencyCode: operatingModelDraft.currencyCode.trim().toUpperCase(),
                localeCode: operatingModelDraft.localeCode.trim(),
                dateDisplayFormat: operatingModelDraft.dateDisplayFormat.trim(),
                weekStartDay: operatingModelDraft.weekStartDay,
                businessDays: operatingModelDraft.businessDays,
                workdayStartTime: operatingModelDraft.workdayStartTime,
                workdayEndTime: operatingModelDraft.workdayEndTime,
                holidayDates: operatingModelDraft.holidayDatesText
                    .split(/[,\n]/)
                    .map((value) => value.trim())
                    .filter(Boolean),
            })
            await loadData()
            setFeedback('Global operating model updated.')
        } catch (err) {
            setError(err.message || 'Failed to save operating model')
        } finally {
            setSaving(false)
        }
    }

    const saveRule = async (severity) => {
        try {
            setSaving(true)
            setError('')
            const existing = filteredRules.find((rule) => rule.severity === severity)
            const targetMinutes = resolveDraft(severity, 'targetMinutes', existing?.targetMinutes)
            const description = resolveDraft(severity, 'description', existing?.description)
            await slaApi.saveRule({
                reconView: selectedReconView,
                severity,
                targetMinutes,
                description,
            })
            await loadData()
            setFeedback(`SLA target updated for ${severity}.`)
        } catch (err) {
            setError(err.message || 'Failed to save SLA rule')
        } finally {
            setSaving(false)
        }
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.blueChipBg, color: palette.blueChipText, flexShrink: 0}}>
                        <TimerOutlinedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('SLA & Aging')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 920}}>
                            {t('Manage target resolution times by module and severity, then monitor breach backlog by assignee, store, and reconciliation lane.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {feedback ? <Alert severity="success" sx={{mb: 2}}>{feedback}</Alert> : null}
            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid size={{xs: 12, md: 6, lg: 2.4}}><SummaryCard label={t('Active Cases')} value={data?.summary?.activeCases || 0} accent={palette.blueChipText} palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, lg: 2.4}}><SummaryCard label={t('Breached')} value={data?.summary?.breachedCases || 0} accent="#DC2626" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, lg: 2.4}}><SummaryCard label={t('Due Soon')} value={data?.summary?.dueSoonCases || 0} accent="#D97706" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, lg: 2.4}}><SummaryCard label={t('Within SLA')} value={data?.summary?.withinSlaCases || 0} accent={palette.tealChipText} palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, lg: 2.4}}><SummaryCard label={t('Breach Rate')} value={`${data?.summary?.breachRate || 0}%`} accent="#7C3AED" palette={palette}/></Grid>
                    </Grid>

                    <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap', mb: 2}}>
                            <Box>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                    {t('Global Operating Model')}
                                </Typography>
                                <Typography sx={{mt: 0.45, fontSize: '0.84rem', color: palette.textMuted, maxWidth: 860}}>
                                    {t('SLA due times and aging now follow the tenant business calendar, local timezone, and local date display pattern. Keep this aligned to how each country or tenant actually operates.')}
                                </Typography>
                            </Box>
                            <Box sx={{display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap'}}>
                                <Chip size="small" label={`${t('Local Time')}: ${data?.operatingModel?.currentLocalTime || '-'}`} sx={{fontWeight: 700, backgroundColor: palette.blueBg, color: palette.blueText}}/>
                                <Chip size="small" label={`${t('Business Date')}: ${data?.operatingModel?.currentBusinessDate || '-'}`} sx={{fontWeight: 700, backgroundColor: palette.tealChipBg, color: palette.tealChipText}}/>
                            </Box>
                        </Box>

                        <Grid container spacing={2}>
                            <Grid size={{xs: 12, md: 4}}>
                                <TextField
                                    size="small"
                                    label={t('Timezone')}
                                    value={operatingModelDraft.timezone}
                                    onChange={(event) => handleOperatingModelFieldChange('timezone', event.target.value)}
                                    disabled={!canEdit || saving}
                                    fullWidth
                                />
                            </Grid>
                            <Grid size={{xs: 12, md: 2}}>
                                <TextField
                                    size="small"
                                    label={t('Country')}
                                    value={operatingModelDraft.countryCode}
                                    onChange={(event) => handleOperatingModelFieldChange('countryCode', event.target.value)}
                                    disabled={!canEdit || saving}
                                    fullWidth
                                />
                            </Grid>
                            <Grid size={{xs: 12, md: 2}}>
                                <TextField
                                    size="small"
                                    label={t('Currency')}
                                    value={operatingModelDraft.currencyCode}
                                    onChange={(event) => handleOperatingModelFieldChange('currencyCode', event.target.value)}
                                    disabled={!canEdit || saving}
                                    fullWidth
                                />
                            </Grid>
                            <Grid size={{xs: 12, md: 2}}>
                                <TextField
                                    size="small"
                                    label={t('Locale')}
                                    value={operatingModelDraft.localeCode}
                                    onChange={(event) => handleOperatingModelFieldChange('localeCode', event.target.value)}
                                    disabled={!canEdit || saving}
                                    fullWidth
                                />
                            </Grid>
                            <Grid size={{xs: 12, md: 2}}>
                                <TextField
                                    size="small"
                                    label={t('Date Pattern')}
                                    value={operatingModelDraft.dateDisplayFormat}
                                    onChange={(event) => handleOperatingModelFieldChange('dateDisplayFormat', event.target.value)}
                                    disabled={!canEdit || saving}
                                    fullWidth
                                />
                            </Grid>
                            <Grid size={{xs: 12, md: 3}}>
                                <FormControl size="small" fullWidth>
                                    <InputLabel>{t('Week Starts')}</InputLabel>
                                    <Select
                                        value={operatingModelDraft.weekStartDay}
                                        label={t('Week Starts')}
                                        onChange={(event) => handleOperatingModelFieldChange('weekStartDay', event.target.value)}
                                        disabled={!canEdit || saving}
                                    >
                                        {WEEK_START_OPTIONS.map((option) => (
                                            <MenuItem key={option} value={option}>{businessDayLabel(option)}</MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>
                            </Grid>
                            <Grid size={{xs: 12, md: 5}}>
                                <FormControl size="small" fullWidth>
                                    <InputLabel>{t('Business Days')}</InputLabel>
                                    <Select
                                        multiple
                                        value={operatingModelDraft.businessDays}
                                        label={t('Business Days')}
                                        onChange={(event) => handleOperatingModelFieldChange(
                                            'businessDays',
                                            Array.isArray(event.target.value)
                                                ? event.target.value
                                                : String(event.target.value).split(',')
                                        )}
                                        disabled={!canEdit || saving}
                                        renderValue={(selected) => selected.map((value) => businessDayLabel(value)).join(', ')}
                                    >
                                        {BUSINESS_DAY_OPTIONS.map((option) => (
                                            <MenuItem key={option} value={option}>{businessDayLabel(option)}</MenuItem>
                                        ))}
                                    </Select>
                                </FormControl>
                            </Grid>
                            <Grid size={{xs: 12, md: 2}}>
                                <TextField
                                    size="small"
                                    label={t('Start Time')}
                                    type="time"
                                    value={operatingModelDraft.workdayStartTime}
                                    onChange={(event) => handleOperatingModelFieldChange('workdayStartTime', event.target.value)}
                                    disabled={!canEdit || saving}
                                    fullWidth
                                    InputLabelProps={{shrink: true}}
                                />
                            </Grid>
                            <Grid size={{xs: 12, md: 2}}>
                                <TextField
                                    size="small"
                                    label={t('End Time')}
                                    type="time"
                                    value={operatingModelDraft.workdayEndTime}
                                    onChange={(event) => handleOperatingModelFieldChange('workdayEndTime', event.target.value)}
                                    disabled={!canEdit || saving}
                                    fullWidth
                                    InputLabelProps={{shrink: true}}
                                />
                            </Grid>
                            <Grid size={{xs: 12}}>
                                <TextField
                                    label={t('Holiday Dates')}
                                    value={operatingModelDraft.holidayDatesText}
                                    onChange={(event) => handleOperatingModelFieldChange('holidayDatesText', event.target.value)}
                                    disabled={!canEdit || saving}
                                    fullWidth
                                    multiline
                                    minRows={2}
                                    helperText={t('Enter ISO dates separated by commas or new lines, for example 2026-12-25, 2027-01-01.')}
                                />
                            </Grid>
                        </Grid>

                        <Box sx={{mt: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap'}}>
                            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                {data?.operatingModel
                                    ? `${t('Business Calendar')}: ${data.operatingModel.businessDaysLabel || '-'} | ${t('Hours')}: ${data.operatingModel.workdayHoursLabel || '-'} | ${t('Holidays')}: ${data.operatingModel.holidayCount || 0}`
                                    : t('No operating model is configured yet.')}
                            </Typography>
                            <Button variant="contained" onClick={saveOperatingModel} disabled={!canEdit || saving}>
                                {t('Save Operating Model')}
                            </Button>
                        </Box>
                    </Paper>

                    <Paper elevation={0} sx={{p: 2, mb: 3, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <FormControl size="small" sx={{minWidth: 280}}>
                            <InputLabel>{t('Module')}</InputLabel>
                            <Select
                                value={selectedReconView}
                                label={t('Module')}
                                onChange={(event) => setSelectedReconView(event.target.value)}
                                disabled={!availableModuleOptions.length}
                            >
                                {availableModuleOptions.map((option) => (
                                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        {!availableModuleOptions.length ? (
                            <Typography sx={{mt: 0.75, fontSize: '0.78rem', color: palette.textMuted}}>
                                {t('No reconciliation modules are assigned to this role.')}
                            </Typography>
                        ) : null}
                    </Paper>

                    <Grid container spacing={2}>
                        <Grid size={{xs: 12, lg: 5}}>
                            <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                    {t('Resolution Targets')}
                                </Typography>
                                <Stack spacing={1.5}>
                                    {SEVERITY_ORDER.map((severity) => {
                                        const rule = filteredRules.find((item) => item.severity === severity)
                                        return (
                                            <Paper key={severity} elevation={0} sx={{p: 1.5, borderRadius: '18px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                                <Stack spacing={1.2}>
                                                    <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1}}>
                                                        <Chip size="small" label={severity} sx={{fontWeight: 700}}/>
                                                        <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                            {t('Target applies from case creation')}
                                                        </Typography>
                                                    </Box>
                                                    <TextField
                                                        type="number"
                                                        label={t('Target Minutes')}
                                                        value={resolveDraft(severity, 'targetMinutes', rule?.targetMinutes)}
                                                        onChange={(event) => handleDraftChange(severity, 'targetMinutes', event.target.value)}
                                                        disabled={!canEdit || saving}
                                                    />
                                                    <TextField
                                                        label={t('Description')}
                                                        value={resolveDraft(severity, 'description', rule?.description)}
                                                        onChange={(event) => handleDraftChange(severity, 'description', event.target.value)}
                                                        multiline
                                                        minRows={2}
                                                        disabled={!canEdit || saving}
                                                    />
                                                    <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1}}>
                                                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                            {rule?.updatedBy ? `${t('Last updated by')} ${rule.updatedBy}` : t('Using seeded default')}
                                                        </Typography>
                                                        <Button
                                                            variant="contained"
                                                            onClick={() => saveRule(severity)}
                                                            disabled={!canEdit || saving || !selectedReconView || !resolveDraft(severity, 'targetMinutes', rule?.targetMinutes)}
                                                        >
                                                            {t('Save')}
                                                        </Button>
                                                    </Box>
                                                </Stack>
                                            </Paper>
                                        )
                                    })}
                                </Stack>
                            </Paper>
                        </Grid>

                        <Grid size={{xs: 12, lg: 7}}>
                            <Grid container spacing={2}>
                                <Grid size={{xs: 12}}>
                                    <AgingTable title={t('Aging by Assignee')} rows={data?.agingByAssignee || []} palette={palette} t={t}/>
                                </Grid>
                                <Grid size={{xs: 12, md: 6}}>
                                    <AgingTable title={t('Aging by Store')} rows={data?.agingByStore || []} palette={palette} t={t}/>
                                </Grid>
                                <Grid size={{xs: 12, md: 6}}>
                                    <AgingTable title={t('Aging by Module')} rows={data?.agingByModule || []} palette={palette} t={t}/>
                                </Grid>
                            </Grid>
                        </Grid>
                    </Grid>
                </>
            )}
        </Box>
    )
}
