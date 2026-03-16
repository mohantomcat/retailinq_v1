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

function formatValue(value) {
    if (value == null || value === '') return '-'
    if (typeof value === 'object') return JSON.stringify(value)
    return String(value)
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
            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start', mb: 1.5}}>
                <Box>
                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                        {module.moduleLabel}
                    </Typography>
                    <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                        {module.reconView}
                    </Typography>
                </Box>
                <Chip
                    size="small"
                    label={module.reachable ? t('Reachable') : t('Unavailable')}
                    sx={{
                        backgroundColor: module.reachable ? palette.tealChipBg : '#FEF2F2',
                        color: module.reachable ? palette.tealChipText : '#DC2626',
                        fontWeight: 700,
                    }}
                />
            </Box>

            <Box sx={{display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1, mb: 2}}>
                {Object.entries(status).slice(0, 6).map(([key, value]) => (
                    <Box key={key} sx={{p: 1.2, borderRadius: 2, backgroundColor: palette.cardBgAlt, border: `1px solid ${palette.borderSoft}`}}>
                        <Typography sx={{fontSize: '0.72rem', color: palette.textMuted, fontWeight: 700}}>
                            {key}
                        </Typography>
                        <Typography sx={{mt: 0.25, fontSize: '0.84rem', color: palette.text, fontWeight: 600}}>
                            {formatValue(value)}
                        </Typography>
                    </Box>
                ))}
            </Box>

            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, fontWeight: 700, mb: 1}}>
                {t('Safe Actions')}
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{mb: advancedActions.length ? 2 : 0}}>
                {(module.availableActions || []).map((action) => {
                    const busy = savingAction === `${module.moduleId}:${action}`
                    return (
                        <Button
                            key={action}
                            size="small"
                            variant="contained"
                            onClick={() => onRunSafe(module.moduleId, action)}
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
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
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
    palette,
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
                        {module.moduleId === 'sim-poller' ? (
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
                            <Grid item xs={12} md={6}>
                                <TextField
                                    fullWidth
                                    label={t('From Business Date')}
                                    type="date"
                                    value={form.fromBusinessDate}
                                    InputLabelProps={{shrink: true}}
                                    onChange={(event) => onChange('fromBusinessDate', event.target.value)}
                                />
                            </Grid>
                            <Grid item xs={12} md={6}>
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
                        {module.moduleId === 'xocs-cloud-connector' && (
                            <TextField
                                label={t('Register Id')}
                                value={form.wkstnId}
                                onChange={(event) => onChange('wkstnId', event.target.value)}
                            />
                        )}
                        {module.moduleId === 'siocs-cloud-connector' && (
                            <Alert severity="info">
                                {t('SIOCS replay window supports business-date and store filtering from staged data. Register filtering is not available for this connector.')}
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
    const canExecuteSafe = hasPermission('OPS_EXECUTE_SAFE')
    const canExecuteAdvanced = hasPermission('OPS_EXECUTE_ADVANCED')
    const canResetCheckpoint = hasPermission('OPS_CHECKPOINT_RESET')
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

    const loadOperations = async () => {
        try {
            setLoading(true)
            setError('')
            setData(await operationsApi.getOperations())
        } catch (err) {
            setError(err.message || 'Failed to load operations')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadOperations()
    }, [])

    const runSafeAction = async (moduleId, actionKey) => {
        try {
            setSavingAction(`${moduleId}:${actionKey}`)
            setError('')
            setFeedback('')
            const response = await operationsApi.executeAction(moduleId, actionKey)
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
            setSavingAction(`advanced:${dialogModule.moduleId}:${dialogActionKey}`)
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
                })
            } else {
                response = await operationsApi.replayWindow(dialogModule.moduleId, {
                    replayMode: advancedForm.replayMode,
                    fromBusinessDate: advancedForm.fromBusinessDate,
                    toBusinessDate: advancedForm.toBusinessDate,
                    storeId: advancedForm.storeId,
                    wkstnId: advancedForm.wkstnId,
                })
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
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#F5F3FF', color: '#7C3AED', flexShrink: 0}}>
                        <BuildCircleRoundedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>
                            {t('Operations')}
                        </Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 920}}>
                            {t('View connector status, checkpoints, and trigger guided operational actions with confirmation, permissions, and audit history.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

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
                <Grid container spacing={2}>
                    {(data?.modules || []).map((module) => (
                        <Grid item xs={12} md={6} key={module.moduleId}>
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
                palette={palette}
                t={t}
            />
        </Box>
    )
}
