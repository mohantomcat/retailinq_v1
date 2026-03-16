import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Divider,
    MenuItem,
    Paper,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import {exceptionApi} from '../services/exceptionApi'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'

const CASE_STATUS_OPTIONS = ['OPEN', 'IN_REVIEW', 'RESOLVED', 'IGNORED']
const REASON_CODE_OPTIONS = [
    'REPLICATION_LAG',
    'DUPLICATE_SUBMISSION',
    'QUANTITY_MISMATCH',
    'TOTAL_MISMATCH',
    'ITEM_MAPPING',
    'DATA_QUALITY',
    'CONFIGURATION',
]

function getThemeMode() {
    return document.documentElement.getAttribute('data-recon-theme') === 'dark'
        ? 'Dark'
        : 'Light'
}

function getPalette(themeMode) {
    const isDark = themeMode === 'Dark'

    return {
        isDark,
        paperBg: isDark ? '#0F172A' : '#FFFFFF',
        cardBg: isDark ? '#111827' : '#F8FAFC',
        border: isDark ? '#1E293B' : '#E5E7EB',
        borderSoft: isDark ? '#243041' : '#F1F5F9',
        text: isDark ? '#E2E8F0' : '#0F172A',
        textMuted: isDark ? '#94A3B8' : '#64748B',
        blueBg: isDark ? '#0F172A' : '#EFF6FF',
        blueText: '#2563EB',
        tealBg: isDark ? '#0F172A' : '#ECFEFF',
        tealText: '#0F7C86',
    }
}

export default function ExceptionWorkbenchPanel({record}) {
    const [themeMode, setThemeMode] = useState(getThemeMode())
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [caseData, setCaseData] = useState(null)
    const [caseStatus, setCaseStatus] = useState('OPEN')
    const [reasonCode, setReasonCode] = useState('')
    const [assigneeUsername, setAssigneeUsername] = useState('')
    const [notes, setNotes] = useState('')
    const [commentDraft, setCommentDraft] = useState('')
    const {hasPermission, user} = useAuth()
    const {t} = useI18n()

    useEffect(() => {
        const observer = new MutationObserver(() => {
            setThemeMode(getThemeMode())
        })

        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-recon-theme'],
        })

        return () => observer.disconnect()
    }, [])

    const palette = useMemo(() => getPalette(themeMode), [themeMode])
    const canView = hasPermission('EXCEPTION_VIEW')
    const canEdit = hasPermission('EXCEPTION_EDIT')

    useEffect(() => {
        if (!record?.transactionKey || !record?.reconView || !canView) {
            setCaseData(null)
            setCaseStatus('OPEN')
            setReasonCode('')
            setAssigneeUsername('')
            setNotes('')
            setCommentDraft('')
            return
        }

        const load = async () => {
            setLoading(true)
            setError('')
            try {
                const response = await exceptionApi.getCase({
                    transactionKey: record.transactionKey,
                    reconView: record.reconView,
                })
                setCaseData(response)
                setCaseStatus(response?.caseStatus || 'OPEN')
                setReasonCode(response?.reasonCode || '')
                setAssigneeUsername(response?.assigneeUsername || '')
                setNotes(response?.notes || '')
            } catch (err) {
                setError(err.message || t('Failed to load exception case'))
            } finally {
                setLoading(false)
            }
        }

        load()
    }, [record?.transactionKey, record?.reconView, canView, t])

    if (!record || !canView) {
        return null
    }

    const handleSave = async () => {
        setSaving(true)
        setError('')
        try {
            const response = await exceptionApi.saveCase({
                transactionKey: record.transactionKey,
                reconView: record.reconView,
                caseStatus,
                reasonCode,
                assigneeUsername,
                notes,
            })
            setCaseData(response)
        } catch (err) {
            setError(err.message || t('Failed to save exception case'))
        } finally {
            setSaving(false)
        }
    }

    const handleAddComment = async () => {
        if (!commentDraft.trim()) return
        setSaving(true)
        setError('')
        try {
            const response = await exceptionApi.addComment({
                transactionKey: record.transactionKey,
                reconView: record.reconView,
                commentText: commentDraft.trim(),
            })
            setCaseData(response)
            setCommentDraft('')
        } catch (err) {
            setError(err.message || t('Failed to add comment'))
        } finally {
            setSaving(false)
        }
    }

    return (
        <Paper
            elevation={0}
            sx={{
                mt: 2.5,
                p: 3,
                borderRadius: 4,
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.paperBg,
            }}
        >
            <Stack spacing={2.25}>
                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                        gap: 2,
                        flexWrap: 'wrap',
                    }}
                >
                    <Box>
                        <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                            {t('Exception Workbench')}
                        </Typography>
                        <Typography sx={{mt: 0.45, fontSize: '0.86rem', color: palette.textMuted}}>
                            {t('Manage ownership, reason code, notes, and comments for the selected exception.')}
                        </Typography>
                    </Box>
                    <Stack direction="row" spacing={1}>
                        <Chip label={record.reconView} size="small" sx={{backgroundColor: palette.blueBg, color: palette.blueText, fontWeight: 700}} />
                        <Chip label={caseStatus} size="small" sx={{backgroundColor: palette.tealBg, color: palette.tealText, fontWeight: 700}} />
                    </Stack>
                </Box>

                {error && <Alert severity="error" sx={{borderRadius: 3}}>{error}</Alert>}

                <Paper
                    elevation={0}
                    sx={{
                        p: 2,
                        borderRadius: 3,
                        border: `1px solid ${palette.borderSoft}`,
                        backgroundColor: palette.cardBg,
                    }}
                >
                    <Stack spacing={0.6}>
                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                            {t('Selected record')}
                        </Typography>
                        <Typography sx={{fontSize: '0.9rem', color: palette.text}}>
                            {t('Transaction Key')}: {record.transactionKey}
                        </Typography>
                        <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                            {record.storeId || '-'} • {record.wkstnId || '-'} • {record.businessDateDisplay || record.businessDate || '-'}
                        </Typography>
                    </Stack>
                </Paper>

                {loading ? (
                    <Box sx={{display: 'flex', justifyContent: 'center', py: 2}}>
                        <CircularProgress size={24} />
                    </Box>
                ) : (
                    <>
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: {xs: '1fr', md: 'repeat(3, minmax(0, 1fr))'},
                                gap: 2,
                            }}
                        >
                            <TextField
                                select
                                size="small"
                                label={t('Case Status')}
                                value={caseStatus}
                                onChange={(e) => setCaseStatus(e.target.value)}
                                disabled={!canEdit || saving}
                            >
                                {CASE_STATUS_OPTIONS.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                select
                                size="small"
                                label={t('Reason Code')}
                                value={reasonCode}
                                onChange={(e) => setReasonCode(e.target.value)}
                                disabled={!canEdit || saving}
                            >
                                <MenuItem value="">{t('None')}</MenuItem>
                                {REASON_CODE_OPTIONS.map((option) => (
                                    <MenuItem key={option} value={option}>{option}</MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                size="small"
                                label={t('Assignee')}
                                value={assigneeUsername}
                                onChange={(e) => setAssigneeUsername(e.target.value)}
                                disabled={!canEdit || saving}
                            />
                        </Box>

                        <TextField
                            label={t('Resolution Notes')}
                            value={notes}
                            onChange={(e) => setNotes(e.target.value)}
                            disabled={!canEdit || saving}
                            multiline
                            minRows={3}
                            fullWidth
                        />

                        <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={2}>
                            <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                {caseData?.updatedBy
                                    ? `${t('Last updated by')} ${caseData.updatedBy} ${t('at')} ${caseData.updatedAt || '-'}`
                                    : t('No case metadata saved yet')}
                            </Typography>
                            {canEdit && (
                                <Button variant="contained" onClick={handleSave} disabled={saving} sx={{textTransform: 'none', fontWeight: 700}}>
                                    {saving ? t('Saving...') : t('Save Case')}
                                </Button>
                            )}
                        </Stack>

                        <Divider />

                        <Stack spacing={1.25}>
                            <Typography sx={{fontSize: '0.95rem', fontWeight: 800, color: palette.text}}>
                                {t('Comments')}
                            </Typography>

                            {(caseData?.comments || []).length > 0 ? (
                                <Stack spacing={1}>
                                    {caseData.comments.map((comment) => (
                                        <Paper
                                            key={comment.id}
                                            elevation={0}
                                            sx={{
                                                p: 1.5,
                                                borderRadius: 3,
                                                border: `1px solid ${palette.borderSoft}`,
                                                backgroundColor: palette.cardBg,
                                            }}
                                        >
                                            <Typography sx={{fontSize: '0.88rem', color: palette.text, lineHeight: 1.55}}>
                                                {comment.commentText}
                                            </Typography>
                                            <Typography sx={{mt: 0.75, fontSize: '0.76rem', color: palette.textMuted}}>
                                                {comment.createdBy} • {comment.createdAt}
                                            </Typography>
                                        </Paper>
                                    ))}
                                </Stack>
                            ) : (
                                <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                                    {t('No comments added yet.')}
                                </Typography>
                            )}

                            {canEdit && (
                                <Stack spacing={1}>
                                    <TextField
                                        label={t('Add Comment')}
                                        value={commentDraft}
                                        onChange={(e) => setCommentDraft(e.target.value)}
                                        multiline
                                        minRows={2}
                                        disabled={saving}
                                    />
                                    <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap'}}>
                                        <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>
                                            {t('Commenting as')} {user?.username || '-'}
                                        </Typography>
                                        <Button variant="outlined" onClick={handleAddComment} disabled={saving || !commentDraft.trim()} sx={{textTransform: 'none', fontWeight: 700}}>
                                            {t('Add Comment')}
                                        </Button>
                                    </Box>
                                </Stack>
                            )}
                        </Stack>
                    </>
                )}
            </Stack>
        </Paper>
    )
}
