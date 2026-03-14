import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogContent,
    DialogTitle,
    IconButton,
    InputAdornment,
    TextField,
    Typography,
} from '@mui/material'
import CloseRoundedIcon from '@mui/icons-material/CloseRounded'
import VisibilityRoundedIcon from '@mui/icons-material/VisibilityRounded'
import VisibilityOffRoundedIcon from '@mui/icons-material/VisibilityOffRounded'
import LockResetRoundedIcon from '@mui/icons-material/LockResetRounded'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'

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
            : 'linear-gradient(180deg, rgba(37,99,235,0.06) 0%, rgba(255,255,255,0) 100%)',
        text: isDark ? '#E2E8F0' : '#0F172A',
        textMuted: isDark ? '#94A3B8' : '#64748B',
        textSoft: isDark ? '#64748B' : '#94A3B8',
        border: isDark ? '#334155' : '#E2E8F0',
        inputBg: isDark ? '#0F172A' : '#FFFFFF',
        neutralBg: isDark ? '#111827' : '#F8FAFC',
        closeBtnBg: isDark ? '#0B1220' : '#FFFFFF',
        closeBtnHover: isDark ? '#111827' : '#F8FAFC',
    }
}

export default function ChangePasswordDialog({
                                                 open,
                                                 onClose,
                                             }) {
    const {changePassword} = useAuth()
    const {t} = useI18n()
    const [themeMode, setThemeMode] = useState(getThemeMode())

    const [currentPassword, setCurrentPassword] = useState('')
    const [newPassword, setNewPassword] = useState('')
    const [confirmPassword, setConfirmPassword] = useState('')
    const [showCurrent, setShowCurrent] = useState(false)
    const [showNew, setShowNew] = useState(false)
    const [showConfirm, setShowConfirm] = useState(false)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')

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

    const resetState = () => {
        setCurrentPassword('')
        setNewPassword('')
        setConfirmPassword('')
        setShowCurrent(false)
        setShowNew(false)
        setShowConfirm(false)
        setSaving(false)
        setError('')
        setSuccess('')
    }

    const handleClose = () => {
        resetState()
        onClose?.()
    }

    const validate = () => {
        if (!currentPassword.trim()) {
            return t('Please enter your current password.')
        }
        if (!newPassword.trim()) {
            return t('Please enter a new password.')
        }
        if (newPassword.length < 8) {
            return t('New password must be at least 8 characters.')
        }
        if (newPassword !== confirmPassword) {
            return t('New password and confirm password do not match.')
        }
        if (currentPassword === newPassword) {
            return t('New password should be different from current password.')
        }
        return ''
    }

    const handleSubmit = async () => {
        setError('')
        setSuccess('')

        const validationError = validate()
        if (validationError) {
            setError(validationError)
            return
        }

        try {
            setSaving(true)
            await changePassword({
                currentPassword,
                newPassword,
            })
            setSuccess(t('Password updated successfully.'))
            setCurrentPassword('')
            setNewPassword('')
            setConfirmPassword('')
        } catch (e) {
            setError(e?.message || t('Failed to change password.'))
        } finally {
            setSaving(false)
        }
    }

    const passwordAdornment = (visible, setVisible) => ({
        endAdornment: (
            <InputAdornment position="end">
                <IconButton
                    edge="end"
                    onClick={() => setVisible((prev) => !prev)}
                    sx={{color: palette.textMuted}}
                >
                    {visible ? (
                        <VisibilityOffRoundedIcon fontSize="small"/>
                    ) : (
                        <VisibilityRoundedIcon fontSize="small"/>
                    )}
                </IconButton>
            </InputAdornment>
        ),
    })

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
        },
        '& .MuiInputLabel-root': {
            color: palette.textMuted,
        },
        '& .MuiInputLabel-root.Mui-focused': {
            color: '#2563EB',
        },
        '& .MuiFormHelperText-root': {
            color: palette.textMuted,
        },
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
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
                                fontSize: '1.1rem',
                                fontWeight: 800,
                                color: palette.text,
                            }}
                        >
                            {t('Change Password')}
                        </Typography>
                        <Typography
                            sx={{
                                mt: 0.35,
                                fontSize: '0.82rem',
                                color: palette.textMuted,
                            }}
                        >
                            {t('Keep your RetailINQ account secure.')}
                        </Typography>
                    </Box>

                    <IconButton
                        onClick={handleClose}
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
                <Box sx={{display: 'grid', gap: 1.5}}>
                    {error && <Alert severity="error">{error}</Alert>}
                    {success && <Alert severity="success">{success}</Alert>}

                    <TextField
                        label={t('Current Password')}
                        type={showCurrent ? 'text' : 'password'}
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        fullWidth
                        autoComplete="current-password"
                        InputProps={passwordAdornment(showCurrent, setShowCurrent)}
                        sx={textFieldSx}
                    />

                    <TextField
                        label={t('New Password')}
                        type={showNew ? 'text' : 'password'}
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        fullWidth
                        autoComplete="new-password"
                        helperText={t('Use at least 8 characters.')}
                        InputProps={passwordAdornment(showNew, setShowNew)}
                        sx={textFieldSx}
                    />

                    <TextField
                        label={t('Confirm New Password')}
                        type={showConfirm ? 'text' : 'password'}
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        fullWidth
                        autoComplete="new-password"
                        InputProps={passwordAdornment(showConfirm, setShowConfirm)}
                        sx={textFieldSx}
                    />
                </Box>

                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'flex-end',
                        gap: 1.2,
                        mt: 2.5,
                    }}
                >
                    <Button
                        onClick={handleClose}
                        variant="outlined"
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
                        {t('Cancel')}
                    </Button>

                    <Button
                        onClick={handleSubmit}
                        variant="contained"
                        disabled={saving}
                        startIcon={
                            saving ? (
                                <CircularProgress size={16} color="inherit"/>
                            ) : (
                                <LockResetRoundedIcon/>
                            )
                        }
                        sx={{
                            textTransform: 'none',
                            borderRadius: '12px',
                            px: 2.2,
                            fontWeight: 700,
                            background:
                                'linear-gradient(90deg, #2563EB, #1D4ED8)',
                            boxShadow: 'none',
                            '&:hover': {
                                boxShadow: 'none',
                                background:
                                    'linear-gradient(90deg, #1D4ED8, #1E40AF)',
                            },
                        }}
                    >
                        {saving ? t('Updating...') : t('Update Password')}
                    </Button>
                </Box>
            </DialogContent>
        </Dialog>
    )
}
