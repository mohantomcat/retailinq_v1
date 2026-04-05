import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    FormControl,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from '@mui/material'
import TuneIcon from '@mui/icons-material/Tune'
import DnsRoundedIcon from '@mui/icons-material/DnsRounded'
import {configApi} from '../services/configApi'
import {useAuth} from '../context/AuthContext'

function EntryValue({entry, palette, value}) {
    if (entry.sensitive) {
        const configured = value === 'Configured'
        return (
            <Chip
                size="small"
                label={configured ? 'Configured' : 'Not set'}
                sx={{
                    backgroundColor: configured ? palette.tealChipBg : palette.neutralChipBg,
                    color: configured ? palette.tealChipText : palette.neutralChipText,
                    fontWeight: 700,
                }}
            />
        )
    }

    return (
        <Typography sx={{fontSize: '0.83rem', color: palette.text, fontWeight: 600}}>
            {value || '-'}
        </Typography>
    )
}

function SectionTable({section, palette, draftValues, savingKey, canEdit, onDraftChange, onSave, onClear}) {
    const applyModeLabel = (applyMode) => {
        if (applyMode === 'LIVE_APPLIED') return 'Live applied'
        if (applyMode === 'RESTART_REQUIRED') return 'Restart required'
        return 'Reference only'
    }

    return (
        <Paper
            elevation={0}
            sx={{
                borderRadius: '22px',
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.cardBg,
                overflow: 'hidden',
            }}
        >
            <Box sx={{px: 2.5, py: 2, borderBottom: `1px solid ${palette.borderSoft}`}}>
                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>
                    {section.label}
                </Typography>
                <Typography sx={{mt: 0.4, fontSize: '0.82rem', color: palette.textMuted}}>
                    {section.description}
                </Typography>
            </Box>

            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>Setting</TableCell>
                            <TableCell>Runtime Value</TableCell>
                            <TableCell>Override</TableCell>
                            <TableCell>Default</TableCell>
                            <TableCell>Apply Mode</TableCell>
                            <TableCell>Environment Variable</TableCell>
                            <TableCell>Description</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {section.entries.map((entry) => {
                            const draftValue = Object.prototype.hasOwnProperty.call(draftValues, entry.key)
                                ? draftValues[entry.key]
                                : (entry.overrideValue ?? '')
                            const isSaving = savingKey === entry.key
                            const hasDraftValue = String(draftValue ?? '').trim().length > 0
                            const hasPersistedOverride = String(entry.overrideValue ?? '').trim().length > 0
                            return (
                                <TableRow key={`${section.id}-${entry.key}`}>
                                    <TableCell>
                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>
                                            {entry.label}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <EntryValue entry={entry} palette={palette} value={entry.effectiveValue}/>
                                    </TableCell>
                                    <TableCell sx={{minWidth: 240}}>
                                        {entry.editable ? (
                                            <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                                                <TextField
                                                    size="small"
                                                    value={draftValue}
                                                    onChange={(event) => onDraftChange(entry.key, event.target.value)}
                                                    placeholder="No override"
                                                    disabled={isSaving || !canEdit}
                                                    sx={{minWidth: 170}}
                                                />
                                                <Button
                                                    variant="contained"
                                                    size="small"
                                                    onClick={() => onSave(entry.key)}
                                                    disabled={isSaving || !canEdit || !String(draftValue ?? '').trim()}
                                                >
                                                    Save
                                                </Button>
                                                <Button
                                                    variant="text"
                                                    size="small"
                                                    onClick={() => onClear(entry.key, hasPersistedOverride)}
                                                    disabled={isSaving || !canEdit || (!hasDraftValue && !hasPersistedOverride)}
                                                >
                                                    Clear
                                                </Button>
                                            </Box>
                                        ) : (
                                            <EntryValue
                                                entry={entry}
                                                palette={palette}
                                                value={entry.overrideValue ? entry.overrideValue : 'Managed outside UI'}
                                            />
                                        )}
                                    </TableCell>
                                    <TableCell>
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                            {entry.defaultValue || '-'}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <Chip
                                            size="small"
                                            label={applyModeLabel(entry.applyMode)}
                                            sx={{
                                                backgroundColor: entry.applyMode === 'LIVE_APPLIED'
                                                    ? palette.tealChipBg
                                                    : entry.applyMode === 'RESTART_REQUIRED'
                                                    ? palette.neutralChipBg
                                                    : palette.blueChipBg,
                                                color: entry.applyMode === 'LIVE_APPLIED'
                                                    ? palette.tealChipText
                                                    : entry.applyMode === 'RESTART_REQUIRED'
                                                    ? palette.neutralChipText
                                                    : palette.blueChipText,
                                                fontWeight: 700,
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <Chip
                                            size="small"
                                            label={entry.envVar}
                                            sx={{
                                                backgroundColor: palette.blueChipBg,
                                                color: palette.blueChipText,
                                                fontWeight: 700,
                                                maxWidth: 220,
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                            {entry.description}
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            )
                        })}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

export default function Configurations({tabId, palette, t}) {
    const {hasPermission} = useAuth()
    const [catalog, setCatalog] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [selectedModuleId, setSelectedModuleId] = useState('')
    const [draftValues, setDraftValues] = useState({})
    const [savingKey, setSavingKey] = useState('')
    const [feedback, setFeedback] = useState('')
    const canEditConfigurations = hasPermission('CONFIG_EDIT')

    const loadCatalog = async () => {
        try {
            setLoading(true)
            setError('')
            const data = await configApi.getCatalog()
            setCatalog(data)
            if (data?.modules?.length && !data.modules.find((m) => m.moduleId === selectedModuleId)) {
                setSelectedModuleId(data.modules[0].moduleId)
            }
        } catch (err) {
            setError(err.message || 'Failed to load configuration catalog')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        let active = true
        ;(async () => {
            if (!active) return
            await loadCatalog()
        })()
        return () => {
            active = false
        }
    }, [])

    const selectedModule = useMemo(
        () => catalog?.modules?.find((module) => module.moduleId === selectedModuleId) ?? null,
        [catalog, selectedModuleId]
    )

    const handleDraftChange = (key, value) => {
        setDraftValues((current) => ({...current, [key]: value}))
    }

    const handleSave = async (configKey) => {
        try {
            setSavingKey(configKey)
            setError('')
            setFeedback('')
            const nextCatalog = await configApi.saveOverride(configKey, draftValues[configKey])
            setCatalog(nextCatalog)
            setFeedback('Configuration override saved. Restart the affected module if required.')
        } catch (err) {
            setError(err.message || 'Failed to save configuration override')
        } finally {
            setSavingKey('')
        }
    }

    const handleClear = async (configKey, hasPersistedOverride) => {
        if (!hasPersistedOverride) {
            setDraftValues((current) => ({...current, [configKey]: ''}))
            setFeedback('')
            setError('')
            return
        }
        try {
            setSavingKey(configKey)
            setError('')
            setFeedback('')
            const nextCatalog = await configApi.clearOverride(configKey)
            setCatalog(nextCatalog)
            setDraftValues((current) => ({...current, [configKey]: ''}))
            setFeedback('Configuration override cleared.')
        } catch (err) {
            setError(err.message || 'Failed to clear configuration override')
        } finally {
            setSavingKey('')
        }
    }

    const header = tabId === 'module-configs'
        ? {
            icon: <TuneIcon sx={{fontSize: 20}}/>,
            title: t('Module Configurations'),
            subtitle: t('Review runtime values and manage non-secret DB-backed overrides for each reconciliation lane.'),
        }
        : {
            icon: <DnsRoundedIcon sx={{fontSize: 20}}/>,
            title: t('System Configurations'),
            subtitle: t('Review shared settings and manage supported DB-backed system overrides.'),
        }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper
                elevation={0}
                sx={{
                    p: 3,
                    mb: 3,
                    borderRadius: '24px',
                    border: `1px solid ${palette.border}`,
                    background: palette.heroBg,
                }}
            >
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box
                        sx={{
                            mt: 0.3,
                            width: 40,
                            height: 40,
                            borderRadius: 3,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            backgroundColor: palette.blueChipBg,
                            color: palette.blueChipText,
                            flexShrink: 0,
                        }}
                    >
                        {header.icon}
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>
                            {header.title}
                        </Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 920}}>
                            {header.subtitle}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            <Alert severity="info" sx={{mb: 3}}>
                UI overrides are stored in PostgreSQL. Secrets remain environment-managed. Settings marked Live applied are read during job execution; others still require a restart.
            </Alert>

            {feedback ? <Alert severity="success" sx={{mb: 3}}>{feedback}</Alert> : null}

            {loading ? (
                <Box sx={{display: 'flex', justifyContent: 'center', py: 6}}>
                    <CircularProgress/>
                </Box>
            ) : error ? (
                <Alert severity="error">{error}</Alert>
            ) : (
                <>
                    {tabId === 'module-configs' && catalog?.modules?.length > 0 && (
                        <Paper
                            elevation={0}
                            sx={{
                                p: 2,
                                mb: 3,
                                borderRadius: '20px',
                                border: `1px solid ${palette.border}`,
                                backgroundColor: palette.cardBg,
                            }}
                        >
                            <FormControl size="small" sx={{minWidth: 280}}>
                                <InputLabel>Module</InputLabel>
                                <Select
                                    value={selectedModuleId}
                                    label="Module"
                                    onChange={(event) => setSelectedModuleId(event.target.value)}
                                    sx={{
                                        color: palette.text,
                                        '& .MuiOutlinedInput-notchedOutline': {
                                            borderColor: palette.border,
                                        },
                                    }}
                                >
                                    {catalog.modules.map((module) => (
                                        <MenuItem key={module.moduleId} value={module.moduleId}>
                                            {module.moduleLabel}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        </Paper>
                    )}

                    <Box sx={{display: 'grid', gap: 2}}>
                        {tabId === 'module-configs'
                            ? selectedModule?.sections?.map((section) => (
                                <SectionTable
                                    key={section.id}
                                    section={section}
                                    palette={palette}
                                    draftValues={draftValues}
                                    savingKey={savingKey}
                                    canEdit={canEditConfigurations}
                                    onDraftChange={handleDraftChange}
                                    onSave={handleSave}
                                    onClear={handleClear}
                                />
                            ))
                            : catalog?.systemSections?.map((section) => (
                                <SectionTable
                                    key={section.id}
                                    section={section}
                                    palette={palette}
                                    draftValues={draftValues}
                                    savingKey={savingKey}
                                    canEdit={canEditConfigurations}
                                    onDraftChange={handleDraftChange}
                                    onSave={handleSave}
                                    onClear={handleClear}
                                />
                            ))}
                    </Box>
                </>
            )}
        </Box>
    )
}
