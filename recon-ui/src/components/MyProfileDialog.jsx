import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Avatar,
    Box,
    Button,
    Chip,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    MenuItem,
    Paper,
    TextField,
    Typography,
} from '@mui/material'
import CloseRoundedIcon from '@mui/icons-material/CloseRounded'
import SecurityRoundedIcon from '@mui/icons-material/SecurityRounded'
import VerifiedUserRoundedIcon from '@mui/icons-material/VerifiedUserRounded'
import BadgeRoundedIcon from '@mui/icons-material/BadgeRounded'
import LockResetRoundedIcon from '@mui/icons-material/LockResetRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import EditRoundedIcon from '@mui/icons-material/EditRounded'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'
import LanguageSelectionDialog from './LanguageSelectionDialog'

function getThemeMode() {
    return document.documentElement.getAttribute('data-recon-theme') === 'dark'
        ? 'Dark'
        : 'Light'
}

function getPalette(themeMode) {
    const isDark = themeMode === 'Dark'

    return {
        isDark,
        dialogBg: isDark
            ? 'linear-gradient(180deg, #0F172A 0%, #111827 100%)'
            : 'linear-gradient(180deg, #FFFFFF 0%, #FCFDFE 100%)',
        dialogBorder: isDark ? '#1E293B' : '#E2E8F0',
        headerBorder: isDark ? '#1E293B' : '#F1F5F9',
        headerBg: isDark
            ? 'linear-gradient(180deg, rgba(37,99,235,0.10) 0%, rgba(255,255,255,0) 100%)'
            : 'linear-gradient(180deg, rgba(15,124,134,0.06) 0%, rgba(255,255,255,0) 100%)',
        cardBg: isDark ? '#0B1220' : '#FFFFFF',
        cardBgAlt: isDark
            ? 'linear-gradient(135deg, #0B1220 0%, #111827 100%)'
            : 'linear-gradient(135deg, #F8FAFC 0%, #FFFFFF 100%)',
        border: isDark ? '#334155' : '#E2E8F0',
        softBorder: isDark ? '#1E293B' : '#F1F5F9',
        text: isDark ? '#E2E8F0' : '#0F172A',
        textMuted: isDark ? '#94A3B8' : '#64748B',
        textSoft: isDark ? '#64748B' : '#94A3B8',
        inputBg: isDark ? '#0F172A' : '#FFFFFF',
        roleBg: isDark ? '#0F172A' : '#EFF6FF',
        roleText: '#2563EB',
        activeBg: isDark ? '#0F172A' : '#ECFEFF',
        activeText: '#0F7C86',
        neutralBg: isDark ? '#111827' : '#F8FAFC',
        closeBtnBg: isDark ? '#0B1220' : '#FFFFFF',
        closeBtnHover: isDark ? '#111827' : '#F8FAFC',
    }
}

function InfoCard({label, children, palette}) {
    return (
        <Paper
            elevation={0}
            sx={{
                p: 1.5,
                borderRadius: '18px',
                border: `1px solid ${palette.border}`,
                backgroundColor: palette.cardBg,
            }}
        >
            <Typography
                sx={{
                    fontSize: '0.76rem',
                    fontWeight: 700,
                    color: palette.textMuted,
                    textTransform: 'uppercase',
                    letterSpacing: '0.45px',
                    mb: 0.9,
                }}
            >
                {label}
            </Typography>
            {children}
        </Paper>
    )
}

export default function MyProfileDialog({
                                            open,
                                            onClose,
                                            user,
                                            onChangePassword,
                                        }) {
    const {updateUserProfile, updateUserPreferences} = useAuth()
    const {allowedLanguages, language, languageOptions, t} = useI18n()
    const [themeMode, setThemeMode] = useState(getThemeMode())

    const [editing, setEditing] = useState(false)
    const [fullName, setFullName] = useState('')
    const [email, setEmail] = useState('')
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')
    const [preferredLanguage, setPreferredLanguage] = useState(language)
    const [assignedLanguages, setAssignedLanguages] = useState(allowedLanguages)
    const [languageDialogOpen, setLanguageDialogOpen] = useState(false)

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

    useEffect(() => {
        if (open) {
            setFullName(user?.fullName || '')
            setEmail(user?.email || '')
            setEditing(false)
            setSaving(false)
            setError('')
            setSuccess('')
            setPreferredLanguage(user?.preferredLanguage || language)
            setAssignedLanguages(user?.allowedLanguages || allowedLanguages)
        }
    }, [allowedLanguages, language, open, user])

    const palette = useMemo(() => getPalette(themeMode), [themeMode])

    const displayName = user?.fullName || user?.username || t('User')
    const username = user?.username || '-'
    const role = user?.roles?.[0]?.name || t('User')
    const initials = displayName
        .split(' ')
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase())
        .join('') || 'U'

    const textFieldSx = {
        '& .MuiOutlinedInput-root': {
            color: palette.text,
            backgroundColor: palette.inputBg,
            '& fieldset': {
                borderColor: palette.border,
            },
            '&:hover fieldset': {
                borderColor: palette.textSoft,
            },
            '&.Mui-focused fieldset': {
                borderColor: '#2563EB',
            },
            '&.Mui-disabled': {
                WebkitTextFillColor: palette.textMuted,
                backgroundColor: palette.neutralBg,
            },
        },
        '& .MuiInputLabel-root': {
            color: palette.textMuted,
        },
        '& .MuiInputBase-input.Mui-disabled': {
            WebkitTextFillColor: palette.textMuted,
        },
    }

    const handleSave = async () => {
        setError('')
        setSuccess('')

        if (!fullName.trim()) {
            setError(t('Full name is required.'))
            return
        }

        if (!email.trim()) {
            setError(t('Email is required.'))
            return
        }

        try {
            setSaving(true)
            await updateUserProfile({
                fullName: fullName.trim(),
                email: email.trim(),
            })
            await updateUserPreferences({
                preferredLanguage,
                allowedLanguages: assignedLanguages,
            })
            setSuccess(t('Language preferences saved.'))
            setEditing(false)
        } catch (e) {
            setError(e?.message || t('Failed to update profile.'))
        } finally {
            setSaving(false)
        }
    }

    return (
        <Dialog
            open={open}
            onClose={onClose}
            fullWidth
            maxWidth="sm"
            PaperProps={{
                sx: {
                    borderRadius: '28px',
                    overflow: 'hidden',
                    border: `1px solid ${palette.dialogBorder}`,
                    boxShadow: '0 24px 60px rgba(15, 23, 42, 0.18)',
                    background: palette.dialogBg,
                },
            }}
        >
            <DialogTitle
                sx={{
                    px: 2.5,
                    py: 2,
                    borderBottom: `1px solid ${palette.headerBorder}`,
                    background: palette.headerBg,
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 2,
                    }}
                >
                    <Box>
                        <Typography
                            sx={{
                                fontSize: '1.15rem',
                                fontWeight: 800,
                                color: palette.text,
                            }}
                        >
                            {t('My Profile')}
                        </Typography>
                        <Typography
                            sx={{
                                mt: 0.4,
                                fontSize: '0.82rem',
                                color: palette.textMuted,
                            }}
                        >
                            {t('View and update your account information.')}
                        </Typography>
                    </Box>

                    <IconButton
                        onClick={onClose}
                        sx={{
                            width: 34,
                            height: 34,
                            border: `1px solid ${palette.border}`,
                            backgroundColor: palette.closeBtnBg,
                            color: palette.textMuted,
                            '&:hover': {
                                backgroundColor: palette.closeBtnHover,
                                color: palette.text,
                            },
                        }}
                    >
                        <CloseRoundedIcon sx={{fontSize: 18}}/>
                    </IconButton>
                </Box>
            </DialogTitle>

            <DialogContent sx={{px: 2.5, py: 2.5}}>
                <Paper
                    elevation={0}
                    sx={{
                        p: 2,
                        mb: 2,
                        borderRadius: '24px',
                        border: `1px solid ${palette.border}`,
                        background: palette.cardBgAlt,
                    }}
                >
                    <Box
                        sx={{
                            display: 'flex',
                            alignItems: {xs: 'flex-start', sm: 'center'},
                            flexDirection: {xs: 'column', sm: 'row'},
                            gap: 1.8,
                        }}
                    >
                        <Avatar
                            sx={{
                                width: 64,
                                height: 64,
                                fontSize: '1.2rem',
                                fontWeight: 800,
                                color: '#ffffff',
                                background:
                                    'linear-gradient(135deg, #0F7C86 0%, #2563EB 100%)',
                                boxShadow: '0 10px 24px rgba(37,99,235,0.18)',
                            }}
                        >
                            {initials}
                        </Avatar>

                        <Box sx={{minWidth: 0, flex: 1}}>
                            <Typography
                                sx={{
                                    fontSize: '1.05rem',
                                    fontWeight: 800,
                                    color: palette.text,
                                    lineHeight: 1.2,
                                    wordBreak: 'break-word',
                                }}
                            >
                                {fullName || displayName}
                            </Typography>

                            <Typography
                                sx={{
                                    mt: 0.45,
                                    fontSize: '0.86rem',
                                    color: palette.textMuted,
                                    wordBreak: 'break-word',
                                }}
                            >
                                {email || '-'}
                            </Typography>

                            <Box
                                sx={{
                                    display: 'flex',
                                    gap: 0.8,
                                    flexWrap: 'wrap',
                                    mt: 1.2,
                                }}
                            >
                                <Chip
                                    size="small"
                                    icon={<BadgeRoundedIcon sx={{fontSize: '0.9rem !important'}}/>}
                                    label={role}
                                    sx={{
                                        height: 24,
                                        backgroundColor: palette.roleBg,
                                        color: palette.roleText,
                                        fontWeight: 700,
                                        fontSize: '0.7rem',
                                    }}
                                />

                                <Chip
                                    size="small"
                                    icon={<VerifiedUserRoundedIcon sx={{fontSize: '0.9rem !important'}}/>}
                                    label={t('Active Account')}
                                    sx={{
                                        height: 24,
                                        backgroundColor: palette.activeBg,
                                        color: palette.activeText,
                                        fontWeight: 700,
                                        fontSize: '0.7rem',
                                    }}
                                />
                            </Box>
                        </Box>
                    </Box>
                </Paper>

                {error && (
                    <Alert severity="error" sx={{mb: 1.5}}>
                        {error}
                    </Alert>
                )}

                {success && (
                    <Alert severity="success" sx={{mb: 1.5}}>
                        {success}
                    </Alert>
                )}

                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {xs: '1fr', sm: '1fr 1fr'},
                        gap: 1.5,
                    }}
                >
                    <InfoCard label={t('Full Name')} palette={palette}>
                        <TextField
                            value={fullName}
                            onChange={(e) => setFullName(e.target.value)}
                            fullWidth
                            size="small"
                            disabled={!editing}
                            sx={textFieldSx}
                        />
                    </InfoCard>

                    <InfoCard label={t('Username')} palette={palette}>
                        <TextField
                            value={username}
                            fullWidth
                            size="small"
                            disabled
                            sx={textFieldSx}
                        />
                    </InfoCard>

                    <InfoCard label={t('Email')} palette={palette}>
                        <TextField
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            fullWidth
                            size="small"
                            disabled={!editing}
                            sx={textFieldSx}
                        />
                    </InfoCard>

                    <InfoCard label={t('Primary Role')} palette={palette}>
                        <Box
                            sx={{
                                height: 40,
                                px: 1.4,
                                borderRadius: '10px',
                                display: 'flex',
                                alignItems: 'center',
                                border: `1px solid ${palette.border}`,
                                backgroundColor: palette.neutralBg,
                                color: palette.text,
                                fontSize: '0.92rem',
                                fontWeight: 700,
                            }}
                        >
                            <SecurityRoundedIcon
                                sx={{
                                    fontSize: 18,
                                    color: palette.textMuted,
                                    mr: 1,
                                }}
                            />
                            {role}
                        </Box>
                    </InfoCard>
                </Box>

                <Divider sx={{my: 2.2, borderColor: palette.softBorder}}/>

                <Box sx={{mb: 2.2}}>
                    <Typography
                        sx={{
                            fontSize: '0.9rem',
                            fontWeight: 700,
                            color: palette.text,
                            mb: 0.35,
                        }}
                    >
                        {t('Language Preferences')}
                    </Typography>
                    <Typography
                        sx={{
                            fontSize: '0.8rem',
                            color: palette.textMuted,
                            mb: 1.2,
                            lineHeight: 1.5,
                        }}
                    >
                        {t(
                            'Choose the languages you want available in your quick switcher.'
                        )}
                    </Typography>

                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: {xs: '1fr', sm: '1fr 1fr'},
                            gap: 1.5,
                        }}
                    >
                        <InfoCard label={t('Preferred Language')} palette={palette}>
                            <TextField
                                select
                                fullWidth
                                size="small"
                                value={preferredLanguage}
                                onChange={(e) =>
                                    setPreferredLanguage(e.target.value)
                                }
                                disabled={!editing}
                                sx={textFieldSx}
                            >
                                {assignedLanguages.map((code) => (
                                    <MenuItem key={code} value={code}>
                                        {languageOptions[code]?.nativeLabel || code}
                                    </MenuItem>
                                ))}
                            </TextField>
                        </InfoCard>

                        <InfoCard label={t('Allowed Languages')} palette={palette}>
                            <Box
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    gap: 1,
                                    flexWrap: 'wrap',
                                }}
                            >
                                <Chip
                                    label={t('{count} languages assigned', {
                                        count: assignedLanguages.length,
                                    })}
                                    size="small"
                                    sx={{
                                        backgroundColor: palette.roleBg,
                                        color: palette.roleText,
                                        fontWeight: 700,
                                    }}
                                />
                                <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={() => setLanguageDialogOpen(true)}
                                    disabled={!editing}
                                    sx={{textTransform: 'none'}}
                                >
                                    {t('Manage Languages')}
                                </Button>
                            </Box>

                            <Box
                                sx={{
                                    display: 'flex',
                                    gap: 0.6,
                                    flexWrap: 'wrap',
                                    mt: 1,
                                }}
                            >
                                {assignedLanguages.map((code) => (
                                    <Chip
                                        key={code}
                                        size="small"
                                        label={
                                            languageOptions[code]?.nativeLabel ||
                                            code
                                        }
                                        sx={{
                                            backgroundColor: palette.neutralBg,
                                            color: palette.text,
                                        }}
                                    />
                                ))}
                            </Box>
                        </InfoCard>
                    </Box>
                </Box>

                <Divider sx={{my: 2.2, borderColor: palette.softBorder}}/>

                <Box
                    sx={{
                        display: 'flex',
                        alignItems: {xs: 'stretch', sm: 'center'},
                        justifyContent: 'space-between',
                        flexDirection: {xs: 'column', sm: 'row'},
                        gap: 1.2,
                    }}
                >
                    <Box>
                        <Typography
                            sx={{
                                fontSize: '0.9rem',
                                fontWeight: 700,
                                color: palette.text,
                            }}
                        >
                            {t('Security')}
                        </Typography>
                        <Typography
                            sx={{
                                mt: 0.35,
                                fontSize: '0.8rem',
                                color: palette.textMuted,
                                lineHeight: 1.5,
                            }}
                        >
                            {t('Update your password and keep your account secure.')}
                        </Typography>
                    </Box>

                    <Button
                        variant="outlined"
                        startIcon={<LockResetRoundedIcon/>}
                        onClick={onChangePassword}
                        sx={{
                            textTransform: 'none',
                            borderRadius: '12px',
                            px: 1.8,
                            py: 0.9,
                            fontWeight: 700,
                            borderColor: '#BFDBFE',
                            color: '#2563EB',
                            backgroundColor: palette.roleBg,
                            '&:hover': {
                                borderColor: '#93C5FD',
                                backgroundColor: palette.isDark
                                    ? '#111827'
                                    : '#DBEAFE',
                            },
                        }}
                    >
                        {t('Change Password')}
                    </Button>
                </Box>

                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'flex-end',
                        gap: 1.2,
                        mt: 2.4,
                    }}
                >
                    {!editing ? (
                        <Button
                            onClick={() => setEditing(true)}
                            variant="outlined"
                            startIcon={<EditRoundedIcon/>}
                            sx={{
                                textTransform: 'none',
                                borderRadius: '12px',
                                px: 2,
                                fontWeight: 700,
                                borderColor: palette.border,
                                color: palette.text,
                                '&:hover': {
                                    borderColor: '#2563EB',
                                    backgroundColor: palette.neutralBg,
                                },
                            }}
                        >
                            {t('Edit Profile')}
                        </Button>
                    ) : (
                        <Button
                            onClick={handleSave}
                            variant="contained"
                            disabled={saving}
                            startIcon={<SaveRoundedIcon/>}
                            sx={{
                                textTransform: 'none',
                                borderRadius: '12px',
                                px: 2.2,
                                fontWeight: 700,
                                background:
                                    'linear-gradient(90deg, #0F7C86, #157F8B)',
                                boxShadow: 'none',
                                '&:hover': {
                                    boxShadow: 'none',
                                    background:
                                        'linear-gradient(90deg, #0B6A72, #126E79)',
                                },
                            }}
                        >
                            {saving ? t('Saving...') : t('Save Changes')}
                        </Button>
                    )}

                    <Button
                        onClick={onClose}
                        variant="text"
                        sx={{
                            textTransform: 'none',
                            borderRadius: '12px',
                            px: 1.6,
                            fontWeight: 700,
                            color: palette.textMuted,
                            '&:hover': {
                                backgroundColor: palette.neutralBg,
                                color: palette.text,
                            },
                        }}
                    >
                        {t('Close')}
                    </Button>
                </Box>
            </DialogContent>

            <LanguageSelectionDialog
                open={languageDialogOpen}
                onClose={() => setLanguageDialogOpen(false)}
                value={assignedLanguages}
                onChange={(next) => {
                    if (!next?.length) {
                        setError(t('Select at least one language.'))
                        return
                    }

                    setAssignedLanguages(next)
                    setPreferredLanguage((prev) =>
                        next.includes(prev) ? prev : next[0]
                    )
                    setError('')
                }}
                multiple
                title={t('Manage Languages')}
                subtitle={t(
                    'Choose the languages you want available in your quick switcher.'
                )}
                confirmLabel={t('Save')}
            />
        </Dialog>
    )
}
