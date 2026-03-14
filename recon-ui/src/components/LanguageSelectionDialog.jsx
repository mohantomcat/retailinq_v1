import {useMemo, useState} from 'react'
import {
    Box,
    Button,
    Checkbox,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    List,
    ListItemButton,
    ListItemText,
    TextField,
    Typography,
} from '@mui/material'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import {LANGUAGE_CATALOG_LIST} from '../i18n/languageCatalog'
import {useI18n} from '../context/I18nContext'

export default function LanguageSelectionDialog({
    open,
    onClose,
    value,
    onChange,
    multiple = false,
    allowedCodes = null,
    title,
    subtitle,
    confirmLabel,
}) {
    const {t} = useI18n()
    const [search, setSearch] = useState('')
    const [draft, setDraft] = useState(() =>
        multiple ? value || [] : value || ''
    )

    const availableLanguages = useMemo(() => {
        const allowed = new Set(allowedCodes || LANGUAGE_CATALOG_LIST.map((x) => x.code))
        const q = search.trim().toLowerCase()

        return LANGUAGE_CATALOG_LIST.filter((item) => {
            if (!allowed.has(item.code)) return false
            if (!q) return true

            return (
                item.label.toLowerCase().includes(q) ||
                item.nativeLabel.toLowerCase().includes(q) ||
                item.code.toLowerCase().includes(q)
            )
        })
    }, [allowedCodes, search])

    const handleClose = () => {
        setSearch('')
        setDraft(multiple ? value || [] : value || '')
        onClose?.()
    }

    const handleToggle = (code) => {
        if (!multiple) {
            setDraft(code)
            onChange?.(code)
            onClose?.()
            return
        }

        setDraft((prev) => {
            const next = new Set(prev || [])
            next.has(code) ? next.delete(code) : next.add(code)
            return Array.from(next)
        })
    }

    const handleConfirm = () => {
        onChange?.(draft)
        onClose?.()
    }

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <DialogTitle sx={{fontWeight: 700}}>
                {title || t(multiple ? 'Manage Languages' : 'Change Language')}
            </DialogTitle>

            <DialogContent sx={{pt: '12px !important'}}>
                <Typography
                    sx={{
                        fontSize: '0.84rem',
                        color: '#64748B',
                        mb: 1.5,
                    }}
                >
                    {subtitle ||
                        t(
                            multiple
                                ? 'Choose the languages you want available in your quick switcher.'
                                : 'Your quick switcher only shows assigned languages.'
                        )}
                </Typography>

                <TextField
                    fullWidth
                    size="small"
                    placeholder={t('Search languages...')}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    InputProps={{
                        startAdornment: (
                            <SearchRoundedIcon
                                sx={{fontSize: 18, color: '#94A3B8', mr: 1}}
                            />
                        ),
                    }}
                    sx={{mb: 1.5}}
                />

                {multiple && (
                    <Box sx={{display: 'flex', gap: 0.75, flexWrap: 'wrap', mb: 1.5}}>
                        {(draft || []).map((code) => {
                            const item = LANGUAGE_CATALOG_LIST.find(
                                (x) => x.code === code
                            )
                            if (!item) return null

                            return (
                                <Chip
                                    key={code}
                                    label={item.nativeLabel}
                                    size="small"
                                />
                            )
                        })}
                    </Box>
                )}

                <List
                    sx={{
                        maxHeight: 360,
                        overflowY: 'auto',
                        border: '1px solid #E2E8F0',
                        borderRadius: '16px',
                        py: 0.5,
                    }}
                >
                    {availableLanguages.length === 0 ? (
                        <Typography
                            sx={{
                                px: 2,
                                py: 3,
                                textAlign: 'center',
                                color: '#94A3B8',
                            }}
                        >
                            {t('No languages found.')}
                        </Typography>
                    ) : (
                        availableLanguages.map((item) => {
                            const selected = multiple
                                ? (draft || []).includes(item.code)
                                : draft === item.code

                            return (
                                <ListItemButton
                                    key={item.code}
                                    onClick={() => handleToggle(item.code)}
                                    sx={{
                                        py: 1,
                                        px: 1.5,
                                        '&:hover': {backgroundColor: '#F8FAFC'},
                                    }}
                                >
                                    {multiple && (
                                        <Checkbox
                                            checked={selected}
                                            sx={{mr: 0.5}}
                                        />
                                    )}
                                    <ListItemText
                                        primary={item.nativeLabel}
                                        secondary={`${item.label} · ${item.code}`}
                                        primaryTypographyProps={{
                                            fontWeight: selected ? 700 : 500,
                                        }}
                                    />
                                </ListItemButton>
                            )
                        })
                    )}
                </List>
            </DialogContent>

            <DialogActions sx={{px: 3, pb: 3}}>
                <Button onClick={handleClose} sx={{textTransform: 'none'}}>
                    {t('Cancel')}
                </Button>
                {multiple && (
                    <Button
                        variant="contained"
                        onClick={handleConfirm}
                        disabled={!draft?.length}
                        sx={{textTransform: 'none'}}
                    >
                        {confirmLabel || t('Save')}
                    </Button>
                )}
            </DialogActions>
        </Dialog>
    )
}
