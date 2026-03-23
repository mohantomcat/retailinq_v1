import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
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
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {RECON_VIEW_OPTIONS_WITH_ALL} from '../constants/reconViews'

const MODULE_OPTIONS = RECON_VIEW_OPTIONS_WITH_ALL

const REQUEST_STATUS_OPTIONS = [
    {value: 'ALL', label: 'All'},
    {value: 'PENDING', label: 'Pending'},
    {value: 'APPROVED', label: 'Approved'},
    {value: 'REJECTED', label: 'Rejected'},
]

const TARGET_STATUS_OPTIONS = ['RESOLVED', 'IGNORED']
const SEVERITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

const EMPTY_POLICY = {
    id: null,
    policyName: '',
    reconView: 'XSTORE_SIOCS',
    targetStatus: 'RESOLVED',
    minSeverity: 'HIGH',
    requireReasonCode: true,
    requireRootCauseCategory: true,
    requireNotes: true,
    requireComment: false,
    requireApproval: true,
    approverRoleName: '',
    active: true,
    description: '',
}

const EMPTY_ESCALATION_POLICY = {
    id: null,
    policyName: '',
    reconView: 'XSTORE_SIOCS',
    minSeverity: 'HIGH',
    minImpactScore: 70,
    triggerOnSlaBreach: true,
    agingHours: 24,
    inactivityHours: '',
    escalateToUsername: '',
    escalateToRoleName: '',
    targetSeverity: 'CRITICAL',
    noteTemplate: 'Escalate to the next support tier and update ownership.',
    active: true,
    description: '',
}

function SummaryCard({label, value, palette, tone = 'blue'}) {
    const styles = {
        blue: palette.blueChipText,
        teal: palette.tealChipText,
        red: '#DC2626',
        amber: '#D97706',
        purple: '#7C3AED',
    }

    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.7rem', fontWeight: 800, color: styles[tone]}}>{value}</Typography>
        </Paper>
    )
}

export default function ApprovalCenter({palette, t}) {
    const {hasPermission} = useAuth()
    const canView = hasPermission('EXCEPTION_APPROVAL_VIEW')
    const canApprove = hasPermission('EXCEPTION_APPROVAL_EDIT')
    const canEditPolicies = hasPermission('EXCEPTION_POLICY_EDIT')

    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [data, setData] = useState(null)
    const [escalationCenter, setEscalationCenter] = useState(null)
    const [filters, setFilters] = useState({
        reconView: '',
        requestStatus: 'ALL',
        search: '',
    })
    const [assignmentOptions, setAssignmentOptions] = useState({usernames: [], roleNames: []})
    const [policyForm, setPolicyForm] = useState(EMPTY_POLICY)
    const [escalationPolicyForm, setEscalationPolicyForm] = useState(EMPTY_ESCALATION_POLICY)
    const [decisionDialog, setDecisionDialog] = useState({
        open: false,
        request: null,
        decision: 'APPROVED',
        decisionNotes: '',
    })

    const load = async (nextFilters = filters) => {
        try {
            setLoading(true)
            setError('')
            const [centerData, options, escalationData] = await Promise.all([
                exceptionApi.getApprovalCenter(nextFilters),
                exceptionApi.getAssignmentOptions(),
                exceptionApi.getEscalationPolicyCenter({reconView: nextFilters.reconView}),
            ])
            setData(centerData)
            setAssignmentOptions(options || {usernames: [], roleNames: []})
            setEscalationCenter(escalationData || null)
        } catch (err) {
            setError(err.message || t('Failed to load approval center'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (canView) {
            load(filters)
        }
    }, [canView])

    const pendingApprovals = useMemo(() => data?.pendingApprovals || [], [data])
    const recentDecisions = useMemo(() => data?.recentDecisions || [], [data])
    const policies = useMemo(() => data?.closurePolicies || [], [data])
    const escalationPolicies = useMemo(() => escalationCenter?.policies || [], [escalationCenter])

    if (!canView) {
        return null
    }

    const handleSavePolicy = async () => {
        setSaving(true)
        setError('')
        try {
            const payload = {
                policyName: policyForm.policyName,
                reconView: policyForm.reconView,
                targetStatus: policyForm.targetStatus,
                minSeverity: policyForm.minSeverity,
                requireReasonCode: policyForm.requireReasonCode,
                requireRootCauseCategory: policyForm.requireRootCauseCategory,
                requireNotes: policyForm.requireNotes,
                requireComment: policyForm.requireComment,
                requireApproval: policyForm.requireApproval,
                approverRoleName: policyForm.approverRoleName,
                active: policyForm.active,
                description: policyForm.description,
            }
            if (policyForm.id) {
                await exceptionApi.updateClosurePolicy(policyForm.id, payload)
            } else {
                await exceptionApi.createClosurePolicy(payload)
            }
            setPolicyForm(EMPTY_POLICY)
            await load(filters)
        } catch (err) {
            setError(err.message || t('Failed to save workflow policy'))
        } finally {
            setSaving(false)
        }
    }

    const handleDeletePolicy = async (policy) => {
        setSaving(true)
        setError('')
        try {
            await exceptionApi.deleteClosurePolicy({
                policyId: policy.id,
                reconView: policy.reconView,
            })
            if (policyForm.id === policy.id) {
                setPolicyForm(EMPTY_POLICY)
            }
            await load(filters)
        } catch (err) {
            setError(err.message || t('Failed to delete workflow policy'))
        } finally {
            setSaving(false)
        }
    }

    const handleSaveEscalationPolicy = async () => {
        setSaving(true)
        setError('')
        try {
            const payload = {
                policyName: escalationPolicyForm.policyName,
                reconView: escalationPolicyForm.reconView,
                minSeverity: escalationPolicyForm.minSeverity,
                minImpactScore: escalationPolicyForm.minImpactScore ? Number(escalationPolicyForm.minImpactScore) : null,
                triggerOnSlaBreach: escalationPolicyForm.triggerOnSlaBreach,
                agingHours: escalationPolicyForm.agingHours ? Number(escalationPolicyForm.agingHours) : null,
                inactivityHours: escalationPolicyForm.inactivityHours ? Number(escalationPolicyForm.inactivityHours) : null,
                escalateToUsername: escalationPolicyForm.escalateToUsername,
                escalateToRoleName: escalationPolicyForm.escalateToRoleName,
                targetSeverity: escalationPolicyForm.targetSeverity,
                noteTemplate: escalationPolicyForm.noteTemplate,
                active: escalationPolicyForm.active,
                description: escalationPolicyForm.description,
            }
            if (escalationPolicyForm.id) {
                await exceptionApi.updateEscalationPolicy(escalationPolicyForm.id, payload)
            } else {
                await exceptionApi.createEscalationPolicy(payload)
            }
            setEscalationPolicyForm(EMPTY_ESCALATION_POLICY)
            await load(filters)
        } catch (err) {
            setError(err.message || t('Failed to save escalation policy'))
        } finally {
            setSaving(false)
        }
    }

    const handleDeleteEscalationPolicy = async (policy) => {
        setSaving(true)
        setError('')
        try {
            await exceptionApi.deleteEscalationPolicy({
                policyId: policy.id,
                reconView: policy.reconView,
            })
            if (escalationPolicyForm.id === policy.id) {
                setEscalationPolicyForm(EMPTY_ESCALATION_POLICY)
            }
            await load(filters)
        } catch (err) {
            setError(err.message || t('Failed to delete escalation policy'))
        } finally {
            setSaving(false)
        }
    }

    const openDecision = (request, decision) => {
        setDecisionDialog({
            open: true,
            request,
            decision,
            decisionNotes: '',
        })
    }

    const handleDecision = async () => {
        if (!decisionDialog.request) {
            return
        }
        setSaving(true)
        setError('')
        try {
            await exceptionApi.decideApproval({
                requestId: decisionDialog.request.id,
                decision: decisionDialog.decision,
                decisionNotes: decisionDialog.decisionNotes,
            })
            setDecisionDialog({open: false, request: null, decision: 'APPROVED', decisionNotes: ''})
            await load(filters)
        } catch (err) {
            setError(err.message || t('Failed to submit approval decision'))
        } finally {
            setSaving(false)
        }
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.tealChipBg, color: palette.tealChipText, flexShrink: 0}}>
                        <FactCheckOutlinedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Approval Center')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 920}}>
                            {t('Control high-sensitivity exception workflows with approval routing, policy-driven closure rules, and timed escalation policies for overdue or high-impact cases.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            <Grid container spacing={2} sx={{mb: 3}}>
                <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Pending Approvals')} value={data?.summary?.pendingApprovals || 0} tone="amber" palette={palette}/></Grid>
                <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Overdue Pending')} value={data?.summary?.overduePendingApprovals || 0} tone="red" palette={palette}/></Grid>
                <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Approved 7 Days')} value={data?.summary?.approvedLast7Days || 0} tone="teal" palette={palette}/></Grid>
                <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Rejected 7 Days')} value={data?.summary?.rejectedLast7Days || 0} tone="purple" palette={palette}/></Grid>
                <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Active Closure Policies')} value={data?.summary?.activePolicies || 0} tone="blue" palette={palette}/></Grid>
                <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Escalated Cases')} value={escalationCenter?.summary?.currentlyEscalatedCases || 0} tone="red" palette={palette}/></Grid>
                <Grid item xs={12} md={6} lg={2.4}><SummaryCard label={t('Active Escalation Policies')} value={escalationCenter?.summary?.activePolicies || 0} tone="amber" palette={palette}/></Grid>
            </Grid>

            <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr)) auto'}, gap: 1.5, alignItems: 'start'}}>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Module')}</InputLabel>
                        <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}>
                            {MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <FormControl size="small" fullWidth>
                        <InputLabel>{t('Request Status')}</InputLabel>
                        <Select value={filters.requestStatus} label={t('Request Status')} onChange={(event) => setFilters((current) => ({...current, requestStatus: event.target.value}))}>
                            {REQUEST_STATUS_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <TextField
                        size="small"
                        label={t('Search')}
                        value={filters.search}
                        onChange={(event) => setFilters((current) => ({...current, search: event.target.value}))}
                    />
                    <Stack direction={{xs: 'column', sm: 'row'}} spacing={1} justifyContent="flex-end">
                        <Button variant="contained" sx={{minWidth: 112, height: 40}} onClick={() => load(filters)} disabled={loading}>
                            {t('Apply')}
                        </Button>
                        <Button
                            variant="outlined"
                            sx={{minWidth: 96, height: 40}}
                            onClick={() => {
                                const cleared = {reconView: '', requestStatus: 'ALL', search: ''}
                                setFilters(cleared)
                                load(cleared)
                            }}
                        >
                            {t('Clear')}
                        </Button>
                    </Stack>
                </Box>
            </Paper>

            <Grid container spacing={3}>
                <Grid item xs={12} xl={4.4}>
                    <Stack spacing={3}>
                        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                {t('Workflow Policies')}
                            </Typography>
                            <Stack spacing={1.5}>
                                <TextField
                                    label={t('Policy Name')}
                                    value={policyForm.policyName}
                                    onChange={(event) => setPolicyForm((current) => ({...current, policyName: event.target.value}))}
                                    disabled={!canEditPolicies || saving}
                                />
                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Module')}</InputLabel>
                                        <Select value={policyForm.reconView} label={t('Module')} onChange={(event) => setPolicyForm((current) => ({...current, reconView: event.target.value}))} disabled={!canEditPolicies || saving}>
                                            {MODULE_OPTIONS.filter((option) => option.value).map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Target Status')}</InputLabel>
                                        <Select value={policyForm.targetStatus} label={t('Target Status')} onChange={(event) => setPolicyForm((current) => ({...current, targetStatus: event.target.value}))} disabled={!canEditPolicies || saving}>
                                            {TARGET_STATUS_OPTIONS.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                </Box>

                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Minimum Severity')}</InputLabel>
                                        <Select value={policyForm.minSeverity} label={t('Minimum Severity')} onChange={(event) => setPolicyForm((current) => ({...current, minSeverity: event.target.value}))} disabled={!canEditPolicies || saving}>
                                            {SEVERITY_OPTIONS.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Approver Role')}</InputLabel>
                                        <Select value={policyForm.approverRoleName} label={t('Approver Role')} onChange={(event) => setPolicyForm((current) => ({...current, approverRoleName: event.target.value}))} disabled={!canEditPolicies || saving || !policyForm.requireApproval}>
                                            <MenuItem value="">{t('None')}</MenuItem>
                                            {assignmentOptions.roleNames.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                </Box>

                                <Paper elevation={0} sx={{p: 1.5, borderRadius: 3, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.paperBg}}>
                                    <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted, textTransform: 'uppercase', mb: 1}}>
                                        {t('Requirements')}
                                    </Typography>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))'}, gap: 0.5}}>
                                        <FormControlLabel control={<Switch checked={policyForm.requireReasonCode} onChange={(event) => setPolicyForm((current) => ({...current, requireReasonCode: event.target.checked}))} disabled={!canEditPolicies || saving}/>} label={t('Reason Code')} />
                                        <FormControlLabel control={<Switch checked={policyForm.requireRootCauseCategory} onChange={(event) => setPolicyForm((current) => ({...current, requireRootCauseCategory: event.target.checked}))} disabled={!canEditPolicies || saving}/>} label={t('Root Cause')} />
                                        <FormControlLabel control={<Switch checked={policyForm.requireNotes} onChange={(event) => setPolicyForm((current) => ({...current, requireNotes: event.target.checked}))} disabled={!canEditPolicies || saving}/>} label={t('Resolution Notes')} />
                                        <FormControlLabel control={<Switch checked={policyForm.requireComment} onChange={(event) => setPolicyForm((current) => ({...current, requireComment: event.target.checked}))} disabled={!canEditPolicies || saving}/>} label={t('Closure Comment')} />
                                        <FormControlLabel control={<Switch checked={policyForm.requireApproval} onChange={(event) => setPolicyForm((current) => ({...current, requireApproval: event.target.checked, approverRoleName: event.target.checked ? current.approverRoleName : ''}))} disabled={!canEditPolicies || saving}/>} label={t('Approval Required')} />
                                        <FormControlLabel control={<Switch checked={policyForm.active} onChange={(event) => setPolicyForm((current) => ({...current, active: event.target.checked}))} disabled={!canEditPolicies || saving}/>} label={t('Active')} />
                                    </Box>
                                </Paper>

                                <TextField
                                    label={t('Description')}
                                    value={policyForm.description}
                                    onChange={(event) => setPolicyForm((current) => ({...current, description: event.target.value}))}
                                    multiline
                                    minRows={3}
                                    disabled={!canEditPolicies || saving}
                                />

                                <Stack direction={{xs: 'column', sm: 'row'}} spacing={1.25} justifyContent="space-between">
                                    <Button variant="outlined" onClick={() => setPolicyForm(EMPTY_POLICY)} disabled={saving}>
                                        {t('Clear')}
                                    </Button>
                                    {canEditPolicies && (
                                        <Button variant="contained" onClick={handleSavePolicy} disabled={saving}>
                                            {policyForm.id ? t('Update Policy') : t('Create Policy')}
                                        </Button>
                                    )}
                                </Stack>
                            </Stack>
                        </Paper>

                        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                {t('Escalation Policies')}
                            </Typography>
                            <Stack spacing={1.5}>
                                <TextField
                                    label={t('Policy Name')}
                                    value={escalationPolicyForm.policyName}
                                    onChange={(event) => setEscalationPolicyForm((current) => ({...current, policyName: event.target.value}))}
                                    disabled={!canEditPolicies || saving}
                                />
                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Module')}</InputLabel>
                                        <Select value={escalationPolicyForm.reconView} label={t('Module')} onChange={(event) => setEscalationPolicyForm((current) => ({...current, reconView: event.target.value}))} disabled={!canEditPolicies || saving}>
                                            {MODULE_OPTIONS.filter((option) => option.value).map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Target Severity')}</InputLabel>
                                        <Select value={escalationPolicyForm.targetSeverity} label={t('Target Severity')} onChange={(event) => setEscalationPolicyForm((current) => ({...current, targetSeverity: event.target.value}))} disabled={!canEditPolicies || saving}>
                                            <MenuItem value="">{t('No Change')}</MenuItem>
                                            {SEVERITY_OPTIONS.filter((option) => option !== 'LOW' && option !== 'MEDIUM').map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                </Box>
                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', sm: 'repeat(3, minmax(0, 1fr))'}, gap: 1.5}}>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Minimum Severity')}</InputLabel>
                                        <Select value={escalationPolicyForm.minSeverity} label={t('Minimum Severity')} onChange={(event) => setEscalationPolicyForm((current) => ({...current, minSeverity: event.target.value}))} disabled={!canEditPolicies || saving}>
                                            {SEVERITY_OPTIONS.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                    <TextField size="small" type="number" label={t('Min Impact Score')} value={escalationPolicyForm.minImpactScore} onChange={(event) => setEscalationPolicyForm((current) => ({...current, minImpactScore: event.target.value}))} disabled={!canEditPolicies || saving}/>
                                    <TextField size="small" type="number" label={t('Age Hours')} value={escalationPolicyForm.agingHours} onChange={(event) => setEscalationPolicyForm((current) => ({...current, agingHours: event.target.value}))} disabled={!canEditPolicies || saving}/>
                                </Box>
                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                    <TextField size="small" type="number" label={t('Inactivity Hours')} value={escalationPolicyForm.inactivityHours} onChange={(event) => setEscalationPolicyForm((current) => ({...current, inactivityHours: event.target.value}))} disabled={!canEditPolicies || saving}/>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Escalate To Team')}</InputLabel>
                                        <Select value={escalationPolicyForm.escalateToRoleName} label={t('Escalate To Team')} onChange={(event) => setEscalationPolicyForm((current) => ({...current, escalateToRoleName: event.target.value}))} disabled={!canEditPolicies || saving}>
                                            <MenuItem value="">{t('None')}</MenuItem>
                                            {assignmentOptions.roleNames.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                </Box>
                                <FormControl size="small" fullWidth>
                                    <InputLabel>{t('Escalate To User')}</InputLabel>
                                    <Select value={escalationPolicyForm.escalateToUsername} label={t('Escalate To User')} onChange={(event) => setEscalationPolicyForm((current) => ({...current, escalateToUsername: event.target.value}))} disabled={!canEditPolicies || saving}>
                                        <MenuItem value="">{t('None')}</MenuItem>
                                        {assignmentOptions.usernames.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                    </Select>
                                </FormControl>
                                <Paper elevation={0} sx={{p: 1.5, borderRadius: 3, border: `1px solid ${palette.borderSoft}`, backgroundColor: palette.paperBg}}>
                                    <Typography sx={{fontSize: '0.78rem', fontWeight: 700, color: palette.textMuted, textTransform: 'uppercase', mb: 1}}>
                                        {t('Escalation Triggers')}
                                    </Typography>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))'}, gap: 0.5}}>
                                        <FormControlLabel control={<Switch checked={escalationPolicyForm.triggerOnSlaBreach} onChange={(event) => setEscalationPolicyForm((current) => ({...current, triggerOnSlaBreach: event.target.checked}))} disabled={!canEditPolicies || saving}/>} label={t('SLA Breach')} />
                                        <FormControlLabel control={<Switch checked={escalationPolicyForm.active} onChange={(event) => setEscalationPolicyForm((current) => ({...current, active: event.target.checked}))} disabled={!canEditPolicies || saving}/>} label={t('Active')} />
                                    </Box>
                                </Paper>
                                <TextField
                                    label={t('Escalation Guidance')}
                                    value={escalationPolicyForm.noteTemplate}
                                    onChange={(event) => setEscalationPolicyForm((current) => ({...current, noteTemplate: event.target.value}))}
                                    multiline
                                    minRows={2}
                                    disabled={!canEditPolicies || saving}
                                />
                                <TextField
                                    label={t('Description')}
                                    value={escalationPolicyForm.description}
                                    onChange={(event) => setEscalationPolicyForm((current) => ({...current, description: event.target.value}))}
                                    multiline
                                    minRows={3}
                                    disabled={!canEditPolicies || saving}
                                />
                                <Stack direction={{xs: 'column', sm: 'row'}} spacing={1.25} justifyContent="space-between">
                                    <Button variant="outlined" onClick={() => setEscalationPolicyForm(EMPTY_ESCALATION_POLICY)} disabled={saving}>
                                        {t('Clear')}
                                    </Button>
                                    {canEditPolicies && (
                                        <Button variant="contained" onClick={handleSaveEscalationPolicy} disabled={saving}>
                                            {escalationPolicyForm.id ? t('Update Escalation Policy') : t('Create Escalation Policy')}
                                        </Button>
                                    )}
                                </Stack>
                            </Stack>
                        </Paper>
                    </Stack>
                </Grid>

                <Grid item xs={12} xl={7.6}>
                    <Stack spacing={3}>
                        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                {t('Pending Approvals')}
                            </Typography>
                            <TableContainer>
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('Transaction')}</TableCell>
                                            <TableCell>{t('Request')}</TableCell>
                                            <TableCell>{t('Policy')}</TableCell>
                                            <TableCell>{t('Approver Role')}</TableCell>
                                            <TableCell>{t('SLA')}</TableCell>
                                            <TableCell align="right">{t('Actions')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {pendingApprovals.length > 0 ? pendingApprovals.map((request) => (
                                            <TableRow key={request.id} hover>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>
                                                        {request.transactionKey}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {request.storeId || '-'} | {request.wkstnId || '-'} | {request.businessDate || '-'}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>
                                                        {`${request.previousCaseStatus} -> ${request.requestedCaseStatus}`}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {request.requestedBy} | {request.requestedAt}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>
                                                        {request.policyName || '-'}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {request.requestedSeverity} | {request.requestedReasonCode || '-'}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>{request.approverRoleName || '-'}</TableCell>
                                                <TableCell>
                                                    <Chip size="small" label={request.slaStatus || '-'} sx={{backgroundColor: request.slaStatus === 'BREACHED' ? '#FEF2F2' : palette.blueChipBg, color: request.slaStatus === 'BREACHED' ? '#DC2626' : palette.blueChipText, fontWeight: 700}} />
                                                </TableCell>
                                                <TableCell align="right">
                                                    {canApprove ? (
                                                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                                                            <Button size="small" variant="contained" onClick={() => openDecision(request, 'APPROVED')}>{t('Approve')}</Button>
                                                            <Button size="small" variant="outlined" color="error" onClick={() => openDecision(request, 'REJECTED')}>{t('Reject')}</Button>
                                                        </Stack>
                                                    ) : (
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>{t('View only')}</Typography>
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        )) : (
                                            <TableRow>
                                                <TableCell colSpan={6}>
                                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                        {t('No pending approvals found for the selected filters.')}
                                                    </Typography>
                                                </TableCell>
                                            </TableRow>
                                        )}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Paper>

                        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                {t('Escalation Policies')}
                            </Typography>
                            <TableContainer>
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('Policy')}</TableCell>
                                            <TableCell>{t('Module')}</TableCell>
                                            <TableCell>{t('Triggers')}</TableCell>
                                            <TableCell>{t('Action')}</TableCell>
                                            <TableCell align="right">{t('Actions')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {escalationPolicies.length > 0 ? escalationPolicies.map((policy) => (
                                            <TableRow key={policy.id} hover>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>
                                                        {policy.policyName}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {policy.active ? t('Active') : t('Inactive')}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>{policy.reconView}</TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>
                                                        {[policy.minSeverity && `${policy.minSeverity}+`, policy.minImpactScore ? `${t('Impact')} ${policy.minImpactScore}+` : null].filter(Boolean).join(' | ') || t('Any severity')}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {[policy.triggerOnSlaBreach && t('SLA breached'), policy.agingHours ? `${t('Age')} ${policy.agingHours}h` : null, policy.inactivityHours ? `${t('Inactive')} ${policy.inactivityHours}h` : null].filter(Boolean).join(', ') || t('No extra time trigger')}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>
                                                        {[policy.escalateToRoleName && `${t('Team')}: ${policy.escalateToRoleName}`, policy.escalateToUsername && `${t('User')}: ${policy.escalateToUsername}`, policy.targetSeverity && `${t('Severity')}: ${policy.targetSeverity}`].filter(Boolean).join(' | ') || t('Timeline event only')}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {policy.noteTemplate || '-'}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell align="right">
                                                    <Stack direction="row" spacing={1} justifyContent="flex-end">
                                                        {canEditPolicies && (
                                                            <>
                                                                <Button size="small" variant="outlined" onClick={() => setEscalationPolicyForm({...policy})}>
                                                                    {t('Edit')}
                                                                </Button>
                                                                <Button size="small" variant="outlined" color="error" onClick={() => handleDeleteEscalationPolicy(policy)}>
                                                                    {t('Delete')}
                                                                </Button>
                                                            </>
                                                        )}
                                                    </Stack>
                                                </TableCell>
                                            </TableRow>
                                        )) : (
                                            <TableRow>
                                                <TableCell colSpan={5}>
                                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                        {t('No escalation policies configured yet.')}
                                                    </Typography>
                                                </TableCell>
                                            </TableRow>
                                        )}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Paper>

                        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                {t('Workflow Policies')}
                            </Typography>
                            <TableContainer>
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('Policy')}</TableCell>
                                            <TableCell>{t('Module')}</TableCell>
                                            <TableCell>{t('Trigger')}</TableCell>
                                            <TableCell>{t('Requirements')}</TableCell>
                                            <TableCell align="right">{t('Actions')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {policies.length > 0 ? policies.map((policy) => (
                                            <TableRow key={policy.id} hover>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>
                                                        {policy.policyName}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {policy.active ? t('Active') : t('Inactive')}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>{policy.reconView}</TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>
                                                        {policy.targetStatus} | {policy.minSeverity}+
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {policy.requireApproval ? `${t('Approval')}: ${policy.approverRoleName || '-'}` : t('Direct closure')}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {[policy.requireReasonCode && t('Reason'), policy.requireRootCauseCategory && t('Root Cause'), policy.requireNotes && t('Notes'), policy.requireComment && t('Comment')].filter(Boolean).join(', ') || t('No extra fields')}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell align="right">
                                                    <Stack direction="row" spacing={1} justifyContent="flex-end">
                                                        {canEditPolicies && (
                                                            <>
                                                                <Button size="small" variant="outlined" onClick={() => setPolicyForm({...policy})}>
                                                                    {t('Edit')}
                                                                </Button>
                                                                <Button size="small" variant="outlined" color="error" onClick={() => handleDeletePolicy(policy)}>
                                                                    {t('Delete')}
                                                                </Button>
                                                            </>
                                                        )}
                                                    </Stack>
                                                </TableCell>
                                            </TableRow>
                                        )) : (
                                            <TableRow>
                                                <TableCell colSpan={5}>
                                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                        {t('No workflow policies configured yet.')}
                                                    </Typography>
                                                </TableCell>
                                            </TableRow>
                                        )}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Paper>

                        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                {t('Recent Decisions')}
                            </Typography>
                            <TableContainer>
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('Transaction')}</TableCell>
                                            <TableCell>{t('Decision')}</TableCell>
                                            <TableCell>{t('Policy')}</TableCell>
                                            <TableCell>{t('Notes')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {recentDecisions.length > 0 ? recentDecisions.map((request) => (
                                            <TableRow key={request.id} hover>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>
                                                        {request.transactionKey}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {request.storeId || '-'} | {request.wkstnId || '-'} | {request.businessDate || '-'}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Chip size="small" label={request.requestStatus} sx={{backgroundColor: request.requestStatus === 'APPROVED' ? palette.tealChipBg : '#FEF2F2', color: request.requestStatus === 'APPROVED' ? palette.tealChipText : '#DC2626', fontWeight: 700}} />
                                                    <Typography sx={{mt: 0.65, fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {request.decisionBy || '-'} | {request.decisionAt || '-'}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>{request.policyName || '-'}</TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {request.decisionNotes || '-'}
                                                    </Typography>
                                                </TableCell>
                                            </TableRow>
                                        )) : (
                                            <TableRow>
                                                <TableCell colSpan={4}>
                                                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                        {t('No recent approval decisions found.')}
                                                    </Typography>
                                                </TableCell>
                                            </TableRow>
                                        )}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Paper>
                    </Stack>
                </Grid>
            </Grid>

            <Dialog open={decisionDialog.open} onClose={() => setDecisionDialog({open: false, request: null, decision: 'APPROVED', decisionNotes: ''})} fullWidth maxWidth="sm">
                <DialogTitle>{decisionDialog.decision === 'APPROVED' ? t('Approve Request') : t('Reject Request')}</DialogTitle>
                <DialogContent sx={{pt: 1}}>
                    <Stack spacing={1.5}>
                        <Alert severity={decisionDialog.decision === 'APPROVED' ? 'success' : 'warning'} sx={{borderRadius: 3}}>
                            {decisionDialog.request
                                ? `${decisionDialog.request.transactionKey} | ${decisionDialog.request.previousCaseStatus} -> ${decisionDialog.request.requestedCaseStatus}`
                                : t('No request selected')}
                        </Alert>
                        <TextField
                            label={t('Decision Notes')}
                            value={decisionDialog.decisionNotes}
                            onChange={(event) => setDecisionDialog((current) => ({...current, decisionNotes: event.target.value}))}
                            multiline
                            minRows={3}
                            fullWidth
                        />
                    </Stack>
                </DialogContent>
                <DialogActions sx={{px: 3, pb: 2.5}}>
                    <Button onClick={() => setDecisionDialog({open: false, request: null, decision: 'APPROVED', decisionNotes: ''})}>
                        {t('Cancel')}
                    </Button>
                    <Button variant="contained" color={decisionDialog.decision === 'APPROVED' ? 'primary' : 'error'} onClick={handleDecision} disabled={saving}>
                        {decisionDialog.decision === 'APPROVED' ? t('Approve') : t('Reject')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    )
}
