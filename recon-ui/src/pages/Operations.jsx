import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    Grid,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import BuildCircleRoundedIcon from '@mui/icons-material/BuildCircleRounded'
import {operationsApi} from '../services/operationsApi'
import {useAuth} from '../context/AuthContext'
import {useReconModules} from '../hooks/useReconModules'

function formatValue(value) {
    if (value == null || value === '') return '-'
    if (typeof value === 'object') return JSON.stringify(value)
    return String(value)
}

function formatDateTimeValue(value) {
    if (!value) return '-'
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? value.replace('T', ' ') : parsed.toLocaleString()
}

function formatLagValue(minutes, t) {
    if (minutes == null || minutes === 0) return t('Current')
    if (minutes < 60) return `${minutes} ${t('min')}`
    if (minutes < 1440) return `${(minutes / 60).toFixed(1)} ${t('hrs')}`
    return `${(minutes / 1440).toFixed(1)} ${t('days')}`
}

function healthChipStyles(value, palette) {
    switch ((value || '').toUpperCase()) {
        case 'CRITICAL':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'WARNING':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        case 'HEALTHY':
            return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
        default:
            return {backgroundColor: palette.cardBgAlt, color: palette.textMuted}
    }
}

function freshnessChipStyles(value, palette) {
    switch ((value || '').toUpperCase()) {
        case 'STALE':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        case 'FRESH':
            return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
        default:
            return {backgroundColor: palette.cardBgAlt, color: palette.textMuted}
    }
}

function SummaryCard({label, value, supporting, tone, palette}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.65, fontSize: '1.55rem', fontWeight: 800, color: tone}}>{value}</Typography>
            <Typography sx={{mt: 0.45, fontSize: '0.76rem', color: palette.textMuted}}>{supporting}</Typography>
        </Paper>
    )
}

function OperationCard({
    module,
    palette,
    canExecuteSafe,
    canExecuteAdvanced,
    canResetCheckpoint,
    onRunSafe,
    onOpenAdvanced,
    savingAction,
    t,
}) {
    const status = module.status || {}
    const advancedActions = module.advancedActions || []
    const highlights = module.statusHighlights || []
    const affectedLabels = module.affectedReconLabels || []
    const isShared = module.sharedAsset && affectedLabels.length > 1

    return (
        <Paper
            elevation={0}
            sx={{
                p: 2.5,
                borderRadius: '22px',
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.cardBg,
                height: '100%',
            }}
        >
            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start', mb: 1.5, flexWrap: 'wrap'}}>
                <Box>
                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                        {module.moduleLabel}
                    </Typography>
                    <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                        {module.reconLabel || module.reconView}
                    </Typography>
                </Box>
                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap justifyContent="flex-end">
                    {module.endpointMode ? (
                        <Chip
                            size="small"
                            label={`${module.systemName || 'System'} ${module.endpointMode}`}
                            sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}
                        />
                    ) : null}
                    {isShared ? (
                        <Chip
                            size="small"
                            label={t('Shared Runtime')}
                            sx={{backgroundColor: '#FFF7ED', color: '#C2410C', fontWeight: 700}}
                        />
                    ) : null}
                    <Chip
                        size="small"
                        label={module.reachable ? t('Reachable') : t('Unavailable')}
                        sx={{
                            backgroundColor: module.reachable ? palette.tealChipBg : '#FEF2F2',
                            color: module.reachable ? palette.tealChipText : '#DC2626',
                            fontWeight: 700,
                        }}
                    />
                    <Chip size="small" label={module.healthStatus || t('Unknown')} sx={{fontWeight: 700, ...healthChipStyles(module.healthStatus, palette)}}/>
                    <Chip size="small" label={module.freshnessStatus || t('Freshness')} sx={{fontWeight: 700, ...freshnessChipStyles(module.freshnessStatus, palette)}}/>
                </Stack>
            </Box>

            {isShared ? (
                <Alert severity="warning" sx={{mb: 1.8}}>
                    {t('This connector runtime is shared with')}: {affectedLabels.join(', ')}
                </Alert>
            ) : null}

            <Box sx={{display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1, mb: 2}}>
                {[
                    {label: t('Health Score'), value: module.healthScore != null ? `${module.healthScore}/100` : '-'},
                    {label: t('Freshness Lag'), value: formatLagValue(module.freshnessLagMinutes, t)},
                    {label: t('Backlog'), value: module.backlogCount || 0},
                    {label: t('Active Cases'), value: module.activeCaseCount || 0},
                    {label: t('Breached Cases'), value: module.breachedCaseCount || 0},
                    {label: t('Last Success'), value: formatDateTimeValue(module.lastSuccessfulSyncAt)},
                ].map((item) => (
                    <Box key={item.label} sx={{p: 1.2, borderRadius: 2, backgroundColor: palette.cardBgAlt, border: `1px solid ${palette.borderSoft}`}}>
                        <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                            {item.label}
                        </Typography>
                        <Typography sx={{mt: 0.25, fontSize: '0.84rem', color: palette.text, fontWeight: 700}}>
                            {item.value}
                        </Typography>
                    </Box>
                ))}
            </Box>

            {module.recommendedAction ? (
                <Box sx={{mb: 1.8, p: 1.15, borderRadius: 2.5, backgroundColor: palette.cardBgAlt, border: `1px solid ${palette.borderSoft}`}}>
                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted, fontWeight: 700}}>
                        {t('Recommended Action')}
                    </Typography>
                    <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.text}}>
                        {module.recommendedAction}
                    </Typography>
                </Box>
            ) : null}

            {highlights.length ? (
                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{mb: 1.8}}>
                    {highlights.map((highlight, index) => (
                        <Chip key={`${highlight}-${index}`} size="small" label={highlight} sx={{backgroundColor: palette.cardBgAlt, color: palette.textMuted}}/>
                    ))}
                </Stack>
            ) : null}

            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, fontWeight: 700, mb: 1}}>
                {t('Safe Actions')}
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{mb: advancedActions.length ? 2 : 0}}>
                {(module.availableActions || []).map((action) => {
                    const busy = savingAction === `${module.reconView}:${module.moduleId}:${action}`
                    return (
                        <Button
                            key={action}
                            size="small"
                            variant="contained"
                            onClick={() => onRunSafe(module, action)}
                            disabled={!canExecuteSafe || !module.reachable || busy}
                        >
                            {busy ? t('Running...') : action}
                        </Button>
                    )
                })}
            </Stack>

            {advancedActions.length > 0 && (
                <>
                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, fontWeight: 700, mb: 1}}>
                        {t('Advanced Actions')}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{mb: 2}}>
                        {advancedActions.map((action) => {
                            const disabled = !module.reachable
                                || (action === 'reset-checkpoint' ? !canResetCheckpoint : !canExecuteAdvanced)
                            return (
                                <Button
                                    key={action}
                                    size="small"
                                    variant="outlined"
                                    color="secondary"
                                    onClick={() => onOpenAdvanced(module, action)}
                                    disabled={disabled}
                                >
                                    {action}
                                </Button>
                            )
                        })}
                    </Stack>
                </>
            )}

            {Object.entries(status).length ? (
                <>
                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, fontWeight: 700, mb: 1}}>
                        {t('Connector Status')}
                    </Typography>
                    <Box sx={{display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1}}>
                        {Object.entries(status).slice(0, 6).map(([key, value]) => (
                            <Box key={key} sx={{p: 1.1, borderRadius: 2, backgroundColor: palette.cardBg, border: `1px solid ${palette.border}`}}>
                                <Typography sx={{fontSize: '0.7rem', color: palette.textMuted, fontWeight: 700}}>
                                    {key}
                                </Typography>
                                <Typography sx={{mt: 0.2, fontSize: '0.8rem', color: palette.text}}>
                                    {formatValue(value)}
                                </Typography>
                            </Box>
                        ))}
                    </Box>
                </>
            ) : null}
        </Paper>
    )
}

function AdvancedActionDialog({
    open,
    module,
    actionKey,
    form,
    onChange,
    onClose,
    onConfirm,
    submitting,
    t,
}) {
    if (!open || !module || !actionKey) {
        return null
    }

    const isReset = actionKey === 'reset-checkpoint'
    const isReplay = actionKey === 'replay-window'
    const title = isReset ? t('Reset Checkpoint') : t('Replay Window')
    const confirmLabel = isReset ? t('Confirm Reset') : t('Queue Replay')

    return (
        <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
            <DialogTitle>{title} - {module.moduleLabel}</DialogTitle>
            <DialogContent dividers>
                <Alert severity="warning" sx={{mb: 2}}>
                    {isReset
                        ? t('This action changes the stored checkpoint and may cause already-processed data to be re-read or re-published.')
                        : t('This action re-queues staged records in the selected business-date window for replay.')}
                </Alert>
                {module.sharedAsset && (module.affectedReconLabels || []).length > 1 ? (
                    <Alert severity="warning" sx={{mb: 2}}>
                        {t('Shared runtime impact')}: {(module.affectedReconLabels || []).join(', ')}
                    </Alert>
                ) : null}

                {isReset && (
                    <Stack spacing={1.5}>
                        <FormControl fullWidth size="small">
                            <InputLabel>{t('Reset Mode')}</InputLabel>
                            <Select
                                value={form.resetMode}
                                label={t('Reset Mode')}
                                onChange={(event) => onChange('resetMode', event.target.value)}
                            >
                                <MenuItem value="BUSINESS_DATE">{t('Business Date')}</MenuItem>
                                <MenuItem value="TIMESTAMP">{t('Timestamp')}</MenuItem>
                            </Select>
                        </FormControl>
                        {form.resetMode === 'BUSINESS_DATE' ? (
                            <TextField
                                label={t('Business Date')}
                                type="date"
                                value={form.businessDate}
                                InputLabelProps={{shrink: true}}
                                onChange={(event) => onChange('businessDate', event.target.value)}
                            />
                        ) : (
                            <TextField
                                label={t('Timestamp (UTC)')}
                                placeholder="2026-03-14T00:00:00Z"
                                value={form.timestamp}
                                onChange={(event) => onChange('timestamp', event.target.value)}
                            />
                        )}
                        {module.resetPayloadMode === 'DB_POLLING' ? (
                            <>
                                <TextField
                                    label={t('Last Processed External Id')}
                                    value={form.externalId}
                                    onChange={(event) => onChange('externalId', event.target.value)}
                                />
                                <TextField
                                    label={t('Last Processed Id')}
                                    type="number"
                                    value={form.processedId}
                                    onChange={(event) => onChange('processedId', event.target.value)}
                                />
                            </>
                        ) : (
                            <TextField
                                label={t('Cursor Id')}
                                type="number"
                                value={form.cursorId}
                                onChange={(event) => onChange('cursorId', event.target.value)}
                            />
                        )}
                    </Stack>
                )}

                {isReplay && (
                    <Stack spacing={1.5}>
                        <FormControl fullWidth size="small">
                            <InputLabel>{t('Replay Mode')}</InputLabel>
                            <Select
                                value={form.replayMode}
                                label={t('Replay Mode')}
                                onChange={(event) => onChange('replayMode', event.target.value)}
                            >
                                <MenuItem value="STAGED_REPUBLISH">{t('Staged Republish')}</MenuItem>
                            </Select>
                        </FormControl>
                        <Grid container spacing={1.5}>
                            <Grid size={{xs: 12, md: 6}}>
                                <TextField
                                    fullWidth
                                    label={t('From Business Date')}
                                    type="date"
                                    value={form.fromBusinessDate}
                                    InputLabelProps={{shrink: true}}
                                    onChange={(event) => onChange('fromBusinessDate', event.target.value)}
                                />
                            </Grid>
                            <Grid size={{xs: 12, md: 6}}>
                                <TextField
                                    fullWidth
                                    label={t('To Business Date')}
                                    type="date"
                                    value={form.toBusinessDate}
                                    InputLabelProps={{shrink: true}}
                                    onChange={(event) => onChange('toBusinessDate', event.target.value)}
                                />
                            </Grid>
                        </Grid>
                        <TextField
                            label={t('Store Id')}
                            value={form.storeId}
                            onChange={(event) => onChange('storeId', event.target.value)}
                        />
                        {module.supportsRegisterFilter && (
                            <TextField
                                label={t('Register Id')}
                                value={form.wkstnId}
                                onChange={(event) => onChange('wkstnId', event.target.value)}
                            />
                        )}
                        {!module.supportsRegisterFilter && (
                            <Alert severity="info">
                                {t('This replay window supports business-date and store filtering from staged data. Register filtering is not available for this connector.')}
                            </Alert>
                        )}
                    </Stack>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={submitting}>{t('Cancel')}</Button>
                <Button
                    variant="contained"
                    onClick={onConfirm}
                    disabled={submitting}
                    sx={{backgroundColor: isReset ? '#D97706' : undefined}}
                >
                    {submitting ? t('Running...') : confirmLabel}
                </Button>
            </DialogActions>
        </Dialog>
    )
}

export default function Operations({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptions, labelByValue} = useReconModules()
    const canExecuteSafe = hasPermission('OPS_EXECUTE_SAFE')
    const canExecuteAdvanced = hasPermission('OPS_EXECUTE_ADVANCED')
    const canResetCheckpoint = hasPermission('OPS_CHECKPOINT_RESET')
    const [selectedReconView, setSelectedReconView] = useState('')
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState('')
    const [savingAction, setSavingAction] = useState('')
    const [dialogState, setDialogState] = useState({
        open: false,
        module: null,
        actionKey: '',
    })
    const [advancedForm, setAdvancedForm] = useState({
        resetMode: 'BUSINESS_DATE',
        timestamp: '',
        businessDate: '',
        externalId: '',
        processedId: '',
        cursorId: '',
        replayMode: 'STAGED_REPUBLISH',
        fromBusinessDate: '',
        toBusinessDate: '',
        storeId: '',
        wkstnId: '',
    })

    const dialogOpen = dialogState.open
    const dialogModule = dialogState.module
    const dialogActionKey = dialogState.actionKey
    const submittingAdvanced = Boolean(savingAction) && savingAction.startsWith('advanced:')
    const summary = data?.summary || {}
    const selectedReconLabel = labelByValue[selectedReconView] || moduleOptions[0]?.label || ''

    const loadOperations = async (reconView = selectedReconView) => {
        try {
            setLoading(true)
            setError('')
            setData(await operationsApi.getOperations(reconView || null))
        } catch (err) {
            setError(err.message || 'Failed to load operations')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (!moduleOptions.length) {
            setSelectedReconView('')
            return
        }
        if (!moduleOptions.some((option) => option.value === selectedReconView)) {
            setSelectedReconView(moduleOptions[0].value)
        }
    }, [moduleOptions, selectedReconView])

    useEffect(() => {
        if (moduleOptions.length && !selectedReconView) {
            return
        }
        loadOperations(selectedReconView)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedReconView, moduleOptions.length])

    const runSafeAction = async (module, actionKey) => {
        try {
            setSavingAction(`${module.reconView}:${module.moduleId}:${actionKey}`)
            setError('')
            setFeedback('')
            const response = await operationsApi.executeAction(module.moduleId, actionKey, module.reconView)
            setFeedback(response.message || 'Action completed.')
            await loadOperations()
        } catch (err) {
            setError(err.message || 'Failed to execute operation')
        } finally {
            setSavingAction('')
        }
    }

    const openAdvancedDialog = (module, actionKey) => {
        setFeedback('')
        setError('')
        setDialogState({open: true, module, actionKey})
        setAdvancedForm({
            resetMode: 'BUSINESS_DATE',
            timestamp: '',
            businessDate: '',
            externalId: '',
            processedId: '',
            cursorId: module?.status?.lastCursorId ?? '',
            replayMode: 'STAGED_REPUBLISH',
            fromBusinessDate: '',
            toBusinessDate: '',
            storeId: '',
            wkstnId: '',
        })
    }

    const closeAdvancedDialog = () => {
        if (submittingAdvanced) return
        setDialogState({open: false, module: null, actionKey: ''})
    }

    const handleAdvancedFormChange = (key, value) => {
        setAdvancedForm((current) => ({...current, [key]: value}))
    }

    const confirmAdvancedAction = async () => {
        if (!dialogModule || !dialogActionKey) return
        try {
            setSavingAction(`advanced:${dialogModule.reconView}:${dialogModule.moduleId}:${dialogActionKey}`)
            setError('')
            setFeedback('')
            let response
            if (dialogActionKey === 'reset-checkpoint') {
                response = await operationsApi.resetCheckpoint(dialogModule.moduleId, {
                    resetMode: advancedForm.resetMode,
                    timestamp: advancedForm.timestamp,
                    businessDate: advancedForm.businessDate,
                    externalId: advancedForm.externalId,
                    processedId: advancedForm.processedId === '' ? null : Number(advancedForm.processedId),
                    cursorId: advancedForm.cursorId === '' ? null : Number(advancedForm.cursorId),
                }, dialogModule.reconView)
            } else {
                response = await operationsApi.replayWindow(dialogModule.moduleId, {
                    replayMode: advancedForm.replayMode,
                    fromBusinessDate: advancedForm.fromBusinessDate,
                    toBusinessDate: advancedForm.toBusinessDate,
                    storeId: advancedForm.storeId,
                    wkstnId: advancedForm.wkstnId,
                }, dialogModule.reconView)
            }
            setFeedback(response.message || 'Advanced action completed.')
            closeAdvancedDialog()
            await loadOperations()
        } catch (err) {
            setError(err.message || 'Failed to execute advanced operation')
        } finally {
            setSavingAction('')
        }
    }

    const infoMessage = useMemo(() => {
        if (!canExecuteSafe && !canExecuteAdvanced && !canResetCheckpoint) {
            return t('You have view-only access. Execution actions require operations execution permissions.')
        }
        if (!canExecuteAdvanced || !canResetCheckpoint) {
            return t('Advanced actions are protected. Checkpoint reset and replay window require dedicated permissions and confirmation.')
        }
        return ''
    }, [canExecuteAdvanced, canExecuteSafe, canResetCheckpoint, t])

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5, flexWrap: 'wrap'}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#F5F3FF', color: '#7C3AED', flexShrink: 0}}>
                        <BuildCircleRoundedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>
                            {t('Operations')}
                        </Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 940}}>
                            {t('Proactively monitor connector health, data freshness, backlog risk, and the case pressure created by unhealthy integrations.')}
                        </Typography>
                    </Box>
                    <Box sx={{minWidth: {xs: '100%', md: 260}, ml: 'auto'}}>
                        <FormControl fullWidth size="small">
                            <InputLabel>{t('Reconciliation Lane')}</InputLabel>
                            <Select
                                value={selectedReconView}
                                label={t('Reconciliation Lane')}
                                onChange={(event) => setSelectedReconView(event.target.value)}
                                disabled={!moduleOptions.length}
                            >
                                {moduleOptions.map((option) => (
                                    <MenuItem key={option.value} value={option.value}>
                                        {option.label}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Box>
                </Box>
            </Paper>

            {selectedReconLabel ? (
                <Alert severity="info" sx={{mb: 2}}>
                    {t('Showing lane-scoped operations for')} {selectedReconLabel}. {t('Shared runtime warnings appear on modules that can affect other allowed lanes.')}
                </Alert>
            ) : null}
            {infoMessage ? (
                <Alert severity="info" sx={{mb: 2}}>
                    {infoMessage}
                </Alert>
            ) : null}
            {feedback ? <Alert severity="success" sx={{mb: 2}}>{feedback}</Alert> : null}
            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}>
                    <CircularProgress/>
                </Box>
            ) : (
                <>
                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(6, minmax(0, 1fr))'}, gap: 1.5, mb: 3}}>
                        <SummaryCard label={t('Healthy Modules')} value={summary.healthyModules || 0} supporting={t('No material connector risk')} tone={palette.tealChipText} palette={palette}/>
                        <SummaryCard label={t('Warning Modules')} value={summary.warningModules || 0} supporting={t('Need monitoring or queue attention')} tone="#D97706" palette={palette}/>
                        <SummaryCard label={t('Critical Modules')} value={summary.criticalModules || 0} supporting={t('Direct operational risk')} tone="#B91C1C" palette={palette}/>
                        <SummaryCard label={t('Stale Modules')} value={summary.staleModules || 0} supporting={t('Data freshness has slipped')} tone={palette.blueChipText} palette={palette}/>
                        <SummaryCard label={t('Total Backlog')} value={summary.totalBacklogCount || 0} supporting={t('Connector records waiting to clear')} tone="#7C3AED" palette={palette}/>
                        <SummaryCard label={t('Cases On Unhealthy Modules')} value={summary.activeCasesOnUnhealthyModules || 0} supporting={`${summary.breachedCasesOnUnhealthyModules || 0} ${t('breached')}`} tone={palette.text} palette={palette}/>
                    </Box>

                    <Grid container spacing={2}>
                        {(data?.modules || []).map((module) => (
                            <Grid size={{xs: 12, md: 6}} key={`${module.reconView}:${module.moduleId}`}>
                                <OperationCard
                                    module={module}
                                    palette={palette}
                                    canExecuteSafe={canExecuteSafe}
                                    canExecuteAdvanced={canExecuteAdvanced}
                                    canResetCheckpoint={canResetCheckpoint}
                                    onRunSafe={runSafeAction}
                                    onOpenAdvanced={openAdvancedDialog}
                                    savingAction={savingAction}
                                    t={t}
                                />
                            </Grid>
                        ))}
                    </Grid>
                </>
            )}

            <AdvancedActionDialog
                open={dialogOpen}
                module={dialogModule}
                actionKey={dialogActionKey}
                form={advancedForm}
                onChange={handleAdvancedFormChange}
                onClose={closeAdvancedDialog}
                onConfirm={confirmAdvancedAction}
                submitting={submittingAdvanced}
                t={t}
            />
        </Box>
    )
}
