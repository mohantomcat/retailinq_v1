import {useEffect, useMemo, useState} from 'react'
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Alert,
    Box,
    Button,
    Chip,
    FormControl,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    Switch,
    TextField,
    Typography,
} from '@mui/material'
import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import PlaylistAddRoundedIcon from '@mui/icons-material/PlaylistAddRounded'
import {operationsApi} from '../services/operationsApi'
import {useAuth} from '../context/AuthContext'
import {RECON_VIEW_OPTIONS} from '../constants/reconViews'

function formatValue(value) {
    if (value == null || value === '') return '-'
    if (typeof value === 'object') return JSON.stringify(value, null, 2)
    return String(value)
}

function formatDateTime(value) {
    if (!value) return '-'
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString()
}

function statusTone(status, palette) {
    switch ((status || '').toUpperCase()) {
        case 'SUCCEEDED':
        case 'ENABLED':
            return {bg: palette.tealChipBg, color: palette.tealChipText}
        case 'FAILED':
        case 'DISABLED':
            return {bg: '#FEF2F2', color: '#B91C1C'}
        case 'RUNNING':
        case 'PENDING':
            return {bg: '#EFF6FF', color: '#1D4ED8'}
        case 'BLOCKED':
            return {bg: '#FFF7ED', color: '#C2410C'}
        default:
            return {bg: palette.cardBgAlt, color: palette.textMuted}
    }
}

function createClientKey() {
    return `step-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function emptyForm() {
    return {
        id: null,
        jobName: '',
        reconView: 'XSTORE_SIM',
        cronExpression: '0 30 23 * * *',
        jobTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
        windowType: 'END_OF_DAY',
        endOfDayLocalTime: '23:00',
        businessDateOffsetDays: 0,
        maxRetryAttempts: 1,
        retryDelayMinutes: 15,
        allowConcurrentRuns: false,
        enabled: true,
        notifyOnSuccess: false,
        notifyOnFailure: true,
        notificationChannelType: '',
        notificationEndpoint: '',
        notificationEmail: '',
        scopeStoreIdsText: '',
        steps: [
            {
                clientStepKey: createClientKey(),
                stepLabel: '',
                stepType: 'OPERATIONS_ACTION',
                stepOrder: 1,
                moduleId: '',
                actionKey: '',
                dependsOnClientStepKey: '',
                settleDelaySeconds: 0,
            },
        ],
    }
}

function SummaryCard({label, value, supporting, palette}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.55rem', fontWeight: 800, color: palette.text}}>{value}</Typography>
            <Typography sx={{mt: 0.45, fontSize: '0.76rem', color: palette.textMuted}}>{supporting}</Typography>
        </Paper>
    )
}

export default function ReconciliationJobs({palette, t}) {
    const {hasPermission} = useAuth()
    const canManage = hasPermission('OPS_EXECUTE_ADVANCED')
    const [center, setCenter] = useState(null)
    const [form, setForm] = useState(emptyForm())
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [runningJobId, setRunningJobId] = useState(null)
    const [feedback, setFeedback] = useState(null)

    const loadCenter = async () => {
        setLoading(true)
        try {
            const data = await operationsApi.getJobsCenter()
            setCenter(data)
        } catch (error) {
            setFeedback({severity: 'error', message: error.message || t('Failed to load reconciliation jobs')})
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadCenter()
    }, [])

    const modules = useMemo(() => {
        const all = center?.actionCatalog || []
        return all.filter((item) => item.reconView === form.reconView)
    }, [center, form.reconView])

    const actionsByModule = useMemo(() => {
        const map = {}
        for (const module of center?.actionCatalog || []) {
            map[module.moduleId] = module.availableActions || []
        }
        return map
    }, [center])

    const applyTemplate = (template) => {
        const idMap = new Map()
        const steps = (template.steps || []).map((step, index) => {
            const clientStepKey = createClientKey()
            idMap.set(step.id, clientStepKey)
            return {
                clientStepKey,
                stepLabel: step.stepLabel || '',
                stepType: step.stepType || 'OPERATIONS_ACTION',
                stepOrder: index + 1,
                moduleId: step.moduleId || '',
                actionKey: step.actionKey || '',
                dependsOnClientStepKey: '',
                settleDelaySeconds: step.settleDelaySeconds ?? 0,
                templateDependsOnStepId: step.dependsOnStepId || null,
            }
        }).map((step) => ({
            ...step,
            dependsOnClientStepKey: step.templateDependsOnStepId ? (idMap.get(step.templateDependsOnStepId) || '') : '',
        }))
        setForm({
            ...emptyForm(),
            jobName: template.templateName,
            reconView: template.reconView || 'XSTORE_SIM',
            cronExpression: template.cronExpression || '0 30 23 * * *',
            windowType: template.windowType || 'END_OF_DAY',
            endOfDayLocalTime: template.endOfDayLocalTime || '23:00',
            businessDateOffsetDays: template.businessDateOffsetDays ?? 0,
            steps: steps.map(({templateDependsOnStepId, ...step}) => step),
        })
    }

    const editDefinition = (definition) => {
        const keyById = new Map()
        const steps = (definition.steps || []).map((step, index) => {
            const clientStepKey = createClientKey()
            keyById.set(step.id, clientStepKey)
            return {
                clientStepKey,
                stepLabel: step.stepLabel || '',
                stepType: step.stepType || 'OPERATIONS_ACTION',
                stepOrder: index + 1,
                moduleId: step.moduleId || '',
                actionKey: step.actionKey || '',
                dependsOnClientStepKey: '',
                settleDelaySeconds: step.settleDelaySeconds ?? 0,
                sourceDependsOnStepId: step.dependsOnStepId || null,
            }
        }).map((step) => ({
            ...step,
            dependsOnClientStepKey: step.sourceDependsOnStepId ? (keyById.get(step.sourceDependsOnStepId) || '') : '',
        }))
        setForm({
            id: definition.id,
            jobName: definition.jobName || '',
            reconView: definition.reconView || 'XSTORE_SIM',
            cronExpression: definition.cronExpression || '',
            jobTimezone: definition.jobTimezone || 'UTC',
            windowType: definition.windowType || 'CONTINUOUS',
            endOfDayLocalTime: definition.endOfDayLocalTime || '',
            businessDateOffsetDays: definition.businessDateOffsetDays ?? 0,
            maxRetryAttempts: definition.maxRetryAttempts ?? 0,
            retryDelayMinutes: definition.retryDelayMinutes ?? 15,
            allowConcurrentRuns: Boolean(definition.allowConcurrentRuns),
            enabled: Boolean(definition.enabled),
            notifyOnSuccess: Boolean(definition.notifyOnSuccess),
            notifyOnFailure: definition.notifyOnFailure !== false,
            notificationChannelType: definition.notificationChannelType || '',
            notificationEndpoint: definition.notificationEndpoint || '',
            notificationEmail: definition.notificationEmail || '',
            scopeStoreIdsText: (definition.scopeStoreIds || []).join(', '),
            steps: steps.map(({sourceDependsOnStepId, ...step}) => step),
        })
    }

    const saveJob = async () => {
        setSaving(true)
        setFeedback(null)
        try {
            const payload = {
                id: form.id || null,
                jobName: form.jobName,
                reconView: form.reconView,
                cronExpression: form.cronExpression,
                jobTimezone: form.jobTimezone,
                windowType: form.windowType,
                endOfDayLocalTime: form.windowType === 'END_OF_DAY' ? form.endOfDayLocalTime : null,
                businessDateOffsetDays: Number(form.businessDateOffsetDays || 0),
                maxRetryAttempts: Number(form.maxRetryAttempts || 0),
                retryDelayMinutes: Number(form.retryDelayMinutes || 15),
                allowConcurrentRuns: Boolean(form.allowConcurrentRuns),
                enabled: Boolean(form.enabled),
                notifyOnSuccess: Boolean(form.notifyOnSuccess),
                notifyOnFailure: Boolean(form.notifyOnFailure),
                notificationChannelType: form.notificationChannelType || null,
                notificationEndpoint: form.notificationEndpoint || null,
                notificationEmail: form.notificationEmail || null,
                scopeStoreIds: form.scopeStoreIdsText.split(',').map((item) => item.trim()).filter(Boolean),
                steps: form.steps.map((step, index) => ({
                    clientStepKey: step.clientStepKey,
                    stepLabel: step.stepLabel,
                    stepType: step.stepType,
                    stepOrder: index + 1,
                    moduleId: step.stepType === 'OPERATIONS_ACTION' ? step.moduleId : null,
                    actionKey: step.stepType === 'OPERATIONS_ACTION' ? step.actionKey : null,
                    dependsOnClientStepKey: step.dependsOnClientStepKey || null,
                    settleDelaySeconds: Number(step.settleDelaySeconds || 0),
                })),
            }
            if (form.id) {
                await operationsApi.updateJob(form.id, payload)
            } else {
                await operationsApi.createJob(payload)
            }
            setFeedback({severity: 'success', message: form.id ? t('Job updated') : t('Job created')})
            setForm(emptyForm())
            await loadCenter()
        } catch (error) {
            setFeedback({severity: 'error', message: error.message || t('Unable to save job')})
        } finally {
            setSaving(false)
        }
    }

    const runNow = async (jobId) => {
        setRunningJobId(jobId)
        setFeedback(null)
        try {
            await operationsApi.runJobNow(jobId)
            setFeedback({severity: 'success', message: t('Manual run started')})
            await loadCenter()
        } catch (error) {
            setFeedback({severity: 'error', message: error.message || t('Unable to run job')})
        } finally {
            setRunningJobId(null)
        }
    }

    const addStep = () => {
        setForm((current) => ({
            ...current,
            steps: [
                ...current.steps,
                {
                    clientStepKey: createClientKey(),
                    stepLabel: '',
                    stepType: 'OPERATIONS_ACTION',
                    stepOrder: current.steps.length + 1,
                    moduleId: '',
                    actionKey: '',
                    dependsOnClientStepKey: current.steps.length ? current.steps[current.steps.length - 1].clientStepKey : '',
                    settleDelaySeconds: 0,
                },
            ],
        }))
    }

    const updateStep = (clientStepKey, field, value) => {
        setForm((current) => ({
            ...current,
            steps: current.steps.map((step) => {
                if (step.clientStepKey !== clientStepKey) return step
                const nextStep = {...step, [field]: value}
                if (field === 'stepType' && value === 'RECON_SUMMARY_SNAPSHOT') {
                    nextStep.moduleId = ''
                    nextStep.actionKey = ''
                }
                if (field === 'moduleId') {
                    nextStep.actionKey = ''
                }
                return nextStep
            }),
        }))
    }

    const removeStep = (clientStepKey) => {
        setForm((current) => {
            const nextSteps = current.steps.filter((step) => step.clientStepKey !== clientStepKey)
            return {
                ...current,
                steps: nextSteps.map((step, index) => ({
                    ...step,
                    stepOrder: index + 1,
                    dependsOnClientStepKey: step.dependsOnClientStepKey === clientStepKey ? '' : step.dependsOnClientStepKey,
                })),
            }
        })
    }

    if (loading && !center) {
        return <Box sx={{p: 3, color: palette.textMuted}}>{t('Loading reconciliation jobs...')}</Box>
    }

    return (
        <Box sx={{p: {xs: 2, md: 3}, display: 'flex', flexDirection: 'column', gap: 2.25}}>
            {feedback ? <Alert severity={feedback.severity}>{feedback.message}</Alert> : null}

            <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(4, minmax(0, 1fr))'}, gap: 1.5}}>
                <SummaryCard label={t('Enabled Jobs')} value={center?.enabledJobs || 0} supporting={t('Scheduled definitions currently active')} palette={palette}/>
                <SummaryCard label={t('Succeeded 24h')} value={center?.successfulRunsLast24Hours || 0} supporting={t('Completed runs in the last 24 hours')} palette={palette}/>
                <SummaryCard label={t('Failed 24h')} value={center?.failedRunsLast24Hours || 0} supporting={t('Runs that need attention or retry')} palette={palette}/>
                <SummaryCard label={t('Pending Retries')} value={center?.pendingRetries || 0} supporting={t('Queued retry attempts still waiting')} palette={palette}/>
            </Box>

            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Recommended Templates')}</Typography>
                <Typography sx={{mt: 0.5, fontSize: '0.82rem', color: palette.textMuted}}>
                    {t('Start from an end-of-day chain, then adjust cron, retry policy, notifications, and store scope.')}
                </Typography>
                <Box sx={{mt: 1.5, display: 'grid', gridTemplateColumns: {xs: '1fr', lg: 'repeat(3, minmax(0, 1fr))'}, gap: 1.25}}>
                    {(center?.templates || []).map((template) => (
                        <Paper key={template.templateKey} elevation={0} sx={{p: 1.75, borderRadius: '18px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                            <Typography sx={{fontSize: '0.92rem', fontWeight: 800, color: palette.text}}>{template.templateName}</Typography>
                            <Typography sx={{mt: 0.4, fontSize: '0.78rem', color: palette.textMuted}}>{template.description}</Typography>
                            <Stack direction="row" spacing={0.8} useFlexGap flexWrap="wrap" sx={{mt: 1}}>
                                <Chip size="small" label={template.reconView} sx={{backgroundColor: palette.cardBg, color: palette.textMuted}}/>
                                <Chip size="small" label={template.cronExpression} sx={{backgroundColor: palette.cardBg, color: palette.textMuted}}/>
                            </Stack>
                            <Button sx={{mt: 1.25}} variant="outlined" onClick={() => applyTemplate(template)} startIcon={<PlaylistAddRoundedIcon/>}>
                                {t('Use Template')}
                            </Button>
                        </Paper>
                    ))}
                </Box>
            </Paper>

            <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: '1.2fr 1fr'}, gap: 2}}>
                <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                    <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap', alignItems: 'center'}}>
                        <Box>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                {form.id ? t('Edit Job Definition') : t('Create Job Definition')}
                            </Typography>
                            <Typography sx={{mt: 0.4, fontSize: '0.8rem', color: palette.textMuted}}>
                                {t('Cron scheduling, end-of-day windows, retry policy, dependency chaining, and completion notifications.')}
                            </Typography>
                        </Box>
                        <Stack direction="row" spacing={1}>
                            <Button variant="outlined" onClick={() => setForm(emptyForm())}>{t('Reset')}</Button>
                            <Button variant="contained" startIcon={<SaveRoundedIcon/>} disabled={!canManage || saving} onClick={saveJob}>
                                {saving ? t('Saving...') : (form.id ? t('Update Job') : t('Create Job'))}
                            </Button>
                        </Stack>
                    </Box>

                    <Box sx={{mt: 1.5, display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.25}}>
                        <TextField label={t('Job Name')} value={form.jobName} onChange={(e) => setForm({...form, jobName: e.target.value})}/>
                        <FormControl>
                            <InputLabel>{t('Recon View')}</InputLabel>
                            <Select label={t('Recon View')} value={form.reconView} onChange={(e) => setForm({...form, reconView: e.target.value})}>
                                        {RECON_VIEW_OPTIONS.map((option) => (
                                            <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                                        ))}
                            </Select>
                        </FormControl>
                        <TextField label={t('Cron Expression')} value={form.cronExpression} onChange={(e) => setForm({...form, cronExpression: e.target.value})}/>
                        <TextField label={t('Timezone')} value={form.jobTimezone} onChange={(e) => setForm({...form, jobTimezone: e.target.value})}/>
                        <FormControl>
                            <InputLabel>{t('Window Type')}</InputLabel>
                            <Select label={t('Window Type')} value={form.windowType} onChange={(e) => setForm({...form, windowType: e.target.value})}>
                                <MenuItem value="CONTINUOUS">{t('Continuous')}</MenuItem>
                                <MenuItem value="END_OF_DAY">{t('End of Day')}</MenuItem>
                            </Select>
                        </FormControl>
                        <TextField label={t('End-of-Day Time')} value={form.endOfDayLocalTime} onChange={(e) => setForm({...form, endOfDayLocalTime: e.target.value})} disabled={form.windowType !== 'END_OF_DAY'}/>
                        <TextField label={t('Business Date Offset')} type="number" value={form.businessDateOffsetDays} onChange={(e) => setForm({...form, businessDateOffsetDays: e.target.value})}/>
                        <TextField label={t('Store Scope')} value={form.scopeStoreIdsText} onChange={(e) => setForm({...form, scopeStoreIdsText: e.target.value})} helperText={t('Comma-separated store IDs; leave empty for all stores')}/>
                        <TextField label={t('Max Retry Attempts')} type="number" value={form.maxRetryAttempts} onChange={(e) => setForm({...form, maxRetryAttempts: e.target.value})}/>
                        <TextField label={t('Retry Delay (mins)')} type="number" value={form.retryDelayMinutes} onChange={(e) => setForm({...form, retryDelayMinutes: e.target.value})}/>
                        <FormControl>
                            <InputLabel>{t('Notify Channel')}</InputLabel>
                            <Select label={t('Notify Channel')} value={form.notificationChannelType} onChange={(e) => setForm({...form, notificationChannelType: e.target.value})}>
                                <MenuItem value="">{t('None')}</MenuItem>
                                <MenuItem value="EMAIL">EMAIL</MenuItem>
                                <MenuItem value="GENERIC_WEBHOOK">GENERIC_WEBHOOK</MenuItem>
                                <MenuItem value="MICROSOFT_TEAMS">MICROSOFT_TEAMS</MenuItem>
                                <MenuItem value="SLACK">SLACK</MenuItem>
                            </Select>
                        </FormControl>
                        <TextField label={t('Notification Email')} value={form.notificationEmail} onChange={(e) => setForm({...form, notificationEmail: e.target.value})}/>
                        <TextField label={t('Notification Endpoint')} value={form.notificationEndpoint} onChange={(e) => setForm({...form, notificationEndpoint: e.target.value})}/>
                    </Box>

                    <Stack direction="row" spacing={2} sx={{mt: 1.5}} useFlexGap flexWrap="wrap">
                        <Stack direction="row" spacing={1} alignItems="center"><Switch checked={form.allowConcurrentRuns} onChange={(e) => setForm({...form, allowConcurrentRuns: e.target.checked})}/><Typography>{t('Allow Concurrent Runs')}</Typography></Stack>
                        <Stack direction="row" spacing={1} alignItems="center"><Switch checked={form.enabled} onChange={(e) => setForm({...form, enabled: e.target.checked})}/><Typography>{t('Enabled')}</Typography></Stack>
                        <Stack direction="row" spacing={1} alignItems="center"><Switch checked={form.notifyOnSuccess} onChange={(e) => setForm({...form, notifyOnSuccess: e.target.checked})}/><Typography>{t('Notify on Success')}</Typography></Stack>
                        <Stack direction="row" spacing={1} alignItems="center"><Switch checked={form.notifyOnFailure} onChange={(e) => setForm({...form, notifyOnFailure: e.target.checked})}/><Typography>{t('Notify on Failure')}</Typography></Stack>
                    </Stack>

                    <Box sx={{mt: 2}}>
                        <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap', alignItems: 'center', mb: 1}}>
                            <Typography sx={{fontSize: '0.92rem', fontWeight: 800, color: palette.text}}>{t('Job Steps')}</Typography>
                            <Button variant="outlined" startIcon={<PlaylistAddRoundedIcon/>} onClick={addStep}>{t('Add Step')}</Button>
                        </Box>
                        <Stack spacing={1}>
                            {form.steps.map((step, index) => (
                                <Paper key={step.clientStepKey} elevation={0} sx={{p: 1.25, borderRadius: '16px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', lg: '1.1fr 0.9fr 0.9fr 0.9fr 0.8fr auto'}, gap: 1}}>
                                        <TextField label={`${t('Step')} ${index + 1}`} value={step.stepLabel} onChange={(e) => updateStep(step.clientStepKey, 'stepLabel', e.target.value)}/>
                                        <FormControl>
                                            <InputLabel>{t('Type')}</InputLabel>
                                            <Select label={t('Type')} value={step.stepType} onChange={(e) => updateStep(step.clientStepKey, 'stepType', e.target.value)}>
                                                <MenuItem value="OPERATIONS_ACTION">OPERATIONS_ACTION</MenuItem>
                                                <MenuItem value="RECON_SUMMARY_SNAPSHOT">RECON_SUMMARY_SNAPSHOT</MenuItem>
                                            </Select>
                                        </FormControl>
                                        <FormControl disabled={step.stepType !== 'OPERATIONS_ACTION'}>
                                            <InputLabel>{t('Module')}</InputLabel>
                                            <Select label={t('Module')} value={step.moduleId} onChange={(e) => updateStep(step.clientStepKey, 'moduleId', e.target.value)}>
                                                {modules.map((module) => <MenuItem key={module.moduleId} value={module.moduleId}>{module.moduleLabel}</MenuItem>)}
                                            </Select>
                                        </FormControl>
                                        <FormControl disabled={step.stepType !== 'OPERATIONS_ACTION' || !step.moduleId}>
                                            <InputLabel>{t('Action')}</InputLabel>
                                            <Select label={t('Action')} value={step.actionKey} onChange={(e) => updateStep(step.clientStepKey, 'actionKey', e.target.value)}>
                                                {(actionsByModule[step.moduleId] || []).map((action) => <MenuItem key={action} value={action}>{action}</MenuItem>)}
                                            </Select>
                                        </FormControl>
                                        <TextField label={t('Settle Delay')} type="number" value={step.settleDelaySeconds} onChange={(e) => updateStep(step.clientStepKey, 'settleDelaySeconds', e.target.value)}/>
                                        <Button color="error" variant="text" onClick={() => removeStep(step.clientStepKey)} disabled={form.steps.length === 1}>{t('Remove')}</Button>
                                    </Box>
                                    <FormControl sx={{mt: 1, minWidth: 220}}>
                                        <InputLabel>{t('Depends On')}</InputLabel>
                                        <Select label={t('Depends On')} value={step.dependsOnClientStepKey} onChange={(e) => updateStep(step.clientStepKey, 'dependsOnClientStepKey', e.target.value)}>
                                            <MenuItem value="">{t('None')}</MenuItem>
                                            {form.steps.filter((candidate, candidateIndex) => candidate.clientStepKey !== step.clientStepKey && candidateIndex < index).map((candidate) => (
                                                <MenuItem key={candidate.clientStepKey} value={candidate.clientStepKey}>
                                                    {candidate.stepLabel || `${t('Step')} ${candidate.stepOrder}`}
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                </Paper>
                            ))}
                        </Stack>
                    </Box>
                </Paper>

                <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Defined Jobs')}</Typography>
                    <Typography sx={{mt: 0.4, fontSize: '0.8rem', color: palette.textMuted}}>
                        {t('Every execution is persisted, with retry ancestry, step history, and notification delivery results.')}
                    </Typography>
                    <Stack spacing={1.15} sx={{mt: 1.5}}>
                        {(center?.jobDefinitions || []).map((definition) => (
                            <Paper key={definition.id} elevation={0} sx={{p: 1.35, borderRadius: '16px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap'}}>
                                    <Box>
                                        <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>{definition.jobName}</Typography>
                                        <Typography sx={{mt: 0.3, fontSize: '0.78rem', color: palette.textMuted}}>
                                            {definition.reconView} · {definition.cronExpression}
                                        </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                        <Chip size="small" label={definition.lastRunStatus || (definition.enabled ? t('Enabled') : t('Disabled'))} sx={statusTone(definition.lastRunStatus || (definition.enabled ? 'ENABLED' : 'DISABLED'), palette)}/>
                                        <Chip size="small" label={definition.windowType || 'CONTINUOUS'} sx={{backgroundColor: palette.cardBg, color: palette.textMuted}}/>
                                    </Stack>
                                </Box>
                                <Typography sx={{mt: 0.85, fontSize: '0.78rem', color: palette.textMuted}}>
                                    {t('Next run')}: {formatDateTime(definition.nextScheduledAt)} · {t('Last completed')}: {formatDateTime(definition.lastRunCompletedAt)}
                                </Typography>
                                <Typography sx={{mt: 0.4, fontSize: '0.78rem', color: palette.textMuted}}>
                                    {definition.lastRunMessage || t('No executions yet')}
                                </Typography>
                                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{mt: 1}}>
                                    <Button variant="outlined" onClick={() => editDefinition(definition)}>{t('Edit')}</Button>
                                    <Button
                                        variant="contained"
                                        startIcon={<PlayArrowRoundedIcon/>}
                                        disabled={!canManage || runningJobId === definition.id}
                                        onClick={() => runNow(definition.id)}
                                    >
                                        {runningJobId === definition.id ? t('Running...') : t('Run Now')}
                                    </Button>
                                </Stack>
                            </Paper>
                        ))}
                    </Stack>
                </Paper>
            </Box>

            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Run History')}</Typography>
                <Typography sx={{mt: 0.4, fontSize: '0.8rem', color: palette.textMuted}}>
                    {t('Scheduled, manual, and retry runs all stay visible with business-date windows, dependency results, and completion notifications.')}
                </Typography>
                <Stack spacing={1.1} sx={{mt: 1.5}}>
                    {(center?.recentRuns || []).map((run) => (
                        <Accordion key={run.id} disableGutters elevation={0} sx={{borderRadius: '16px', overflow: 'hidden', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBgAlt}}>
                            <AccordionSummary expandIcon={<ExpandMoreRoundedIcon/>}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.25, width: '100%', flexWrap: 'wrap', alignItems: 'center'}}>
                                    <Box>
                                        <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>
                                            {run.jobName}
                                        </Typography>
                                        <Typography sx={{mt: 0.3, fontSize: '0.78rem', color: palette.textMuted}}>
                                            {run.triggerType} · {t('Attempt')} {run.attemptNumber} · {formatDateTime(run.startedAt)}
                                        </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                                        <Chip size="small" label={run.runStatus} sx={statusTone(run.runStatus, palette)}/>
                                        {run.retryPending ? <Chip size="small" label={t('Retry Pending')} sx={{backgroundColor: '#FFF7ED', color: '#C2410C'}}/> : null}
                                        {run.businessDate ? <Chip size="small" label={`${t('Business Date')}: ${run.businessDate}`} sx={{backgroundColor: palette.cardBg, color: palette.textMuted}}/> : null}
                                    </Stack>
                                </Box>
                            </AccordionSummary>
                            <AccordionDetails>
                                <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                    {run.summary || t('No run summary recorded')}
                                </Typography>
                                <Box sx={{mt: 1.2, display: 'grid', gridTemplateColumns: {xs: '1fr', lg: '1fr 1fr'}, gap: 1.25}}>
                                    <Paper elevation={0} sx={{p: 1.2, borderRadius: '14px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBg}}>
                                        <Typography sx={{fontSize: '0.8rem', fontWeight: 800, color: palette.text}}>{t('Step History')}</Typography>
                                        <Stack spacing={0.85} sx={{mt: 1}}>
                                            {(run.stepRuns || []).map((step) => (
                                                <Box key={step.id} sx={{p: 1, borderRadius: 2, border: `1px solid ${palette.borderSoft}`}}>
                                                    <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap'}}>
                                                        <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.text}}>
                                                            {step.stepOrder}. {step.stepLabel}
                                                        </Typography>
                                                        <Chip size="small" label={step.runStatus} sx={statusTone(step.runStatus, palette)}/>
                                                    </Box>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {step.stepType} {step.moduleId ? `· ${step.moduleId}` : ''} {step.actionKey ? `· ${step.actionKey}` : ''}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {step.message || t('No message')} · {t('Duration')}: {step.durationMs ?? 0}ms
                                                    </Typography>
                                                </Box>
                                            ))}
                                        </Stack>
                                    </Paper>
                                    <Paper elevation={0} sx={{p: 1.2, borderRadius: '14px', border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.cardBg}}>
                                        <Typography sx={{fontSize: '0.8rem', fontWeight: 800, color: palette.text}}>{t('Result & Notifications')}</Typography>
                                        <Box component="pre" sx={{mt: 1, p: 1, borderRadius: 2, backgroundColor: palette.cardBgAlt, border: `1px solid ${palette.borderSoft}`, overflowX: 'auto', fontSize: '0.72rem', color: palette.text, whiteSpace: 'pre-wrap'}}>
                                            {formatValue(run.resultPayload)}
                                        </Box>
                                        <Stack spacing={0.85} sx={{mt: 1}}>
                                            {(run.notifications || []).map((notification) => (
                                                <Box key={notification.id} sx={{p: 1, borderRadius: 2, border: `1px solid ${palette.borderSoft}`}}>
                                                    <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap'}}>
                                                        <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.text}}>
                                                            {notification.channelType}
                                                        </Typography>
                                                        <Chip size="small" label={notification.deliveryStatus} sx={statusTone(notification.deliveryStatus, palette)}/>
                                                    </Box>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.74rem', color: palette.textMuted}}>
                                                        {notification.destination || t('No destination configured')}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.25, fontSize: '0.74rem', color: palette.textMuted}}>
                                                        {notification.errorMessage || formatDateTime(notification.createdAt)}
                                                    </Typography>
                                                </Box>
                                            ))}
                                        </Stack>
                                    </Paper>
                                </Box>
                            </AccordionDetails>
                        </Accordion>
                    ))}
                </Stack>
            </Paper>
        </Box>
    )
}
