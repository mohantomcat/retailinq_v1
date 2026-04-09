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
    TextField,
    Typography,
} from '@mui/material'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {useReconModules} from '../hooks/useReconModules'

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

function SummaryCard({label, value, palette, tone}) {
    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.6rem', fontWeight: 800, color: tone}}>{value}</Typography>
        </Paper>
    )
}

function issueToForm(issue) {
    return {
        issueKey: issue?.issueKey || '',
        title: issue?.title || '',
        issueSummary: issue?.issueSummary || '',
        reconView: issue?.reconView || '',
        reconStatus: issue?.reconStatus || '',
        reasonCode: issue?.reasonCode || '',
        rootCauseCategory: issue?.rootCauseCategory || '',
        storeId: issue?.storeId || '',
        matchKeywords: issue?.matchKeywords || '',
        probableCause: issue?.probableCause || '',
        recommendedAction: issue?.recommendedAction || '',
        escalationGuidance: issue?.escalationGuidance || '',
        resolverNotes: issue?.resolverNotes || '',
        priorityWeight: issue?.priorityWeight || 100,
        active: issue?.active ?? true,
    }
}

export default function KnownIssues({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptionsWithAll: MODULE_OPTIONS} = useReconModules()
    const canView = hasPermission('EXCEPTION_QUEUE_VIEW')
    const canEdit = hasPermission('EXCEPTION_EDIT')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState('')
    const [data, setData] = useState(null)
    const [selectedIssueId, setSelectedIssueId] = useState('')
    const [filters, setFilters] = useState({
        reconView: '',
        activeFilter: 'ACTIVE',
        search: '',
    })
    const [form, setForm] = useState(issueToForm(null))

    const loadKnownIssues = async (nextFilters = filters, preferredIssueId = selectedIssueId) => {
        try {
            setLoading(true)
            setError('')
            const response = await exceptionApi.getKnownIssues({
                reconView: nextFilters.reconView,
                activeOnly: nextFilters.activeFilter === 'ACTIVE' ? true : undefined,
                search: nextFilters.search,
            })
            setData(response)
            const items = response?.items || []
            const selected = items.find((item) => item.id === preferredIssueId) || null
            if (selected) {
                setSelectedIssueId(selected.id)
                setForm(issueToForm(selected))
            } else if (!preferredIssueId) {
                setForm((current) => current)
            } else if (items[0]) {
                setSelectedIssueId(items[0].id)
                setForm(issueToForm(items[0]))
            } else {
                setSelectedIssueId('')
                setForm(issueToForm(null))
            }
        } catch (err) {
            setError(err.message || 'Failed to load known issues')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (canView) {
            loadKnownIssues(filters)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canView])

    const items = useMemo(() => data?.items || [], [data])
    const feedbackCoverage = useMemo(
        () => items.filter((item) => (item.helpfulCount || 0) + (item.notHelpfulCount || 0) > 0).length,
        [items]
    )

    if (!canView) {
        return null
    }

    const handleFormChange = (field, value) => {
        setForm((current) => ({...current, [field]: value}))
    }

    const handleSelectIssue = (issue) => {
        setSelectedIssueId(issue.id)
        setForm(issueToForm(issue))
        setFeedback('')
    }

    const handleNewIssue = () => {
        setSelectedIssueId('')
        setForm(issueToForm(null))
        setFeedback('')
    }

    const handleSave = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                issueKey: form.issueKey,
                title: form.title,
                issueSummary: form.issueSummary,
                reconView: form.reconView,
                reconStatus: form.reconStatus,
                reasonCode: form.reasonCode,
                rootCauseCategory: form.rootCauseCategory,
                storeId: form.storeId,
                matchKeywords: form.matchKeywords,
                probableCause: form.probableCause,
                recommendedAction: form.recommendedAction,
                escalationGuidance: form.escalationGuidance,
                resolverNotes: form.resolverNotes,
                priorityWeight: Number(form.priorityWeight) || 100,
                active: Boolean(form.active),
            }
            const saved = selectedIssueId
                ? await exceptionApi.updateKnownIssue(selectedIssueId, payload)
                : await exceptionApi.createKnownIssue(payload)
            setSelectedIssueId(saved.id)
            setForm(issueToForm(saved))
            setFeedback(selectedIssueId ? 'Known issue updated.' : 'Known issue created.')
            await loadKnownIssues(filters, saved.id)
        } catch (err) {
            setError(err.message || 'Failed to save known issue')
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
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Known Issues')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 980}}>
                            {t('Maintain the retailer-facing issue catalog that powers guided resolution on cases and store incidents. Keep the rules simple, specific, and grounded in repeatable operational fixes.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}
            {!error && feedback ? <Alert severity="success" sx={{mb: 2}}>{feedback}</Alert> : null}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box>
            ) : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid size={{xs: 12, md: 4}}><SummaryCard label={t('Catalog Size')} value={data?.totalCount || 0} tone={palette.text} palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 4}}><SummaryCard label={t('Active Issues')} value={data?.activeCount || 0} tone={palette.tealChipText} palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 4}}><SummaryCard label={t('Issues With Feedback')} value={feedbackCoverage} tone={palette.blueChipText} palette={palette}/></Grid>
                    </Grid>

                    <Paper elevation={0} sx={{p: 2.25, mb: 3, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr)) auto'}, gap: 1.5, alignItems: 'end'}}>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Module')}</InputLabel>
                                <Select value={filters.reconView} label={t('Module')} onChange={(event) => setFilters((current) => ({...current, reconView: event.target.value}))}>
                                    {MODULE_OPTIONS.map((option) => <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <FormControl size="small" fullWidth>
                                <InputLabel>{t('Catalog Scope')}</InputLabel>
                                <Select value={filters.activeFilter} label={t('Catalog Scope')} onChange={(event) => setFilters((current) => ({...current, activeFilter: event.target.value}))}>
                                    <MenuItem value="ACTIVE">{t('Active Only')}</MenuItem>
                                    <MenuItem value="ALL">{t('All Issues')}</MenuItem>
                                </Select>
                            </FormControl>
                            <TextField size="small" label={t('Search')} value={filters.search} onChange={(event) => setFilters((current) => ({...current, search: event.target.value}))} placeholder={t('Key, title, keywords, cause')} fullWidth/>
                            <Stack direction={{xs: 'column', lg: 'row'}} spacing={1}>
                                <Button variant="contained" onClick={() => loadKnownIssues(filters, selectedIssueId)}>{t('Apply')}</Button>
                                <Button variant="outlined" onClick={() => {
                                    const cleared = {reconView: '', activeFilter: 'ACTIVE', search: ''}
                                    setFilters(cleared)
                                    loadKnownIssues(cleared, selectedIssueId)
                                }}>{t('Clear')}</Button>
                            </Stack>
                        </Box>
                    </Paper>

                    <Grid container spacing={2}>
                        <Grid size={{xs: 12, lg: 5}}>
                            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'center', mb: 1.5}}>
                                    <Box>
                                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{t('Catalog Entries')}</Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('Select an issue to edit its rules and guidance.')}
                                        </Typography>
                                    </Box>
                                    {canEdit ? <Button variant="outlined" onClick={handleNewIssue}>{t('New Issue')}</Button> : null}
                                </Box>
                                <Stack spacing={1.2}>
                                    {items.length ? items.map((issue) => (
                                        <Paper
                                            key={issue.id}
                                            elevation={0}
                                            onClick={() => handleSelectIssue(issue)}
                                            sx={{
                                                p: 1.5,
                                                borderRadius: 3,
                                                border: `1px solid ${selectedIssueId === issue.id ? palette.blueChipText : palette.border}`,
                                                backgroundColor: selectedIssueId === issue.id ? palette.selectedBg : palette.cardBg,
                                                cursor: 'pointer',
                                            }}
                                        >
                                            <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap'}}>
                                                <Box sx={{flex: '1 1 260px'}}>
                                                    <Typography sx={{fontSize: '0.9rem', fontWeight: 800, color: palette.text}}>{issue.title}</Typography>
                                                    <Typography sx={{mt: 0.35, fontSize: '0.78rem', color: palette.textMuted}}>
                                                        {issue.issueKey} | {issue.reconView || t('All Modules')}
                                                    </Typography>
                                                </Box>
                                                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                                    <Chip size="small" label={issue.active ? t('Active') : t('Inactive')} sx={{backgroundColor: issue.active ? palette.tealChipBg : palette.cardBg, color: issue.active ? palette.tealChipText : palette.textMuted, fontWeight: 700, border: issue.active ? 'none' : `1px solid ${palette.border}`}}/>
                                                    <Chip size="small" label={`${t('Helpful')}: ${issue.helpfulCount || 0}`} sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}/>
                                                </Stack>
                                            </Box>
                                            <Typography sx={{mt: 0.8, fontSize: '0.82rem', color: palette.text}}>
                                                {issue.probableCause}
                                            </Typography>
                                            <Typography sx={{mt: 0.4, fontSize: '0.76rem', color: palette.textMuted}}>
                                                {t('Action')}: {issue.recommendedAction}
                                            </Typography>
                                            <Typography sx={{mt: 0.35, fontSize: '0.74rem', color: palette.textMuted}}>
                                                {t('Updated')}: {issue.updatedAt || '-'}
                                            </Typography>
                                        </Paper>
                                    )) : (
                                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No known issues matched the current filters.')}</Typography>
                                    )}
                                </Stack>
                            </Paper>
                        </Grid>

                        <Grid size={{xs: 12, lg: 7}}>
                            <Paper elevation={0} sx={{p: 2.25, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.5, alignItems: 'flex-start', flexWrap: 'wrap', mb: 2}}>
                                    <Box>
                                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                                            {selectedIssueId ? t('Edit Known Issue') : t('Create Known Issue')}
                                        </Typography>
                                        <Typography sx={{mt: 0.35, fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('Keep rules focused on recurring retailer problems with clear guidance that store and support teams can actually act on.')}
                                        </Typography>
                                    </Box>
                                    {selectedIssueId ? (
                                        <Chip size="small" label={form.active ? t('Active') : t('Inactive')} sx={{backgroundColor: form.active ? palette.tealChipBg : palette.cardBg, color: form.active ? palette.tealChipText : palette.textMuted, fontWeight: 700, border: form.active ? 'none' : `1px solid ${palette.border}`}}/>
                                    ) : null}
                                </Box>

                                <Stack spacing={1.5}>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: '1fr 1.1fr'}, gap: 1.5}}>
                                        <TextField size="small" label={t('Issue Key')} value={form.issueKey} onChange={(event) => handleFormChange('issueKey', event.target.value)} disabled={!canEdit}/>
                                        <TextField size="small" label={t('Title')} value={form.title} onChange={(event) => handleFormChange('title', event.target.value)} disabled={!canEdit}/>
                                    </Box>

                                    <TextField size="small" label={t('Issue Summary')} value={form.issueSummary} onChange={(event) => handleFormChange('issueSummary', event.target.value)} disabled={!canEdit} multiline minRows={2}/>

                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', md: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl size="small" fullWidth>
                                            <InputLabel>{t('Module')}</InputLabel>
                                            <Select value={form.reconView} label={t('Module')} onChange={(event) => handleFormChange('reconView', event.target.value)} disabled={!canEdit}>
                                                {MODULE_OPTIONS.map((option) => <MenuItem key={option.value || 'all'} value={option.value}>{t(option.label)}</MenuItem>)}
                                            </Select>
                                        </FormControl>
                                        <TextField size="small" label={t('Recon Status')} value={form.reconStatus} onChange={(event) => handleFormChange('reconStatus', event.target.value)} disabled={!canEdit}/>
                                        <FormControl size="small" fullWidth>
                                            <InputLabel>{t('Reason Code')}</InputLabel>
                                            <Select value={form.reasonCode} label={t('Reason Code')} onChange={(event) => handleFormChange('reasonCode', event.target.value)} disabled={!canEdit}>
                                                {REASON_CODE_OPTIONS.map((option) => <MenuItem key={option || 'all'} value={option}>{option || t('Any')}</MenuItem>)}
                                            </Select>
                                        </FormControl>
                                        <FormControl size="small" fullWidth>
                                            <InputLabel>{t('Root Cause')}</InputLabel>
                                            <Select value={form.rootCauseCategory} label={t('Root Cause')} onChange={(event) => handleFormChange('rootCauseCategory', event.target.value)} disabled={!canEdit}>
                                                {ROOT_CAUSE_CATEGORY_OPTIONS.map((option) => <MenuItem key={option || 'all'} value={option}>{option || t('Any')}</MenuItem>)}
                                            </Select>
                                        </FormControl>
                                        <TextField size="small" label={t('Store Scope')} value={form.storeId} onChange={(event) => handleFormChange('storeId', event.target.value)} disabled={!canEdit}/>
                                        <TextField size="small" label={t('Priority Weight')} type="number" value={form.priorityWeight} onChange={(event) => handleFormChange('priorityWeight', event.target.value)} disabled={!canEdit}/>
                                    </Box>

                                    <TextField
                                        size="small"
                                        label={t('Match Keywords')}
                                        value={form.matchKeywords}
                                        onChange={(event) => handleFormChange('matchKeywords', event.target.value)}
                                        disabled={!canEdit}
                                        helperText={t('Comma-separated words or phrases from incident titles, summaries, or notes.')}
                                    />

                                    <TextField size="small" label={t('Probable Cause')} value={form.probableCause} onChange={(event) => handleFormChange('probableCause', event.target.value)} disabled={!canEdit} multiline minRows={2}/>
                                    <TextField size="small" label={t('Recommended Action')} value={form.recommendedAction} onChange={(event) => handleFormChange('recommendedAction', event.target.value)} disabled={!canEdit} multiline minRows={2}/>
                                    <TextField size="small" label={t('Escalation Guidance')} value={form.escalationGuidance} onChange={(event) => handleFormChange('escalationGuidance', event.target.value)} disabled={!canEdit} multiline minRows={2}/>
                                    <TextField size="small" label={t('Resolver Notes')} value={form.resolverNotes} onChange={(event) => handleFormChange('resolverNotes', event.target.value)} disabled={!canEdit} multiline minRows={2}/>

                                    <FormControl size="small" sx={{maxWidth: 220}}>
                                        <InputLabel>{t('Status')}</InputLabel>
                                        <Select value={form.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleFormChange('active', event.target.value === 'ACTIVE')} disabled={!canEdit}>
                                            <MenuItem value="ACTIVE">{t('Active')}</MenuItem>
                                            <MenuItem value="INACTIVE">{t('Inactive')}</MenuItem>
                                        </Select>
                                    </FormControl>

                                    {canEdit ? (
                                        <Stack direction="row" spacing={1.2}>
                                            <Button variant="contained" onClick={handleSave} disabled={saving}>
                                                {saving ? t('Saving...') : selectedIssueId ? t('Update Issue') : t('Create Issue')}
                                            </Button>
                                            <Button variant="outlined" onClick={handleNewIssue} disabled={saving}>
                                                {t('Reset')}
                                            </Button>
                                        </Stack>
                                    ) : (
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('You can view known issues but do not have permission to edit the catalog.')}
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
