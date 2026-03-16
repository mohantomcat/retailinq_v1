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
import NotificationsActiveRoundedIcon from '@mui/icons-material/NotificationsActiveRounded'
import {alertApi} from '../services/alertApi'
import {useAuth} from '../context/AuthContext'

const MODULE_OPTIONS = [
    {value: 'XSTORE_SIM', label: 'Xstore vs SIM'},
    {value: 'XSTORE_SIOCS', label: 'Xstore vs SIOCS'},
    {value: 'XSTORE_XOCS', label: 'Xstore vs XOCS'},
]

const METRIC_OPTIONS = [
    {value: 'MISSING_IN_TARGET', label: 'Missing in Target'},
    {value: 'DUPLICATE_TRANSACTIONS', label: 'Duplicate Transactions'},
    {value: 'MATCH_RATE', label: 'Match Rate'},
    {value: 'QUANTITY_MISMATCH', label: 'Quantity Mismatch'},
    {value: 'ITEM_MISSING', label: 'Item Missing'},
    {value: 'TOTAL_MISMATCH', label: 'Transaction Total Mismatch'},
    {value: 'TOTAL_TRANSACTIONS', label: 'Total Transactions'},
    {value: 'OPEN_EXCEPTIONS_7_PLUS', label: 'Open Exceptions 7+ Days'},
]

const EMPTY_RULE = {
    ruleName: '',
    reconView: 'XSTORE_SIM',
    metricKey: 'MISSING_IN_TARGET',
    operator: '>=',
    thresholdValue: '',
    severity: 'HIGH',
    storeId: '',
    wkstnId: '',
    lookbackDays: 1,
    cooldownMinutes: 60,
    active: true,
    description: '',
}

function SummaryCard({label, value, palette, tone}) {
    const styles = {
        blue: {bg: palette.blueChipBg, color: palette.blueChipText},
        teal: {bg: palette.tealChipBg, color: palette.tealChipText},
        red: {bg: '#FEF2F2', color: '#DC2626'},
        neutral: {bg: palette.neutralChipBg, color: palette.neutralChipText},
    }[tone]

    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.7rem', fontWeight: 800, color: styles.color}}>{value}</Typography>
        </Paper>
    )
}

function AlertRulesTable({rules, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Alert Rules')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Rule')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Threshold')}</TableCell>
                            <TableCell>{t('Severity')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rules.length > 0 ? rules.map((rule) => (
                            <TableRow key={rule.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.text}}>{rule.ruleName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{rule.reconView} • {rule.metricKey}</Typography>
                                </TableCell>
                                <TableCell>{rule.storeId || t('All Stores')}{rule.wkstnId ? ` / ${rule.wkstnId}` : ''}</TableCell>
                                <TableCell>{rule.operator} {rule.thresholdValue}</TableCell>
                                <TableCell><Chip size="small" label={rule.severity} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(rule)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(rule.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : (
                            <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No alert rules defined yet.')}</Typography></TableCell></TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function AlertEventsTable({events, palette, canEdit, onStatus, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('In-App Alert Events')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Message')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell>{t('Severity')}</TableCell>
                            <TableCell>{t('Metric')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {events.length > 0 ? events.map((event) => (
                            <TableRow key={event.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{event.ruleName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{event.eventMessage}</Typography>
                                </TableCell>
                                <TableCell><Chip size="small" label={event.alertStatus} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell><Chip size="small" label={event.severity} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>{event.metricValue} / {event.thresholdValue}</TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        {event.alertStatus === 'OPEN' && <Button size="small" onClick={() => onStatus(event.id, 'ACKNOWLEDGED')} disabled={!canEdit || saving}>{t('Acknowledge')}</Button>}
                                        {event.alertStatus !== 'RESOLVED' && <Button size="small" color="success" onClick={() => onStatus(event.id, 'RESOLVED')} disabled={!canEdit || saving}>{t('Resolve')}</Button>}
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : (
                            <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No alert events available.')}</Typography></TableCell></TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

export default function Alerts({palette, t}) {
    const {hasPermission} = useAuth()
    const canEdit = hasPermission('ALERT_EDIT')
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState('')
    const [selectedReconView, setSelectedReconView] = useState('')
    const [saving, setSaving] = useState(false)
    const [editingRuleId, setEditingRuleId] = useState(null)
    const [ruleForm, setRuleForm] = useState(EMPTY_RULE)

    const loadAlerts = async (reconView = '') => {
        try {
            setLoading(true)
            setError('')
            setData(await alertApi.getAlerts(reconView || null))
        } catch (err) {
            setError(err.message || 'Failed to load alerts')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadAlerts('')
    }, [])

    const filteredRules = useMemo(() => (selectedReconView ? (data?.rules || []).filter((rule) => rule.reconView === selectedReconView) : (data?.rules || [])), [data, selectedReconView])
    const filteredEvents = useMemo(() => (selectedReconView ? (data?.events || []).filter((event) => event.reconView === selectedReconView) : (data?.events || [])), [data, selectedReconView])

    const handleForm = (key, value) => setRuleForm((current) => ({...current, [key]: value}))

    const resetForm = () => {
        setEditingRuleId(null)
        setRuleForm(EMPTY_RULE)
    }

    const saveRule = async () => {
        try {
            setSaving(true)
            setError('')
            const payload = {...ruleForm, thresholdValue: Number(ruleForm.thresholdValue), lookbackDays: Number(ruleForm.lookbackDays || 1), cooldownMinutes: Number(ruleForm.cooldownMinutes || 60)}
            const next = editingRuleId ? await alertApi.updateRule(editingRuleId, payload) : await alertApi.createRule(payload)
            setData(next)
            setFeedback(editingRuleId ? 'Alert rule updated.' : 'Alert rule created.')
            resetForm()
        } catch (err) {
            setError(err.message || 'Failed to save alert rule')
        } finally {
            setSaving(false)
        }
    }

    const editRule = (rule) => {
        setEditingRuleId(rule.id)
        setRuleForm({...EMPTY_RULE, ...rule})
    }

    const deleteRule = async (ruleId) => {
        try {
            setSaving(true)
            setError('')
            setData(await alertApi.deleteRule(ruleId))
            setFeedback('Alert rule deleted.')
            if (editingRuleId === ruleId) resetForm()
        } catch (err) {
            setError(err.message || 'Failed to delete alert rule')
        } finally {
            setSaving(false)
        }
    }

    const updateEvent = async (eventId, status) => {
        try {
            setSaving(true)
            setError('')
            setData(await alertApi.updateEventStatus(eventId, status))
            setFeedback(status === 'ACKNOWLEDGED' ? 'Alert acknowledged.' : 'Alert resolved.')
        } catch (err) {
            setError(err.message || 'Failed to update alert event')
        } finally {
            setSaving(false)
        }
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#FEF2F2', color: '#DC2626', flexShrink: 0}}>
                        <NotificationsActiveRoundedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Alerts')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 920}}>
                            {t('Define threshold-based in-app alerts for reconciliation KPIs and manage alert events by module, store, and register scope.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {feedback ? <Alert severity="success" sx={{mb: 2}}>{feedback}</Alert> : null}
            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            {loading ? <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box> : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Active Rules')} value={data?.summary?.activeRules || 0} tone="blue" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Open Alerts')} value={data?.summary?.openEvents || 0} tone="red" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Acknowledged')} value={data?.summary?.acknowledgedEvents || 0} tone="teal" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Resolved')} value={data?.summary?.resolvedEvents || 0} tone="neutral" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Critical Active')} value={data?.summary?.criticalEvents || 0} tone="red" palette={palette}/></Grid>
                    </Grid>

                    <Paper elevation={0} sx={{p: 2, mb: 3, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <FormControl size="small" sx={{minWidth: 260}}>
                            <InputLabel>{t('Module')}</InputLabel>
                            <Select value={selectedReconView} label={t('Module')} onChange={(event) => setSelectedReconView(event.target.value)}>
                                <MenuItem value="">{t('All Modules')}</MenuItem>
                                {MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                            </Select>
                        </FormControl>
                    </Paper>

                    <Grid container spacing={2}>
                        <Grid item xs={12} lg={5}>
                            <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{editingRuleId ? t('Edit Alert Rule') : t('Create Alert Rule')}</Typography>
                                <Stack spacing={1.5}>
                                    <TextField label={t('Rule Name')} value={ruleForm.ruleName} onChange={(event) => handleForm('ruleName', event.target.value)} disabled={!canEdit || saving}/>
                                    <Grid container spacing={1.5}>
                                        <Grid item xs={12} md={6}>
                                            <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={ruleForm.reconView} label={t('Module')} onChange={(event) => handleForm('reconView', event.target.value)} disabled={!canEdit || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        </Grid>
                                        <Grid item xs={12} md={6}>
                                            <FormControl fullWidth size="small"><InputLabel>{t('Metric')}</InputLabel><Select value={ruleForm.metricKey} label={t('Metric')} onChange={(event) => handleForm('metricKey', event.target.value)} disabled={!canEdit || saving}>{METRIC_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        </Grid>
                                        <Grid item xs={12} md={4}>
                                            <FormControl fullWidth size="small"><InputLabel>{t('Operator')}</InputLabel><Select value={ruleForm.operator} label={t('Operator')} onChange={(event) => handleForm('operator', event.target.value)} disabled={!canEdit || saving}>{['>', '>=', '<', '<='].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                        </Grid>
                                        <Grid item xs={12} md={4}><TextField fullWidth label={t('Threshold')} type="number" value={ruleForm.thresholdValue} onChange={(event) => handleForm('thresholdValue', event.target.value)} disabled={!canEdit || saving}/></Grid>
                                        <Grid item xs={12} md={4}>
                                            <FormControl fullWidth size="small"><InputLabel>{t('Severity')}</InputLabel><Select value={ruleForm.severity} label={t('Severity')} onChange={(event) => handleForm('severity', event.target.value)} disabled={!canEdit || saving}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                        </Grid>
                                    </Grid>
                                    <Grid container spacing={1.5}>
                                        <Grid item xs={12} md={6}><TextField fullWidth label={t('Store Id')} value={ruleForm.storeId} onChange={(event) => handleForm('storeId', event.target.value)} disabled={!canEdit || saving}/></Grid>
                                        <Grid item xs={12} md={6}><TextField fullWidth label={t('Register Id')} value={ruleForm.wkstnId} onChange={(event) => handleForm('wkstnId', event.target.value)} disabled={!canEdit || saving}/></Grid>
                                        <Grid item xs={12} md={6}><TextField fullWidth label={t('Lookback Days')} type="number" value={ruleForm.lookbackDays} onChange={(event) => handleForm('lookbackDays', event.target.value)} disabled={!canEdit || saving}/></Grid>
                                        <Grid item xs={12} md={6}><TextField fullWidth label={t('Cooldown Minutes')} type="number" value={ruleForm.cooldownMinutes} onChange={(event) => handleForm('cooldownMinutes', event.target.value)} disabled={!canEdit || saving}/></Grid>
                                    </Grid>
                                    <TextField label={t('Description')} multiline minRows={2} value={ruleForm.description} onChange={(event) => handleForm('description', event.target.value)} disabled={!canEdit || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={ruleForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleForm('active', event.target.value === 'ACTIVE')} disabled={!canEdit || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={saveRule} disabled={!canEdit || saving || !String(ruleForm.ruleName).trim() || ruleForm.thresholdValue === ''}>{editingRuleId ? t('Update Rule') : t('Create Rule')}</Button>
                                        <Button variant="text" onClick={resetForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>
                        </Grid>
                        <Grid item xs={12} lg={7}>
                            <AlertRulesTable rules={filteredRules} palette={palette} canEdit={canEdit} onEdit={editRule} onDelete={deleteRule} t={t} saving={saving}/>
                            <AlertEventsTable events={filteredEvents} palette={palette} canEdit={canEdit} onStatus={updateEvent} t={t} saving={saving}/>
                        </Grid>
                    </Grid>
                </>
            )}
        </Box>
    )
}
