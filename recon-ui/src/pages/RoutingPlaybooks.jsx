import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    FormControl,
    Grid,
    IconButton,
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
import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {RECON_VIEW_OPTIONS_WITH_ALL} from '../constants/reconViews'

const MODULE_OPTIONS = RECON_VIEW_OPTIONS_WITH_ALL

const RECON_STATUS_OPTIONS = [
    '',
    'MISSING_IN_SIM',
    'MISSING_IN_SIOCS',
    'MISSING_IN_MFCS',
    'MISSING_IN_XOCS',
    'PROCESSING_PENDING_IN_SIM',
    'PROCESSING_PENDING_IN_SIOCS',
    'PROCESSING_PENDING_IN_MFCS',
    'ITEM_MISSING',
    'QUANTITY_MISMATCH',
    'TOTAL_MISMATCH',
    'DUPLICATE_IN_SIM',
    'DUPLICATE_IN_SIOCS',
    'DUPLICATE_IN_MFCS',
    'DUPLICATE_IN_XOCS',
]

const SEVERITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const ROOT_CAUSE_CATEGORY_OPTIONS = [
    '',
    'INTEGRATION_TIMING',
    'DUPLICATE_PROCESSING',
    'ITEM_SYNC_GAP',
    'RECONCILIATION_VARIANCE',
    'CONFIGURATION_ISSUE',
    'SOURCE_DATA_ISSUE',
    'MANUAL_REVIEW',
]
const REASON_CODE_OPTIONS = [
    '',
    'REPLICATION_LAG',
    'DUPLICATE_SUBMISSION',
    'ITEM_SYNC_GAP',
    'QUANTITY_VARIANCE',
    'TOTAL_CALCULATION_MISMATCH',
    'CONFIGURATION_ISSUE',
    'SOURCE_DATA_ISSUE',
    'MANUAL_REVIEW_REQUIRED',
]
const OPERATION_MODULE_OPTIONS = [
    '',
    'xstore-publisher',
    'sim-poller',
    'siocs-cloud-connector',
    'mfcs-rds-connector',
    'xocs-cloud-connector',
]

function emptyRoutingRule() {
    return {
        id: null,
        ruleName: '',
        reconView: 'XSTORE_SIM',
        reconStatus: '',
        minSeverity: 'MEDIUM',
        rootCauseCategory: '',
        reasonCode: '',
        storeId: '',
        targetAssigneeUsername: '',
        targetRoleName: '',
        priority: 100,
        active: true,
        description: '',
    }
}

function emptyStep(stepOrder) {
    return {
        id: null,
        stepOrder,
        stepTitle: '',
        stepDetail: '',
        operationModuleId: '',
        operationActionKey: '',
    }
}

function emptyPlaybook() {
    return {
        id: null,
        playbookName: '',
        reconView: 'XSTORE_SIM',
        reconStatus: '',
        minSeverity: 'MEDIUM',
        rootCauseCategory: '',
        reasonCode: '',
        active: true,
        description: '',
        steps: [emptyStep(1)],
    }
}

function SummaryCard({label, value, palette, tone = 'blue'}) {
    const color = {
        blue: palette.blueChipText,
        teal: palette.tealChipText,
        purple: '#7C3AED',
    }[tone]

    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.55, fontSize: '1.7rem', fontWeight: 800, color}}>{value}</Typography>
        </Paper>
    )
}

function RuleTable({rules, onEdit, onDelete, palette, t, canEdit, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                {t('Routing Rules')}
            </Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Rule')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Route To')}</TableCell>
                            <TableCell>{t('Priority')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell align="right">{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rules.length > 0 ? rules.map((rule) => (
                            <TableRow key={rule.id} hover>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>
                                        {rule.ruleName}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                        {rule.reconView}
                                    </Typography>
                                </TableCell>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                        {(rule.reconStatus || t('Any'))} | {(rule.reasonCode || t('Any'))}
                                    </Typography>
                                </TableCell>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                        {rule.targetAssigneeUsername || '-'} / {rule.targetRoleName || '-'}
                                    </Typography>
                                </TableCell>
                                <TableCell>{rule.priority}</TableCell>
                                <TableCell>
                                    <Chip label={rule.active ? t('Active') : t('Inactive')} size="small" sx={{backgroundColor: rule.active ? palette.tealChipBg : palette.cardBg, color: rule.active ? palette.tealChipText : palette.textMuted, fontWeight: 700}} />
                                </TableCell>
                                <TableCell align="right">
                                    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                                        <Button size="small" onClick={() => onEdit(rule)} sx={{textTransform: 'none'}}>{t('Edit')}</Button>
                                        <IconButton size="small" onClick={() => onDelete(rule)} disabled={!canEdit || saving}>
                                            <DeleteOutlineRoundedIcon sx={{fontSize: 18}}/>
                                        </IconButton>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : (
                            <TableRow>
                                <TableCell colSpan={6}>
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('No routing rules found for the selected filters.')}
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

function PlaybookTable({playbooks, onEdit, onDelete, palette, t, canEdit, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                {t('Playbooks')}
            </Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Playbook')}</TableCell>
                            <TableCell>{t('Trigger')}</TableCell>
                            <TableCell>{t('Steps')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell align="right">{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {playbooks.length > 0 ? playbooks.map((playbook) => (
                            <TableRow key={playbook.id} hover>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>
                                        {playbook.playbookName}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                        {playbook.reconView}
                                    </Typography>
                                </TableCell>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                        {(playbook.reconStatus || t('Any'))} | {(playbook.reasonCode || t('Any'))}
                                    </Typography>
                                </TableCell>
                                <TableCell>{playbook.steps?.length || 0}</TableCell>
                                <TableCell>
                                    <Chip label={playbook.active ? t('Active') : t('Inactive')} size="small" sx={{backgroundColor: playbook.active ? palette.tealChipBg : palette.cardBg, color: playbook.active ? palette.tealChipText : palette.textMuted, fontWeight: 700}} />
                                </TableCell>
                                <TableCell align="right">
                                    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                                        <Button size="small" onClick={() => onEdit(playbook)} sx={{textTransform: 'none'}}>{t('Edit')}</Button>
                                        <IconButton size="small" onClick={() => onDelete(playbook)} disabled={!canEdit || saving}>
                                            <DeleteOutlineRoundedIcon sx={{fontSize: 18}}/>
                                        </IconButton>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : (
                            <TableRow>
                                <TableCell colSpan={5}>
                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                        {t('No playbooks found for the selected filters.')}
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

export default function RoutingPlaybooks({palette, t}) {
    const {hasPermission} = useAuth()
    const canView = hasPermission('EXCEPTION_AUTOMATION_VIEW') || hasPermission('EXCEPTION_AUTOMATION_EDIT')
    const canEdit = hasPermission('EXCEPTION_AUTOMATION_EDIT')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [message, setMessage] = useState('')
    const [filters, setFilters] = useState({reconView: ''})
    const [assignmentOptions, setAssignmentOptions] = useState({usernames: [], roleNames: []})
    const [data, setData] = useState({routingRules: [], playbooks: []})
    const [routingRuleForm, setRoutingRuleForm] = useState(emptyRoutingRule())
    const [playbookForm, setPlaybookForm] = useState(emptyPlaybook())

    const loadData = async (nextFilters = filters) => {
        setLoading(true)
        setError('')
        try {
            const [automationCenter, options] = await Promise.all([
                exceptionApi.getAutomationCenter({reconView: nextFilters.reconView}),
                exceptionApi.getAssignmentOptions(),
            ])
            setData(automationCenter || {routingRules: [], playbooks: []})
            setAssignmentOptions(options || {usernames: [], roleNames: []})
        } catch (err) {
            setError(err.message || t('Failed to load routing and playbook automation settings'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (canView) {
            loadData(filters)
        }
    }, [canView])

    const summary = useMemo(() => ({
        rules: data.routingRules?.length || 0,
        activeRules: (data.routingRules || []).filter((rule) => rule.active).length,
        playbooks: data.playbooks?.length || 0,
    }), [data])

    if (!canView) {
        return null
    }

    const saveRoutingRule = async () => {
        setSaving(true)
        setError('')
        setMessage('')
        try {
            const payload = {...routingRuleForm, priority: Number(routingRuleForm.priority || 100)}
            if (routingRuleForm.id) {
                await exceptionApi.updateRoutingRule(routingRuleForm.id, payload)
            } else {
                await exceptionApi.createRoutingRule(payload)
            }
            setRoutingRuleForm(emptyRoutingRule())
            setMessage(t('Routing rule saved successfully.'))
            await loadData(filters)
        } catch (err) {
            setError(err.message || t('Failed to save routing rule'))
        } finally {
            setSaving(false)
        }
    }

    const deleteRoutingRule = async (rule) => {
        if (!window.confirm(t('Delete this routing rule?'))) return
        setSaving(true)
        setError('')
        setMessage('')
        try {
            await exceptionApi.deleteRoutingRule({ruleId: rule.id, reconView: rule.reconView})
            if (routingRuleForm.id === rule.id) {
                setRoutingRuleForm(emptyRoutingRule())
            }
            setMessage(t('Routing rule deleted.'))
            await loadData(filters)
        } catch (err) {
            setError(err.message || t('Failed to delete routing rule'))
        } finally {
            setSaving(false)
        }
    }

    const savePlaybook = async () => {
        setSaving(true)
        setError('')
        setMessage('')
        try {
            const payload = {
                ...playbookForm,
                steps: playbookForm.steps.map((step, index) => ({...step, stepOrder: index + 1})),
            }
            if (playbookForm.id) {
                await exceptionApi.updatePlaybook(playbookForm.id, payload)
            } else {
                await exceptionApi.createPlaybook(payload)
            }
            setPlaybookForm(emptyPlaybook())
            setMessage(t('Playbook saved successfully.'))
            await loadData(filters)
        } catch (err) {
            setError(err.message || t('Failed to save playbook'))
        } finally {
            setSaving(false)
        }
    }

    const deletePlaybook = async (playbook) => {
        if (!window.confirm(t('Delete this playbook?'))) return
        setSaving(true)
        setError('')
        setMessage('')
        try {
            await exceptionApi.deletePlaybook({playbookId: playbook.id, reconView: playbook.reconView})
            if (playbookForm.id === playbook.id) {
                setPlaybookForm(emptyPlaybook())
            }
            setMessage(t('Playbook deleted.'))
            await loadData(filters)
        } catch (err) {
            setError(err.message || t('Failed to delete playbook'))
        } finally {
            setSaving(false)
        }
    }

    const updatePlaybookStep = (index, field, value) => {
        setPlaybookForm((current) => ({
            ...current,
            steps: current.steps.map((step, stepIndex) => stepIndex === index ? {...step, [field]: value} : step),
        }))
    }

    const addStep = () => {
        setPlaybookForm((current) => ({
            ...current,
            steps: [...current.steps, emptyStep(current.steps.length + 1)],
        }))
    }

    const removeStep = (index) => {
        setPlaybookForm((current) => {
            const steps = current.steps.filter((_, stepIndex) => stepIndex !== index)
                .map((step, stepIndex) => ({...step, stepOrder: stepIndex + 1}))
            return {...current, steps: steps.length > 0 ? steps : [emptyStep(1)]}
        })
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.tealChipBg, color: palette.tealChipText, flexShrink: 0}}>
                        <AutoFixHighRoundedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Routing & Playbooks')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Configure smart routing rules and guided resolution playbooks so new exceptions are routed to the right owner and handled with repeatable operational steps.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error && <Alert severity="error" sx={{mb: 2}}>{error}</Alert>}
            {!error && message && <Alert severity="success" sx={{mb: 2}}>{message}</Alert>}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} md={4}><SummaryCard label={t('Routing Rules')} value={summary.rules} palette={palette} tone="blue"/></Grid>
                        <Grid item xs={12} md={4}><SummaryCard label={t('Active Rules')} value={summary.activeRules} palette={palette} tone="teal"/></Grid>
                        <Grid item xs={12} md={4}><SummaryCard label={t('Playbooks')} value={summary.playbooks} palette={palette} tone="purple"/></Grid>
                    </Grid>

                    <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'minmax(0, 280px) auto'}, gap: 1.5, alignItems: 'center'}}>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Module')}</InputLabel>
                                <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters({reconView: event.target.value})}>
                                    {MODULE_OPTIONS.map((option) => (
                                        <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                            <Stack direction={{xs: 'column', sm: 'row'}} spacing={1} justifyContent="flex-end">
                                <Button variant="contained" onClick={() => loadData(filters)} sx={{minWidth: 112, height: 40}}>{t('Apply')}</Button>
                                <Button variant="outlined" onClick={() => {
                                    const cleared = {reconView: ''}
                                    setFilters(cleared)
                                    loadData(cleared)
                                }} sx={{minWidth: 96, height: 40}}>
                                    {t('Clear')}
                                </Button>
                            </Stack>
                        </Box>
                    </Paper>

                    <Grid container spacing={3}>
                        <Grid item xs={12} xl={6}>
                            <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                    {routingRuleForm.id ? t('Edit Routing Rule') : t('New Routing Rule')}
                                </Typography>
                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                    <TextField size="small" label={t('Rule Name')} value={routingRuleForm.ruleName} onChange={(event) => setRoutingRuleForm((current) => ({...current, ruleName: event.target.value}))} disabled={!canEdit || saving}/>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Module')}</InputLabel>
                                        <Select value={routingRuleForm.reconView} label={t('Module')} onChange={(event) => setRoutingRuleForm((current) => ({...current, reconView: event.target.value}))} disabled={!canEdit || saving}>
                                            {MODULE_OPTIONS.filter((option) => option.value).map((option) => (
                                                <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Recon Status')}</InputLabel>
                                        <Select value={routingRuleForm.reconStatus} label={t('Recon Status')} onChange={(event) => setRoutingRuleForm((current) => ({...current, reconStatus: event.target.value}))} disabled={!canEdit || saving}>
                                            {RECON_STATUS_OPTIONS.map((option) => (
                                                <MenuItem key={option || 'any'} value={option}>{option || t('Any')}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Minimum Severity')}</InputLabel>
                                        <Select value={routingRuleForm.minSeverity} label={t('Minimum Severity')} onChange={(event) => setRoutingRuleForm((current) => ({...current, minSeverity: event.target.value}))} disabled={!canEdit || saving}>
                                            {SEVERITY_OPTIONS.map((option) => (
                                                <MenuItem key={option} value={option}>{option}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Root Cause Category')}</InputLabel>
                                        <Select value={routingRuleForm.rootCauseCategory} label={t('Root Cause Category')} onChange={(event) => setRoutingRuleForm((current) => ({...current, rootCauseCategory: event.target.value}))} disabled={!canEdit || saving}>
                                            {ROOT_CAUSE_CATEGORY_OPTIONS.map((option) => (
                                                <MenuItem key={option || 'any'} value={option}>{option || t('Any')}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Reason Code')}</InputLabel>
                                        <Select value={routingRuleForm.reasonCode} label={t('Reason Code')} onChange={(event) => setRoutingRuleForm((current) => ({...current, reasonCode: event.target.value}))} disabled={!canEdit || saving}>
                                            {REASON_CODE_OPTIONS.map((option) => (
                                                <MenuItem key={option || 'any'} value={option}>{option || t('Any')}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <TextField size="small" label={t('Store Scope')} value={routingRuleForm.storeId} onChange={(event) => setRoutingRuleForm((current) => ({...current, storeId: event.target.value}))} disabled={!canEdit || saving} helperText={t('Optional exact store match')}/>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Target Assignee')}</InputLabel>
                                        <Select value={routingRuleForm.targetAssigneeUsername} label={t('Target Assignee')} onChange={(event) => setRoutingRuleForm((current) => ({...current, targetAssigneeUsername: event.target.value}))} disabled={!canEdit || saving}>
                                            <MenuItem value="">{t('None')}</MenuItem>
                                            {assignmentOptions.usernames.map((option) => (
                                                <MenuItem key={option} value={option}>{option}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Target Team')}</InputLabel>
                                        <Select value={routingRuleForm.targetRoleName} label={t('Target Team')} onChange={(event) => setRoutingRuleForm((current) => ({...current, targetRoleName: event.target.value}))} disabled={!canEdit || saving}>
                                            <MenuItem value="">{t('None')}</MenuItem>
                                            {assignmentOptions.roleNames.map((option) => (
                                                <MenuItem key={option} value={option}>{option}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <TextField size="small" type="number" label={t('Priority')} value={routingRuleForm.priority} onChange={(event) => setRoutingRuleForm((current) => ({...current, priority: event.target.value}))} disabled={!canEdit || saving}/>
                                </Box>
                                <TextField sx={{mt: 1.5}} size="small" label={t('Description')} value={routingRuleForm.description} onChange={(event) => setRoutingRuleForm((current) => ({...current, description: event.target.value}))} disabled={!canEdit || saving} multiline minRows={2} fullWidth/>
                                <Stack direction="row" spacing={1} sx={{mt: 1.5}} alignItems="center" justifyContent="space-between">
                                    <Stack direction="row" spacing={1} alignItems="center">
                                        <Chip label={routingRuleForm.active ? t('Active') : t('Inactive')} size="small" sx={{backgroundColor: routingRuleForm.active ? palette.tealChipBg : palette.cardBg, color: routingRuleForm.active ? palette.tealChipText : palette.textMuted, fontWeight: 700}}/>
                                        <Button variant="text" onClick={() => setRoutingRuleForm((current) => ({...current, active: !current.active}))} disabled={!canEdit || saving} sx={{textTransform: 'none'}}>
                                            {routingRuleForm.active ? t('Disable') : t('Enable')}
                                        </Button>
                                    </Stack>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="outlined" onClick={() => setRoutingRuleForm(emptyRoutingRule())} sx={{textTransform: 'none'}}>{t('New')}</Button>
                                        <Button variant="contained" onClick={saveRoutingRule} disabled={!canEdit || saving} sx={{textTransform: 'none'}}>
                                            {routingRuleForm.id ? t('Update Rule') : t('Save Rule')}
                                        </Button>
                                    </Stack>
                                </Stack>
                            </Paper>
                            <RuleTable rules={data.routingRules || []} onEdit={(rule) => setRoutingRuleForm({...rule})} onDelete={deleteRoutingRule} palette={palette} t={t} canEdit={canEdit} saving={saving}/>
                        </Grid>
                        <Grid item xs={12} xl={6}>
                            <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                    {playbookForm.id ? t('Edit Playbook') : t('New Playbook')}
                                </Typography>
                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                    <TextField size="small" label={t('Playbook Name')} value={playbookForm.playbookName} onChange={(event) => setPlaybookForm((current) => ({...current, playbookName: event.target.value}))} disabled={!canEdit || saving}/>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Module')}</InputLabel>
                                        <Select value={playbookForm.reconView} label={t('Module')} onChange={(event) => setPlaybookForm((current) => ({...current, reconView: event.target.value}))} disabled={!canEdit || saving}>
                                            {MODULE_OPTIONS.filter((option) => option.value).map((option) => (
                                                <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Recon Status')}</InputLabel>
                                        <Select value={playbookForm.reconStatus} label={t('Recon Status')} onChange={(event) => setPlaybookForm((current) => ({...current, reconStatus: event.target.value}))} disabled={!canEdit || saving}>
                                            {RECON_STATUS_OPTIONS.map((option) => (
                                                <MenuItem key={option || 'any'} value={option}>{option || t('Any')}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Minimum Severity')}</InputLabel>
                                        <Select value={playbookForm.minSeverity} label={t('Minimum Severity')} onChange={(event) => setPlaybookForm((current) => ({...current, minSeverity: event.target.value}))} disabled={!canEdit || saving}>
                                            {SEVERITY_OPTIONS.map((option) => (
                                                <MenuItem key={option} value={option}>{option}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Root Cause Category')}</InputLabel>
                                        <Select value={playbookForm.rootCauseCategory} label={t('Root Cause Category')} onChange={(event) => setPlaybookForm((current) => ({...current, rootCauseCategory: event.target.value}))} disabled={!canEdit || saving}>
                                            {ROOT_CAUSE_CATEGORY_OPTIONS.map((option) => (
                                                <MenuItem key={option || 'any'} value={option}>{option || t('Any')}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Reason Code')}</InputLabel>
                                        <Select value={playbookForm.reasonCode} label={t('Reason Code')} onChange={(event) => setPlaybookForm((current) => ({...current, reasonCode: event.target.value}))} disabled={!canEdit || saving}>
                                            {REASON_CODE_OPTIONS.map((option) => (
                                                <MenuItem key={option || 'any'} value={option}>{option || t('Any')}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                </Box>
                                <TextField sx={{mt: 1.5}} size="small" label={t('Description')} value={playbookForm.description} onChange={(event) => setPlaybookForm((current) => ({...current, description: event.target.value}))} disabled={!canEdit || saving} multiline minRows={2} fullWidth/>

                                <Box sx={{mt: 2}}>
                                    <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{mb: 1.25}}>
                                        <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>{t('Playbook Steps')}</Typography>
                                        <Button startIcon={<AddRoundedIcon/>} variant="outlined" onClick={addStep} disabled={!canEdit || saving} sx={{textTransform: 'none'}}>
                                            {t('Add Step')}
                                        </Button>
                                    </Stack>
                                    <Stack spacing={1.25}>
                                        {playbookForm.steps.map((step, index) => (
                                            <Paper key={step.id || `step-${index}`} elevation={0} sx={{p: 1.5, borderRadius: 3, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.paperBg}}>
                                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', lg: '80px minmax(0, 1.2fr) minmax(0, 1fr) auto'}, gap: 1.2, alignItems: 'start'}}>
                                                    <TextField size="small" type="number" label={t('Order')} value={index + 1} disabled/>
                                                    <TextField size="small" label={t('Step Title')} value={step.stepTitle} onChange={(event) => updatePlaybookStep(index, 'stepTitle', event.target.value)} disabled={!canEdit || saving}/>
                                                    <TextField size="small" label={t('Step Detail')} value={step.stepDetail} onChange={(event) => updatePlaybookStep(index, 'stepDetail', event.target.value)} disabled={!canEdit || saving}/>
                                                    <IconButton onClick={() => removeStep(index)} disabled={!canEdit || saving}>
                                                        <DeleteOutlineRoundedIcon sx={{fontSize: 18}}/>
                                                    </IconButton>
                                                </Box>
                                                <Box sx={{mt: 1.2, display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.2}}>
                                                    <FormControl size="small" fullWidth>
                                                        <InputLabel>{t('Operation Module')}</InputLabel>
                                                        <Select value={step.operationModuleId} label={t('Operation Module')} onChange={(event) => updatePlaybookStep(index, 'operationModuleId', event.target.value)} disabled={!canEdit || saving}>
                                                            {OPERATION_MODULE_OPTIONS.map((option) => (
                                                                <MenuItem key={option || 'none'} value={option}>{option || t('None')}</MenuItem>
                                                            ))}
                                                        </Select>
                                                    </FormControl>
                                                    <TextField size="small" label={t('Operation Action Key')} value={step.operationActionKey} onChange={(event) => updatePlaybookStep(index, 'operationActionKey', event.target.value)} disabled={!canEdit || saving} helperText={t('Optional link to an Operations action')}/>
                                                </Box>
                                            </Paper>
                                        ))}
                                    </Stack>
                                </Box>

                                <Stack direction="row" spacing={1} sx={{mt: 1.5}} alignItems="center" justifyContent="space-between">
                                    <Stack direction="row" spacing={1} alignItems="center">
                                        <Chip label={playbookForm.active ? t('Active') : t('Inactive')} size="small" sx={{backgroundColor: playbookForm.active ? palette.tealChipBg : palette.cardBg, color: playbookForm.active ? palette.tealChipText : palette.textMuted, fontWeight: 700}}/>
                                        <Button variant="text" onClick={() => setPlaybookForm((current) => ({...current, active: !current.active}))} disabled={!canEdit || saving} sx={{textTransform: 'none'}}>
                                            {playbookForm.active ? t('Disable') : t('Enable')}
                                        </Button>
                                    </Stack>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="outlined" onClick={() => setPlaybookForm(emptyPlaybook())} sx={{textTransform: 'none'}}>{t('New')}</Button>
                                        <Button variant="contained" onClick={savePlaybook} disabled={!canEdit || saving} sx={{textTransform: 'none'}}>
                                            {playbookForm.id ? t('Update Playbook') : t('Save Playbook')}
                                        </Button>
                                    </Stack>
                                </Stack>
                            </Paper>
                            <PlaybookTable
                                playbooks={data.playbooks || []}
                                onEdit={(playbook) => setPlaybookForm({...playbook, steps: (playbook.steps || []).map((step, index) => ({...step, stepOrder: index + 1}))})}
                                onDelete={deletePlaybook}
                                palette={palette}
                                t={t}
                                canEdit={canEdit}
                                saving={saving}
                            />
                        </Grid>
                    </Grid>
                </>
            )}
        </Box>
    )
}
