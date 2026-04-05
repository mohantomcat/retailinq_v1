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
import AutorenewRoundedIcon from '@mui/icons-material/AutorenewRounded'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {useReconModules} from '../hooks/useReconModules'

const ACTION_OPTIONS = [
    {value: 'AUTO_RESOLVE', label: 'Auto Resolve'},
    {value: 'SUPPRESS_QUEUE', label: 'Suppress Queue'},
]

const SEVERITY_OPTIONS = ['', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

function SummaryCard({label, value, supporting, tone, palette}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.65, fontSize: '1.55rem', fontWeight: 800, color: tone}}>{value}</Typography>
            {supporting ? (
                <Typography sx={{mt: 0.45, fontSize: '0.76rem', color: palette.textMuted}}>{supporting}</Typography>
            ) : null}
        </Paper>
    )
}

function actionChipStyles(actionType, palette) {
    switch ((actionType || '').toUpperCase()) {
        case 'AUTO_RESOLVE':
            return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
        case 'SUPPRESS_QUEUE':
            return {backgroundColor: palette.blueChipBg, color: palette.blueChipText}
        default:
            return {backgroundColor: palette.cardBgAlt, color: palette.textMuted}
    }
}

function ruleToForm(rule) {
    return {
        ruleName: rule?.ruleName || '',
        reconView: rule?.reconView || '',
        reconStatus: rule?.reconStatus || '',
        maxSeverity: rule?.maxSeverity || '',
        rootCauseCategory: rule?.rootCauseCategory || '',
        reasonCode: rule?.reasonCode || '',
        storeId: rule?.storeId || '',
        actionType: rule?.actionType || 'AUTO_RESOLVE',
        maxValueAtRisk: rule?.maxValueAtRisk ?? '',
        minRepeatCount: rule?.minRepeatCount ?? '',
        active: rule?.active ?? true,
        description: rule?.description || '',
    }
}

function formatDateTimeValue(value) {
    if (!value) return '-'
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value.replace('T', ' ') : parsed.toLocaleString()
}

export default function NoiseSuppression({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptionsWithAll: MODULE_OPTIONS} = useReconModules()
    const canView = hasPermission('EXCEPTION_AUTOMATION_VIEW') || hasPermission('EXCEPTION_AUTOMATION_EDIT')
    const canEdit = hasPermission('EXCEPTION_AUTOMATION_EDIT')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [deleting, setDeleting] = useState(false)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState('')
    const [data, setData] = useState(null)
    const [filters, setFilters] = useState({reconView: ''})
    const [selectedRuleId, setSelectedRuleId] = useState('')
    const [form, setForm] = useState(ruleToForm(null))

    const loadData = async (nextFilters = filters, preferredRuleId = selectedRuleId) => {
        try {
            setLoading(true)
            setError('')
            const response = await exceptionApi.getAutomationCenter({
                reconView: nextFilters.reconView,
            })
            setData(response)
            const rules = response?.suppressionRules || []
            const selected = rules.find((rule) => rule.id === preferredRuleId) || null
            if (selected) {
                setSelectedRuleId(selected.id)
                setForm(ruleToForm(selected))
            } else if (rules[0] && preferredRuleId) {
                setSelectedRuleId(rules[0].id)
                setForm(ruleToForm(rules[0]))
            } else if (!rules.length) {
                setSelectedRuleId('')
                setForm(ruleToForm(null))
            }
        } catch (err) {
            setError(err.message || 'Failed to load suppression rules')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (canView) {
            loadData(filters)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canView])

    const rules = useMemo(() => data?.suppressionRules || [], [data])
    const recentActivity = useMemo(() => data?.recentSuppressionActivity || [], [data])
    const summary = data?.suppressionSummary || {}

    if (!canView) {
        return null
    }

    const handleNewRule = () => {
        setSelectedRuleId('')
        setForm(ruleToForm(null))
        setFeedback('')
    }

    const handleSelectRule = (rule) => {
        setSelectedRuleId(rule.id)
        setForm(ruleToForm(rule))
        setFeedback('')
    }

    const handleFormChange = (field, value) => {
        setForm((current) => ({...current, [field]: value}))
    }

    const handleSave = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ruleName: form.ruleName.trim(),
                reconView: form.reconView,
                reconStatus: form.reconStatus.trim(),
                maxSeverity: form.maxSeverity,
                rootCauseCategory: form.rootCauseCategory.trim(),
                reasonCode: form.reasonCode.trim(),
                storeId: form.storeId.trim(),
                actionType: form.actionType,
                maxValueAtRisk: form.maxValueAtRisk === '' ? null : Number(form.maxValueAtRisk),
                minRepeatCount: form.minRepeatCount === '' ? null : Number(form.minRepeatCount),
                active: Boolean(form.active),
                description: form.description.trim(),
            }
            const saved = selectedRuleId
                ? await exceptionApi.updateSuppressionRule(selectedRuleId, payload)
                : await exceptionApi.createSuppressionRule(payload)
            setSelectedRuleId(saved.id)
            setForm(ruleToForm(saved))
            setFeedback(selectedRuleId ? 'Suppression rule updated.' : 'Suppression rule created.')
            await loadData(filters, saved.id)
        } catch (err) {
            setError(err.message || 'Failed to save suppression rule')
        } finally {
            setSaving(false)
        }
    }

    const handleDelete = async () => {
        if (!selectedRuleId) {
            return
        }
        try {
            setDeleting(true)
            setError('')
            setFeedback('')
            await exceptionApi.deleteSuppressionRule({
                ruleId: selectedRuleId,
                reconView: filters.reconView,
            })
            setSelectedRuleId('')
            setForm(ruleToForm(null))
            setFeedback('Suppression rule removed.')
            await loadData(filters, '')
        } catch (err) {
            setError(err.message || 'Failed to delete suppression rule')
        } finally {
            setDeleting(false)
        }
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 42, height: 42, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.tealChipBg, color: palette.tealChipText, flexShrink: 0}}>
                        <AutorenewRoundedIcon sx={{fontSize: 22}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Noise Suppression')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Reduce analyst workload by automatically resolving or suppressing low-risk repeat exceptions when the rule is simple, explicit, and operationally safe.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}
            {!error && feedback ? <Alert severity="success" sx={{mb: 2}}>{feedback}</Alert> : null}

            <Paper elevation={0} sx={{p: 2.25, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'minmax(0, 1fr) auto'}, gap: 1.5, alignItems: 'end'}}>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Module')}</InputLabel>
                        <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}>
                            {MODULE_OPTIONS.map((option) => (
                                <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <Stack direction={{xs: 'column', sm: 'row'}} spacing={1}>
                        <Button variant="contained" onClick={() => loadData(filters, selectedRuleId)} disabled={loading}>
                            {t('Apply')}
                        </Button>
                        <Button
                            variant="outlined"
                            onClick={() => {
                                const cleared = {reconView: ''}
                                setFilters(cleared)
                                loadData(cleared, selectedRuleId)
                            }}
                            disabled={loading}
                        >
                            {t('Clear')}
                        </Button>
                    </Stack>
                </Box>
            </Paper>

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} md={3}>
                            <SummaryCard label={t('Rules')} value={summary.ruleCount || 0} supporting={t('Total suppression rules')} tone={palette.text} palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={3}>
                            <SummaryCard label={t('Active Rules')} value={summary.activeRuleCount || 0} supporting={t('Currently evaluating cases')} tone={palette.tealChipText} palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={3}>
                            <SummaryCard label={t('Auto Resolved 7d')} value={summary.autoResolvedLast7Days || 0} supporting={t('Closed without analyst handling')} tone={palette.blueChipText} palette={palette}/>
                        </Grid>
                        <Grid item xs={12} md={3}>
                            <SummaryCard label={t('Suppressed 7d')} value={summary.suppressedLast7Days || 0} supporting={t('Removed from the working queue')} tone="#D97706" palette={palette}/>
                        </Grid>
                    </Grid>

                    <Grid container spacing={2}>
                        <Grid item xs={12} lg={5}>
                            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'center', mb: 1.5}}>
                                    <Box>
                                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Rule Coverage')}</Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('Keep rules narrow. These should cover repeat low-risk patterns, not business-critical exceptions.')}
                                        </Typography>
                                    </Box>
                                    {canEdit ? <Button variant="outlined" onClick={handleNewRule}>{t('New Rule')}</Button> : null}
                                </Box>

                                <Stack spacing={1.2}>
                                    {rules.length ? rules.map((rule) => (
                                        <Paper
                                            key={rule.id}
                                            elevation={0}
                                            onClick={() => handleSelectRule(rule)}
                                            sx={{
                                                p: 1.5,
                                                borderRadius: 3,
                                                border: `1px solid ${selectedRuleId === rule.id ? palette.blueChipText : palette.border}`,
                                                backgroundColor: selectedRuleId === rule.id ? palette.selectedBg : palette.cardBg,
                                                cursor: 'pointer',
                                            }}
                                        >
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 250px'}}>
                                                    <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>{rule.ruleName}</Typography>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.76rem', color: palette.textMuted}}>
                                                        {rule.reconView || t('All Modules')} | {rule.reconStatus || t('Any Status')} | {rule.maxSeverity || t('Any Severity')}
                                                    </Typography>
                                                </Box>
                                                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                    <Chip size="small" label={rule.active ? t('Active') : t('Inactive')} sx={{backgroundColor: rule.active ? palette.tealChipBg : palette.cardBgAlt, color: rule.active ? palette.tealChipText : palette.textMuted, fontWeight: 700}}/>
                                                    <Chip size="small" label={rule.actionType || t('Rule')} sx={{fontWeight: 700, ...actionChipStyles(rule.actionType, palette)}}/>
                                                </Stack>
                                            </Box>
                                            <Typography sx={{mt: 0.8, fontSize: '0.82rem', color: palette.text}}>
                                                {rule.description || t('No description supplied.')}
                                            </Typography>
                                            <Typography sx={{mt: 0.55, fontSize: '0.74rem', color: palette.textMuted}}>
                                                {t('Repeat Threshold')}: {rule.minRepeatCount || 0} | {t('Max Value At Risk')}: {rule.maxValueAtRisk ?? t('Any')}
                                            </Typography>
                                        </Paper>
                                    )) : (
                                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                            {t('No suppression rules are configured for the current filter.')}
                                        </Typography>
                                    )}
                                </Stack>
                            </Paper>
                        </Grid>

                        <Grid item xs={12} lg={7}>
                            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start', flexWrap: 'wrap', mb: 2}}>
                                    <Box>
                                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                            {selectedRuleId ? t('Edit Suppression Rule') : t('Create Suppression Rule')}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('Use this only for patterns that are truly low-risk and operationally understood. Approval-gated closures will still remain manual.')}
                                        </Typography>
                                    </Box>
                                    {selectedRuleId ? (
                                        <Chip size="small" label={form.active ? t('Active') : t('Inactive')} sx={{backgroundColor: form.active ? palette.tealChipBg : palette.cardBgAlt, color: form.active ? palette.tealChipText : palette.textMuted, fontWeight: 700}}/>
                                    ) : null}
                                </Box>

                                <Stack spacing={1.5}>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: '1.1fr 0.9fr'}, gap: 1.5}}>
                                        <TextField size="small" label={t('Rule Name')} value={form.ruleName} onChange={(event) => handleFormChange('ruleName', event.target.value)} disabled={!canEdit}/>
                                        <FormControl size="small" fullWidth>
                                            <InputLabel>{t('Action Type')}</InputLabel>
                                            <Select value={form.actionType} label={t('Action Type')} onChange={(event) => handleFormChange('actionType', event.target.value)} disabled={!canEdit}>
                                                {ACTION_OPTIONS.map((option) => (
                                                    <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>
                                                ))}
                                            </Select>
                                        </FormControl>
                                    </Box>

                                    <TextField size="small" label={t('Description')} value={form.description} onChange={(event) => handleFormChange('description', event.target.value)} multiline minRows={2} disabled={!canEdit}/>

                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl size="small" fullWidth>
                                            <InputLabel>{t('Module')}</InputLabel>
                                            <Select value={form.reconView} label={t('Module')} onChange={(event) => handleFormChange('reconView', event.target.value)} disabled={!canEdit}>
                                                {MODULE_OPTIONS.map((option) => (
                                                    <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>
                                                ))}
                                            </Select>
                                        </FormControl>
                                        <TextField size="small" label={t('Recon Status')} value={form.reconStatus} onChange={(event) => handleFormChange('reconStatus', event.target.value)} disabled={!canEdit}/>
                                        <FormControl size="small" fullWidth>
                                            <InputLabel>{t('Max Severity')}</InputLabel>
                                            <Select value={form.maxSeverity} label={t('Max Severity')} onChange={(event) => handleFormChange('maxSeverity', event.target.value)} disabled={!canEdit}>
                                                {SEVERITY_OPTIONS.map((option) => (
                                                    <MenuItem key={option || 'any'} value={option}>{option || t('Any')}</MenuItem>
                                                ))}
                                            </Select>
                                        </FormControl>
                                        <TextField size="small" label={t('Store Scope')} value={form.storeId} onChange={(event) => handleFormChange('storeId', event.target.value)} disabled={!canEdit}/>
                                        <TextField size="small" label={t('Root Cause Category')} value={form.rootCauseCategory} onChange={(event) => handleFormChange('rootCauseCategory', event.target.value)} disabled={!canEdit}/>
                                        <TextField size="small" label={t('Reason Code')} value={form.reasonCode} onChange={(event) => handleFormChange('reasonCode', event.target.value)} disabled={!canEdit}/>
                                        <TextField size="small" label={t('Min Repeat Count')} type="number" value={form.minRepeatCount} onChange={(event) => handleFormChange('minRepeatCount', event.target.value)} disabled={!canEdit}/>
                                        <TextField size="small" label={t('Max Value At Risk')} type="number" value={form.maxValueAtRisk} onChange={(event) => handleFormChange('maxValueAtRisk', event.target.value)} disabled={!canEdit}/>
                                    </Box>

                                    <Box sx={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, p: 1.25, borderRadius: 3, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                        <Box>
                                            <Typography sx={{fontSize: '0.86rem', fontWeight: 700, color: palette.text}}>{t('Rule Active')}</Typography>
                                            <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                {t('Inactive rules stay in the catalog but do not evaluate new cases.')}
                                            </Typography>
                                        </Box>
                                        <Switch checked={Boolean(form.active)} onChange={(event) => handleFormChange('active', event.target.checked)} disabled={!canEdit}/>
                                    </Box>

                                    {canEdit ? (
                                        <Stack direction={{xs: 'column', sm: 'row'}} spacing={1.2}>
                                            <Button variant="contained" onClick={handleSave} disabled={saving || deleting}>
                                                {saving ? t('Saving...') : selectedRuleId ? t('Update Rule') : t('Create Rule')}
                                            </Button>
                                            <Button variant="outlined" onClick={handleNewRule} disabled={saving || deleting}>
                                                {t('Reset')}
                                            </Button>
                                            {selectedRuleId ? (
                                                <Button variant="outlined" color="error" onClick={handleDelete} disabled={saving || deleting}>
                                                    {deleting ? t('Deleting...') : t('Delete Rule')}
                                                </Button>
                                            ) : null}
                                        </Stack>
                                    ) : (
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('You can review suppression performance but do not have permission to change the rules.')}
                                        </Typography>
                                    )}
                                </Stack>
                            </Paper>
                        </Grid>
                    </Grid>

                    <Paper elevation={0} sx={{mt: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg, overflow: 'hidden'}}>
                        <Box sx={{px: 2.5, py: 2}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Recent Automation Activity')}</Typography>
                            <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                {t('Audit trail of rules that automatically resolved or suppressed incoming cases.')}
                            </Typography>
                        </Box>
                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>{t('When')}</TableCell>
                                        <TableCell>{t('Rule')}</TableCell>
                                        <TableCell>{t('Case')}</TableCell>
                                        <TableCell>{t('Action')}</TableCell>
                                        <TableCell>{t('Result')}</TableCell>
                                        <TableCell>{t('Message')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {recentActivity.length ? recentActivity.map((item) => (
                                        <TableRow key={item.id} hover>
                                            <TableCell>{formatDateTimeValue(item.createdAt)}</TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{item.ruleName || t('Unnamed Rule')}</Typography>
                                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>{item.reconView || '-'}</Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Typography sx={{fontSize: '0.82rem', color: palette.text}}>{item.transactionKey || '-'}</Typography>
                                            </TableCell>
                                            <TableCell>
                                                <Chip size="small" label={item.actionType || t('Automation')} sx={{fontWeight: 700, ...actionChipStyles(item.actionType, palette)}}/>
                                            </TableCell>
                                            <TableCell>
                                                <Chip
                                                    size="small"
                                                    label={item.resultStatus || t('Completed')}
                                                    sx={{
                                                        fontWeight: 700,
                                                        backgroundColor: (item.resultStatus || '').toUpperCase() === 'APPLIED' ? palette.tealChipBg : palette.cardBgAlt,
                                                        color: (item.resultStatus || '').toUpperCase() === 'APPLIED' ? palette.tealChipText : palette.textMuted,
                                                    }}
                                                />
                                            </TableCell>
                                            <TableCell sx={{minWidth: 280}}>
                                                <Typography sx={{fontSize: '0.8rem', color: palette.text}}>{item.resultMessage || '-'}</Typography>
                                            </TableCell>
                                        </TableRow>
                                    )) : (
                                        <TableRow>
                                            <TableCell colSpan={6}>
                                                <Typography sx={{py: 2, fontSize: '0.84rem', color: palette.textMuted}}>
                                                    {t('No suppression activity has been recorded yet.')}
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
