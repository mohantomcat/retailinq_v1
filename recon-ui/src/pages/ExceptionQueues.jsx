import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Checkbox,
    Chip,
    CircularProgress,
    FormControl,
    FormControlLabel,
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
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import ExceptionWorkbenchPanel from '../components/ExceptionWorkbenchPanel'
import {RECON_VIEW_OPTIONS_WITH_ALL} from '../constants/reconViews'
import {
    EXCEPTION_QUEUE_PREFILL_EVENT,
    EXCEPTION_QUEUE_PREFILL_KEY,
} from '../constants/uiStateKeys'

const CLEAR_FIELD_TOKEN = '__CLEAR__'

const MODULE_OPTIONS = RECON_VIEW_OPTIONS_WITH_ALL

const QUEUE_OPTIONS = [
    {value: 'ALL', label: 'All Open'},
    {value: 'HIGH_IMPACT', label: 'High Impact'},
    {value: 'UNASSIGNED', label: 'Unassigned'},
    {value: 'OWNERSHIP_GAP', label: 'Ownership Gaps'},
    {value: 'ACTION_DUE', label: 'Action Due'},
    {value: 'MINE', label: 'Mine'},
    {value: 'MY_TEAM', label: 'My Team'},
    {value: 'OVERDUE', label: 'Overdue'},
    {value: 'HIGH_SEVERITY', label: 'High Severity'},
    {value: 'ESCALATED', label: 'Escalated'},
]

const CASE_STATUS_OPTIONS = ['', 'OPEN', 'IN_REVIEW', 'RESOLVED', 'IGNORED']
const BULK_CASE_STATUS_OPTIONS = ['', 'OPEN', 'IN_REVIEW', 'RESOLVED', 'IGNORED']
const SEVERITY_OPTIONS = ['', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
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
function SummaryCard({label, value, palette, tone}) {
    const styles = {
        blue: {color: palette.blueChipText},
        teal: {color: palette.tealChipText},
        red: {color: '#DC2626'},
        amber: {color: '#D97706'},
        purple: {color: '#7C3AED'},
    }[tone]

    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.7rem', fontWeight: 800, color: styles.color}}>{value}</Typography>
        </Paper>
    )
}

function selectionKey(record) {
    return `${record.transactionKey}::${record.reconView}`
}

function impactChipStyles(impactBand, palette) {
    switch ((impactBand || '').toUpperCase()) {
        case 'CRITICAL':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'HIGH':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        case 'MEDIUM':
            return {backgroundColor: palette.blueBg, color: palette.blueText}
        default:
            return {backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}
    }
}

function formatCurrencyValue(amount, currencyCode) {
    if (amount === null || amount === undefined || amount === '') {
        return '-'
    }
    const numeric = Number(amount)
    if (!Number.isFinite(numeric)) {
        return `${currencyCode ? `${currencyCode} ` : ''}${amount}`
    }
    if (!currencyCode) {
        return numeric.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})
    }
    try {
        return new Intl.NumberFormat(undefined, {
            style: 'currency',
            currency: currencyCode,
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        }).format(numeric)
    } catch {
        return `${currencyCode} ${numeric.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}`
    }
}

function formatDateTimeValue(value) {
    if (!value) {
        return '-'
    }
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) {
        return value.replace('T', ' ')
    }
    return parsed.toLocaleString()
}

function ownershipChipStyles(status, palette) {
    switch ((status || '').toUpperCase()) {
        case 'UNOWNED':
        case 'OWNERSHIP_GAP':
            return {backgroundColor: '#FEF2F2', color: '#B91C1C'}
        case 'NO_NEXT_ACTION':
            return {backgroundColor: '#FFF7ED', color: '#C2410C'}
        case 'ACTION_OVERDUE':
            return {backgroundColor: '#FEF3C7', color: '#B45309'}
        case 'ACTION_DUE_SOON':
            return {backgroundColor: palette.blueBg, color: palette.blueText}
        default:
            return {backgroundColor: palette.tealChipBg, color: palette.tealChipText}
    }
}

function ownershipLabel(status, t) {
    switch ((status || '').toUpperCase()) {
        case 'UNOWNED':
            return t('Unowned')
        case 'NO_NEXT_ACTION':
            return t('No Next Action')
        case 'ACTION_OVERDUE':
            return t('Action Overdue')
        case 'ACTION_DUE_SOON':
            return t('Action Due Soon')
        case 'OWNERSHIP_GAP':
            return t('Ownership Gap')
        default:
            return t('On Track')
    }
}

function buildPrefillFilters(prefill) {
    return {
        reconView: prefill?.reconView || '',
        queueType: 'ALL',
        caseStatus: '',
        severity: '',
        assignee: '',
        assignedRole: '',
        search: prefill?.search || prefill?.transactionKey || '',
    }
}

export default function ExceptionQueues({palette, t}) {
    const {hasPermission} = useAuth()
    const canView = hasPermission('EXCEPTION_QUEUE_VIEW')
    const canEdit = hasPermission('EXCEPTION_EDIT')
    const [loading, setLoading] = useState(true)
    const [savingBulk, setSavingBulk] = useState(false)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState(null)
    const [data, setData] = useState(null)
    const [selectedRecord, setSelectedRecord] = useState(null)
    const [selectedIncidentKey, setSelectedIncidentKey] = useState('')
    const [selectedKeys, setSelectedKeys] = useState([])
    const [workbenchRefreshToken, setWorkbenchRefreshToken] = useState(0)
    const [activePrefill, setActivePrefill] = useState(null)
    const [filters, setFilters] = useState({
        reconView: '',
        queueType: 'HIGH_IMPACT',
        caseStatus: '',
        severity: '',
        assignee: '',
        assignedRole: '',
        search: '',
    })
    const [assignmentOptions, setAssignmentOptions] = useState({usernames: [], roleNames: []})
    const [bulkForm, setBulkForm] = useState({
        caseStatus: '',
        assigneeUsername: '',
        assignedRoleName: '',
        nextAction: '',
        nextActionDueAt: '',
        handoffNote: '',
        reasonCode: '',
        commentText: '',
        reopenReason: '',
        captureAuditSnapshot: false,
    })

    const loadQueues = async (nextFilters = filters, preferredSelection = null) => {
        try {
            setLoading(true)
            setError('')
            const queueData = await exceptionApi.getQueues(nextFilters)
            setData(queueData)
            setSelectedKeys((current) => current.filter((key) => (queueData?.items || []).some((item) => selectionKey(item) === key)))
            setSelectedIncidentKey((current) => (queueData?.storeIncidents || []).some((item) => item.incidentKey === current) ? current : '')
            const firstItem = (queueData?.items || [])[0]
            const exactItem = preferredSelection?.transactionKey
                ? (queueData?.items || []).find((item) =>
                    item.transactionKey === preferredSelection.transactionKey
                    && (!preferredSelection.reconView || item.reconView === preferredSelection.reconView))
                : null
            setSelectedRecord((current) => {
                if (
                    current &&
                    queueData?.items?.some(
                        (item) =>
                            item.transactionKey === current.transactionKey &&
                            item.reconView === current.reconView
                    )
                ) {
                    return current
                }
                return exactItem
                    ? toWorkbenchRecord(exactItem)
                    : firstItem ? toWorkbenchRecord(firstItem) : null
            })
        } catch (err) {
            setError(err.message || 'Failed to load exception queues')
        } finally {
            setLoading(false)
        }
    }

    const loadAssignmentOptions = async () => {
        try {
            const options = await exceptionApi.getAssignmentOptions()
            setAssignmentOptions(options || {usernames: [], roleNames: []})
        } catch {
            setAssignmentOptions({usernames: [], roleNames: []})
        }
    }

    useEffect(() => {
        if (canView) {
            loadQueues(filters)
            loadAssignmentOptions()
        }
    }, [canView])

    useEffect(() => {
        if (!canView) {
            return undefined
        }

        const applyPrefill = (prefill) => {
            if (!prefill) {
                return
            }
            const nextFilters = buildPrefillFilters(prefill)
            setActivePrefill(prefill)
            setFilters(nextFilters)
            loadQueues(nextFilters, prefill)
            sessionStorage.removeItem(EXCEPTION_QUEUE_PREFILL_KEY)
        }

        const stored = sessionStorage.getItem(EXCEPTION_QUEUE_PREFILL_KEY)
        if (stored) {
            try {
                applyPrefill(JSON.parse(stored))
            } catch {
                sessionStorage.removeItem(EXCEPTION_QUEUE_PREFILL_KEY)
            }
        }

        const handlePrefill = (event) => {
            applyPrefill(event.detail)
        }

        window.addEventListener(EXCEPTION_QUEUE_PREFILL_EVENT, handlePrefill)
        return () => window.removeEventListener(EXCEPTION_QUEUE_PREFILL_EVENT, handlePrefill)
    }, [canView])

    const records = useMemo(() => data?.items || [], [data])
    const incidents = useMemo(() => data?.storeIncidents || [], [data])
    const selectedIncident = useMemo(
        () => incidents.find((incident) => incident.incidentKey === selectedIncidentKey) || null,
        [incidents, selectedIncidentKey]
    )
    const visibleRecords = useMemo(
        () => selectedIncidentKey
            ? records.filter((record) => record.storeIncidentKey === selectedIncidentKey)
            : records,
        [records, selectedIncidentKey]
    )
    const selectedItems = useMemo(
        () => visibleRecords.filter((record) => selectedKeys.includes(selectionKey(record))),
        [visibleRecords, selectedKeys]
    )
    const allVisibleSelected = visibleRecords.length > 0 && selectedItems.length === visibleRecords.length
    const someVisibleSelected = selectedItems.length > 0 && selectedItems.length < visibleRecords.length

    useEffect(() => {
        setSelectedKeys((current) => current.filter((key) => visibleRecords.some((record) => selectionKey(record) === key)))
        setSelectedRecord((current) => {
            if (
                current &&
                visibleRecords.some(
                    (record) =>
                        record.transactionKey === current.transactionKey &&
                        record.reconView === current.reconView
                )
            ) {
                return current
            }
            return visibleRecords[0] ? toWorkbenchRecord(visibleRecords[0]) : null
        })
    }, [visibleRecords])

    if (!canView) {
        return null
    }

    const updateFilter = (field, value) => {
        setActivePrefill(null)
        setFilters((current) => ({...current, [field]: value}))
    }

    const toggleRecordSelection = (record) => {
        const key = selectionKey(record)
        setSelectedKeys((current) => current.includes(key)
            ? current.filter((value) => value !== key)
            : [...current, key])
    }

    const toggleSelectAllVisible = () => {
        if (allVisibleSelected) {
            setSelectedKeys([])
            return
        }
        setSelectedKeys(visibleRecords.map((record) => selectionKey(record)))
    }

    const handleBulkFormChange = (field, value) => {
        setBulkForm((current) => ({...current, [field]: value}))
    }

    const resetBulkForm = () => {
        setBulkForm({
            caseStatus: '',
            assigneeUsername: '',
            assignedRoleName: '',
            nextAction: '',
            nextActionDueAt: '',
            handoffNote: '',
            reasonCode: '',
            commentText: '',
            reopenReason: '',
            captureAuditSnapshot: false,
        })
    }

    const handleBulkApply = async () => {
        if (selectedItems.length === 0) {
            return
        }
        setSavingBulk(true)
        setError('')
        setFeedback(null)
        try {
            const response = await exceptionApi.bulkUpdateCases({
                items: selectedItems.map((item) => ({
                    transactionKey: item.transactionKey,
                    reconView: item.reconView,
                })),
                caseStatus: bulkForm.caseStatus,
                assigneeUsername: bulkForm.assigneeUsername,
                assignedRoleName: bulkForm.assignedRoleName,
                nextAction: bulkForm.nextAction,
                nextActionDueAt: bulkForm.nextActionDueAt,
                handoffNote: bulkForm.handoffNote.trim(),
                reasonCode: bulkForm.reasonCode,
                commentText: bulkForm.commentText.trim(),
                reopenReason: bulkForm.reopenReason.trim(),
                captureAuditSnapshot: bulkForm.captureAuditSnapshot,
            })

            const failedCount = response?.failedCount || 0
            const updatedCount = response?.updatedCount || 0
            const firstFailure = response?.failures?.[0]?.error

            setFeedback({
                severity: failedCount > 0 ? 'warning' : 'success',
                message: failedCount > 0
                    ? `${updatedCount} cases updated, ${failedCount} failed${firstFailure ? `: ${firstFailure}` : ''}`
                    : `${updatedCount} cases updated successfully.`,
            })

            setSelectedKeys([])
            resetBulkForm()
            await loadQueues(filters)
            setWorkbenchRefreshToken((current) => current + 1)
        } catch (err) {
            setError(err.message || 'Failed to apply bulk triage')
        } finally {
            setSavingBulk(false)
        }
    }

    const focusIncident = (incident) => {
        setSelectedIncidentKey(incident?.incidentKey || '')
        setSelectedKeys([])
    }

    const selectIncidentCases = (incident) => {
        if (!incident?.incidentKey) {
            return
        }
        const incidentRecords = records.filter((record) => record.storeIncidentKey === incident.incidentKey)
        setSelectedIncidentKey(incident.incidentKey)
        setSelectedKeys(incidentRecords.map((record) => selectionKey(record)))
        if (incidentRecords[0]) {
            setSelectedRecord(toWorkbenchRecord(incidentRecords[0]))
        }
    }

    return (
        <Box sx={{px: 1, py: 3}} data-testid="exception-queues-page">
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: palette.tealChipBg, color: palette.tealChipText, flexShrink: 0}}>
                        <FactCheckOutlinedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Exception Queues')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Work exceptions in business-impact order so store ops teams handle the biggest service and reconciliation risks first, then triage similar cases in bulk.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}
            {!error && feedback ? <Alert severity={feedback.severity} sx={{mb: 2}}>{feedback.message}</Alert> : null}

            {loading ? <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box> : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('Open Cases')} value={data?.summary?.openCases || 0} tone="blue" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('High Impact')} value={data?.summary?.highImpactCases || 0} tone="red" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('Store Incidents')} value={data?.summary?.storeIncidentCount || 0} tone="purple" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('Stores At Risk')} value={data?.summary?.storesAtRisk || 0} tone="amber" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('Escalated')} value={data?.summary?.escalatedCases || 0} tone="red" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('Unassigned High Impact')} value={data?.summary?.unassignedHighImpactCases || 0} tone="purple" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('Ownership Gaps')} value={data?.summary?.ownershipGapCases || 0} tone="red" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('Action Due')} value={data?.summary?.actionDueCases || 0} tone="amber" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('Overdue')} value={data?.summary?.overdueCases || 0} tone="teal" palette={palette}/></Grid>
                        <Grid item xs={12} md={6} lg={2}><SummaryCard label={t('My Team')} value={data?.summary?.myTeamCases || 0} tone="blue" palette={palette}/></Grid>
                    </Grid>

                    <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: {
                                    xs: '1fr',
                                    sm: 'repeat(2, minmax(0, 1fr))',
                                    xl: 'repeat(6, minmax(0, 1fr))',
                                },
                                gap: 1.5,
                                alignItems: 'start',
                            }}
                        >
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Module')}</InputLabel>
                                <Select value={filters.reconView} label={t('Module')} onChange={(event) => updateFilter('reconView', event.target.value)}>
                                    {MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Queue')}</InputLabel>
                                <Select value={filters.queueType} label={t('Queue')} onChange={(event) => updateFilter('queueType', event.target.value)}>
                                    {QUEUE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Status')}</InputLabel>
                                <Select value={filters.caseStatus} label={t('Status')} onChange={(event) => updateFilter('caseStatus', event.target.value)}>
                                    {CASE_STATUS_OPTIONS.map((option) => <MenuItem key={option || 'all'} value={option}>{option ? option : t('All')}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Severity')}</InputLabel>
                                <Select value={filters.severity} label={t('Severity')} onChange={(event) => updateFilter('severity', event.target.value)}>
                                    {SEVERITY_OPTIONS.map((option) => <MenuItem key={option || 'all'} value={option}>{option ? option : t('All')}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Assignee')}</InputLabel>
                                <Select value={filters.assignee} label={t('Assignee')} onChange={(event) => updateFilter('assignee', event.target.value)}>
                                    <MenuItem value="">{t('All')}</MenuItem>
                                    {assignmentOptions.usernames.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Assigned Team')}</InputLabel>
                                <Select value={filters.assignedRole} label={t('Assigned Team')} onChange={(event) => updateFilter('assignedRole', event.target.value)}>
                                    <MenuItem value="">{t('All')}</MenuItem>
                                    {assignmentOptions.roleNames.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                </Select>
                            </FormControl>
                        </Box>
                        <Box
                            sx={{
                                mt: 1.5,
                                display: 'grid',
                                gridTemplateColumns: {
                                    xs: '1fr',
                                    md: 'minmax(0, 2fr) auto',
                                },
                                gap: 1.5,
                                alignItems: 'center',
                            }}
                        >
                            <TextField
                                size="small"
                                label={t('Search')}
                                fullWidth
                                value={filters.search}
                                onChange={(event) => updateFilter('search', event.target.value)}
                            />
                            <Stack direction={{xs: 'column', sm: 'row'}} spacing={1} justifyContent="flex-end">
                                <Button variant="contained" onClick={() => loadQueues(filters)} sx={{minWidth: 112, height: 40}}>
                                    {t('Apply')}
                                </Button>
                                <Button
                                    variant="outlined"
                                    onClick={() => {
                                        const cleared = {reconView: '', queueType: 'HIGH_IMPACT', caseStatus: '', severity: '', assignee: '', assignedRole: '', search: ''}
                                        setActivePrefill(null)
                                        setFilters(cleared)
                                        loadQueues(cleared)
                                    }}
                                    sx={{minWidth: 96, height: 40}}
                                >
                                    {t('Clear')}
                                </Button>
                            </Stack>
                        </Box>
                    </Paper>

                    {activePrefill?.transactionKey && !loading && incidents.length === 0 && visibleRecords.length === 0 ? (
                        <Alert severity="info" sx={{borderRadius: 3}}>
                            {t(
                                'No exception case exists yet for transaction {transactionKey} in {module}. The queue jump is scoped correctly; there is simply no active case to work.',
                                {
                                    transactionKey: activePrefill.transactionKey,
                                    module: activePrefill.reconView || t('the selected module'),
                                }
                            )}
                        </Alert>
                    ) : null}

                    <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap', mb: 2}}>
                            <Box>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                    {t('Store Incidents')}
                                </Typography>
                                <Typography sx={{mt: 0.4, fontSize: '0.84rem', color: palette.textMuted, maxWidth: 920}}>
                                    {t('Group repeated transaction exceptions into one store incident so operations teams can focus on a broken lane, drill into underlying cases, and apply triage in bulk.')}
                                </Typography>
                            </Box>
                            {selectedIncident ? (
                                <Button variant="outlined" onClick={() => focusIncident(null)} sx={{textTransform: 'none'}}>
                                    {t('Show All Cases')}
                                </Button>
                            ) : null}
                        </Box>

                        {selectedIncident ? (
                            <Alert severity="info" sx={{mb: 2}}>
                                {selectedIncident.storeId} | {selectedIncident.reconView} | {selectedIncident.incidentTitle} | {selectedIncident.impactedTransactions || 0} {t('transactions impacted')}
                            </Alert>
                        ) : null}

                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>{t('Incident')}</TableCell>
                                        <TableCell>{t('Impact')}</TableCell>
                                        <TableCell>{t('Cases')}</TableCell>
                                        <TableCell>{t('Priority')}</TableCell>
                                        <TableCell>{t('Started')}</TableCell>
                                        <TableCell>{t('Updated')}</TableCell>
                                        <TableCell align="right">{t('Actions')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {incidents.length > 0 ? incidents.map((incident) => {
                                        const focused = selectedIncidentKey === incident.incidentKey
                                        return (
                                            <TableRow key={incident.incidentKey} hover selected={focused}>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 800, color: palette.text}}>
                                                        {incident.incidentTitle}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                        {t('Store')} {incident.storeId || '-'} | {incident.reconView}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted, mt: 0.45}}>
                                                        {incident.incidentSummary || '-'}
                                                    </Typography>
                                                    {incident.businessValue?.summary ? (
                                                        <Typography sx={{fontSize: '0.76rem', color: palette.blueText, mt: 0.45}}>
                                                            {incident.businessValue.summary}
                                                        </Typography>
                                                    ) : null}
                                                    <Stack direction="row" spacing={0.75} sx={{mt: 0.8}} flexWrap="wrap" useFlexGap>
                                                        <Chip
                                                            size="small"
                                                            label={ownershipLabel(incident.ownershipStatus, t)}
                                                            sx={{fontWeight: 700, ...ownershipChipStyles(incident.ownershipStatus, palette)}}
                                                        />
                                                        <Chip
                                                            size="small"
                                                            label={incident.ownerSummary || t('Unassigned')}
                                                            sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}
                                                        />
                                                    </Stack>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.92rem', fontWeight: 800, color: palette.text}}>
                                                        {incident.impactScore ?? 0}
                                                    </Typography>
                                                    <Chip
                                                        size="small"
                                                        label={incident.impactBand || t('Low')}
                                                        sx={{mt: 0.6, fontWeight: 700, ...impactChipStyles(incident.impactBand, palette)}}
                                                    />
                                                    {incident.businessValue?.customerImpact ? (
                                                        <Chip
                                                            size="small"
                                                            label={`${t('Customer')}: ${incident.businessValue.customerImpact}`}
                                                            sx={{mt: 0.75, ml: 0.75, backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}
                                                        />
                                                    ) : null}
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>
                                                        {incident.impactedTransactions || 0} {t('transactions')}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {incident.affectedRegisters || 0} {t('registers')} | {incident.openCaseCount || 0} {t('cases')}
                                                    </Typography>
                                                    {incident.businessValue?.valueAtRisk ? (
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.blueText, mt: 0.55, fontWeight: 700}}>
                                                            {t('Value At Risk')}: {formatCurrencyValue(incident.businessValue.valueAtRisk, incident.businessValue.currencyCode)}
                                                        </Typography>
                                                    ) : null}
                                                    <Stack direction="row" spacing={0.75} sx={{mt: 0.7}} flexWrap="wrap" useFlexGap>
                                                        <Chip size="small" label={`${t('Overdue')}: ${incident.overdueCases || 0}`} sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}/>
                                                        <Chip size="small" label={`${t('Unassigned')}: ${incident.unassignedCases || 0}`} sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}/>
                                                    </Stack>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.8rem', color: palette.text, lineHeight: 1.5}}>
                                                        {incident.priorityReason || '-'}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.55, fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {t('Next Action')}: {incident.nextAction || t('Not set')}
                                                    </Typography>
                                                    {incident.nextActionDueAt ? (
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                            {t('Due By')}: {formatDateTimeValue(incident.nextActionDueAt)}
                                                        </Typography>
                                                    ) : null}
                                                    {incident.lastHandoffAt ? (
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                            {t('Last Handoff')}: {incident.lastHandoffBy || '-'} | {formatDateTimeValue(incident.lastHandoffAt)}
                                                        </Typography>
                                                    ) : null}
                                                </TableCell>
                                                <TableCell>{incident.startedAt || '-'}</TableCell>
                                                <TableCell>{incident.latestUpdatedAt || '-'}</TableCell>
                                                <TableCell align="right">
                                                    <Stack direction={{xs: 'column', md: 'row'}} spacing={1} justifyContent="flex-end">
                                                        <Button variant={focused ? 'contained' : 'outlined'} size="small" onClick={() => focusIncident(incident)} sx={{textTransform: 'none'}}>
                                                            {focused ? t('Focused') : t('View Cases')}
                                                        </Button>
                                                        {canEdit ? (
                                                            <Button variant="outlined" size="small" onClick={() => selectIncidentCases(incident)} sx={{textTransform: 'none'}}>
                                                                {t('Select Cases')}
                                                            </Button>
                                                        ) : null}
                                                    </Stack>
                                                </TableCell>
                                            </TableRow>
                                        )
                                    }) : (
                                        <TableRow>
                                            <TableCell colSpan={7}>
                                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                    {t('No store incidents found for the selected filters.')}
                                                </Typography>
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </Paper>

                    {canEdit && selectedItems.length > 0 ? (
                        <Paper elevation={0} sx={{p: 2.5, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                            <Stack spacing={1.5}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap'}}>
                                    <Box>
                                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                            {t('Bulk Triage')}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.84rem', color: palette.textMuted}}>
                                            {selectedItems.length} {t('cases selected for coordinated triage actions.')}
                                        </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="outlined" onClick={() => setSelectedKeys([])} disabled={savingBulk} sx={{textTransform: 'none'}}>
                                            {t('Clear Selection')}
                                        </Button>
                                        <Button variant="outlined" onClick={resetBulkForm} disabled={savingBulk} sx={{textTransform: 'none'}}>
                                            {t('Reset Form')}
                                        </Button>
                                    </Stack>
                                </Box>

                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(4, minmax(0, 1fr))'}, gap: 1.5}}>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Bulk Status')}</InputLabel>
                                        <Select value={bulkForm.caseStatus} label={t('Bulk Status')} onChange={(event) => handleBulkFormChange('caseStatus', event.target.value)}>
                                            {BULK_CASE_STATUS_OPTIONS.map((option) => <MenuItem key={option || 'unchanged'} value={option}>{option ? option : t('No Change')}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Bulk Assignee')}</InputLabel>
                                        <Select value={bulkForm.assigneeUsername} label={t('Bulk Assignee')} onChange={(event) => handleBulkFormChange('assigneeUsername', event.target.value)}>
                                            <MenuItem value="">{t('No Change')}</MenuItem>
                                            <MenuItem value={CLEAR_FIELD_TOKEN}>{t('Unassign')}</MenuItem>
                                            {assignmentOptions.usernames.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Bulk Team')}</InputLabel>
                                        <Select value={bulkForm.assignedRoleName} label={t('Bulk Team')} onChange={(event) => handleBulkFormChange('assignedRoleName', event.target.value)}>
                                            <MenuItem value="">{t('No Change')}</MenuItem>
                                            <MenuItem value={CLEAR_FIELD_TOKEN}>{t('Unassign Team')}</MenuItem>
                                            {assignmentOptions.roleNames.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                    <FormControl size="small" fullWidth>
                                        <InputLabel>{t('Bulk Reason Code')}</InputLabel>
                                        <Select value={bulkForm.reasonCode} label={t('Bulk Reason Code')} onChange={(event) => handleBulkFormChange('reasonCode', event.target.value)}>
                                            <MenuItem value="">{t('No Change')}</MenuItem>
                                            <MenuItem value={CLEAR_FIELD_TOKEN}>{t('Clear Reason Code')}</MenuItem>
                                            {REASON_CODE_OPTIONS.filter(Boolean).map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
                                        </Select>
                                    </FormControl>
                                </Box>

                                <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'minmax(0, 1.4fr) minmax(0, 1fr)'}, gap: 1.5}}>
                                    <TextField
                                        size="small"
                                        label={t('Bulk Next Action')}
                                        value={bulkForm.nextAction}
                                        onChange={(event) => handleBulkFormChange('nextAction', event.target.value)}
                                        fullWidth
                                        helperText={t('Leave blank to keep each case as-is.')}
                                    />
                                    <TextField
                                        size="small"
                                        label={t('Bulk Action Due By')}
                                        type="datetime-local"
                                        value={bulkForm.nextActionDueAt}
                                        onChange={(event) => handleBulkFormChange('nextActionDueAt', event.target.value)}
                                        fullWidth
                                        InputLabelProps={{shrink: true}}
                                        helperText={t('Apply the same next-action due time to all selected cases.')}
                                    />
                                </Box>

                                <TextField
                                    size="small"
                                    label={t('Bulk Shift Handoff Note')}
                                    value={bulkForm.handoffNote}
                                    onChange={(event) => handleBulkFormChange('handoffNote', event.target.value)}
                                    multiline
                                    minRows={2}
                                    fullWidth
                                    helperText={t('Used for shift handoff context and added to the case timeline when ownership details change.')}
                                />

                                <TextField
                                    size="small"
                                    label={t('Bulk Comment')}
                                    value={bulkForm.commentText}
                                    onChange={(event) => handleBulkFormChange('commentText', event.target.value)}
                                    multiline
                                    minRows={2}
                                    fullWidth
                                    helperText={t('Applied as a comment to every selected case.')}
                                />

                                {['OPEN', 'IN_REVIEW'].includes((bulkForm.caseStatus || '').toUpperCase()) ? (
                                    <TextField
                                        size="small"
                                        label={t('Bulk Reopen Reason')}
                                        value={bulkForm.reopenReason}
                                        onChange={(event) => handleBulkFormChange('reopenReason', event.target.value)}
                                        multiline
                                        minRows={2}
                                        fullWidth
                                        helperText={t('Required if any selected cases are being reopened from resolved or ignored states.')}
                                    />
                                ) : null}

                                <FormControlLabel
                                    control={<Checkbox checked={bulkForm.captureAuditSnapshot} onChange={(event) => handleBulkFormChange('captureAuditSnapshot', event.target.checked)} />}
                                    label={t('Capture before/after audit snapshot for sensitive field changes')}
                                />

                                <Box sx={{display: 'flex', justifyContent: 'flex-end'}}>
                                    <Button
                                        variant="contained"
                                        onClick={handleBulkApply}
                                        disabled={savingBulk}
                                        sx={{textTransform: 'none', fontWeight: 700}}
                                    >
                                        {savingBulk ? t('Applying...') : t('Apply Bulk Triage')}
                                    </Button>
                                </Box>
                            </Stack>
                        </Paper>
                    ) : null}

                    <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap', mb: 2}}>
                            <Box>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                    {t('Queue Records')}
                                </Typography>
                                <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                    {selectedIncident
                                        ? `${selectedIncident.storeId || '-'} | ${selectedIncident.reconView} | ${selectedIncident.incidentTitle}`
                                        : t('Transaction-level cases ranked by business impact.')}
                                </Typography>
                            </Box>
                            {selectedIncident ? (
                                <Chip
                                    label={`${visibleRecords.length} ${t('cases in focus')}`}
                                    sx={{backgroundColor: palette.blueBg, color: palette.blueText, fontWeight: 700}}
                                />
                            ) : null}
                        </Box>
                        <TableContainer>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell padding="checkbox">
                                            <Checkbox
                                                checked={allVisibleSelected}
                                                indeterminate={someVisibleSelected}
                                                onChange={toggleSelectAllVisible}
                                                disabled={!canEdit || visibleRecords.length === 0}
                                            />
                                        </TableCell>
                                        <TableCell>{t('Transaction')}</TableCell>
                                        <TableCell>{t('Impact')}</TableCell>
                                        <TableCell>{t('Priority Reason')}</TableCell>
                                        <TableCell>{t('Module')}</TableCell>
                                        <TableCell>{t('Severity')}</TableCell>
                                        <TableCell>{t('Status')}</TableCell>
                                        <TableCell>{t('Assignee')}</TableCell>
                                        <TableCell>{t('Team')}</TableCell>
                                        <TableCell>{t('SLA')}</TableCell>
                                        <TableCell>{t('Updated')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {visibleRecords.length > 0 ? visibleRecords.map((record) => {
                                        const key = selectionKey(record)
                                        const selected = selectedKeys.includes(key)
                                        return (
                                            <TableRow
                                                key={record.id}
                                                hover
                                                selected={selectedRecord?.transactionKey === record.transactionKey && selectedRecord?.reconView === record.reconView}
                                                onClick={() => setSelectedRecord(toWorkbenchRecord(record))}
                                                sx={{cursor: 'pointer'}}
                                            >
                                                <TableCell padding="checkbox" onClick={(event) => event.stopPropagation()}>
                                                    <Checkbox checked={selected} onChange={() => toggleRecordSelection(record)} disabled={!canEdit}/>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', fontWeight: 700, color: palette.text}}>
                                                        {record.transactionKey}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {record.storeIncidentTitle || '-'}
                                                    </Typography>
                                                    {record.businessValue?.summary ? (
                                                        <Typography sx={{fontSize: '0.75rem', color: palette.blueText}}>
                                                            {record.businessValue.summary}
                                                        </Typography>
                                                    ) : null}
                                                    <Typography sx={{fontSize: '0.75rem', color: palette.textMuted}}>
                                                        {record.storeId || '-'} | {record.wkstnId || '-'} | {record.businessDate || '-'}
                                                    </Typography>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>
                                                        {record.impactScore ?? 0}
                                                    </Typography>
                                                    <Stack direction="row" spacing={0.75} sx={{mt: 0.6}} flexWrap="wrap" useFlexGap>
                                                        <Chip
                                                            size="small"
                                                            label={record.impactBand || t('Low')}
                                                            sx={{fontWeight: 700, ...impactChipStyles(record.impactBand, palette)}}
                                                        />
                                                        {record.businessValue?.valueAtRisk ? (
                                                            <Chip
                                                                size="small"
                                                                label={formatCurrencyValue(record.businessValue.valueAtRisk, record.businessValue.currencyCode)}
                                                                sx={{backgroundColor: palette.blueBg, color: palette.blueText}}
                                                            />
                                                        ) : null}
                                                        <Chip
                                                            size="small"
                                                            label={`${t('Store')}: ${record.storeOpenCaseCount || 0}`}
                                                            sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}
                                                        />
                                                        <Chip
                                                            size="small"
                                                            label={`${t('Repeat')}: ${record.repeatIssueCount || 0}`}
                                                            sx={{backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}}
                                                        />
                                                    </Stack>
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.8rem', color: palette.text, lineHeight: 1.5}}>
                                                        {record.priorityReason || '-'}
                                                    </Typography>
                                                    <Typography sx={{mt: 0.55, fontSize: '0.74rem', color: palette.textMuted}}>
                                                        {t('Next Action')}: {record.nextAction || t('Not set')}
                                                    </Typography>
                                                    {record.nextActionDueAt ? (
                                                        <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                                            {t('Due By')}: {formatDateTimeValue(record.nextActionDueAt)}
                                                        </Typography>
                                                    ) : null}
                                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                                        {t('Age')}: {record.caseAgeHours ?? 0}h
                                                    </Typography>
                                                    {record.businessValue?.customerImpact ? (
                                                        <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                                            {t('Customer Impact')}: {record.businessValue.customerImpact}
                                                        </Typography>
                                                    ) : null}
                                                </TableCell>
                                                <TableCell>{record.reconView}</TableCell>
                                                <TableCell>{record.severity}</TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text, fontWeight: 700}}>
                                                        {record.caseStatus}
                                                    </Typography>
                                                    {record.escalationState === 'ESCALATED' ? (
                                                        <Chip
                                                            size="small"
                                                            label={t('Escalated')}
                                                            sx={{mt: 0.65, backgroundColor: '#FEF2F2', color: '#B91C1C', fontWeight: 700}}
                                                        />
                                                    ) : null}
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text, fontWeight: 700}}>
                                                        {record.assigneeUsername || '-'}
                                                    </Typography>
                                                    <Chip
                                                        size="small"
                                                        label={ownershipLabel(record.ownershipStatus, t)}
                                                        sx={{mt: 0.65, fontWeight: 700, ...ownershipChipStyles(record.ownershipStatus, palette)}}
                                                    />
                                                </TableCell>
                                                <TableCell>
                                                    <Typography sx={{fontSize: '0.82rem', color: palette.text}}>
                                                        {record.assignedRoleName || '-'}
                                                    </Typography>
                                                    {record.lastHandoffAt ? (
                                                        <Typography sx={{mt: 0.5, fontSize: '0.74rem', color: palette.textMuted}}>
                                                            {t('Last Handoff')}: {formatDateTimeValue(record.lastHandoffAt)}
                                                        </Typography>
                                                    ) : null}
                                                    {record.lastEscalatedAt ? (
                                                        <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                                            {t('Last Escalated')}: {formatDateTimeValue(record.lastEscalatedAt)}
                                                        </Typography>
                                                    ) : null}
                                                </TableCell>
                                                <TableCell>{record.slaStatus}</TableCell>
                                                <TableCell>{record.updatedAt || '-'}</TableCell>
                                            </TableRow>
                                        )
                                    }) : (
                                        <TableRow>
                                            <TableCell colSpan={11}>
                                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                                    {selectedIncident
                                                        ? t('No exception queue records found for the selected store incident.')
                                                        : t('No exception queue records found for the selected filters.')}
                                                </Typography>
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </Paper>

                    <ExceptionWorkbenchPanel
                        record={selectedRecord}
                        refreshToken={workbenchRefreshToken}
                        onCaseSaved={async () => {
                            await loadQueues(filters)
                        }}
                    />
                </>
            )}
        </Box>
    )
}

function toWorkbenchRecord(record) {
    return {
        transactionKey: record.transactionKey,
        reconView: record.reconView,
        reconStatus: record.reconStatus,
        storeId: record.storeId,
        wkstnId: record.wkstnId,
        businessDate: record.businessDate,
        businessDateDisplay: record.businessDate,
    }
}
