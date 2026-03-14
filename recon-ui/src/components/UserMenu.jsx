import {useMemo, useState} from 'react'
import {
    Avatar,
    Box,
    Chip,
    Divider,
    IconButton,
    ListItemIcon,
    Menu,
    MenuItem,
    Typography,
} from '@mui/material'
import KeyboardArrowDownRoundedIcon from '@mui/icons-material/KeyboardArrowDownRounded'
import PersonOutlineRoundedIcon from '@mui/icons-material/PersonOutlineRounded'
import LanguageRoundedIcon from '@mui/icons-material/LanguageRounded'
import DarkModeRoundedIcon from '@mui/icons-material/DarkModeRounded'
import LightModeRoundedIcon from '@mui/icons-material/LightModeRounded'
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import CheckRoundedIcon from '@mui/icons-material/CheckRounded'
import ShieldRoundedIcon from '@mui/icons-material/ShieldRounded'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'
import LanguageSelectionDialog from './LanguageSelectionDialog'

export default function UserMenu({
    onProfileClick,
    language = 'English',
    languageCode = 'en',
    theme = 'Light',
    onLanguageChange,
    onThemeChange,
}) {
    const {user, logout} = useAuth()
    const {allowedLanguages, languageOptions, t} = useI18n()
    const [anchorEl, setAnchorEl] = useState(null)
    const [languageDialogOpen, setLanguageDialogOpen] = useState(false)

    const open = Boolean(anchorEl)

    const initials = useMemo(() => {
        const source = user?.fullName || user?.username || t('User')
        return source
            .split(' ')
            .filter(Boolean)
            .slice(0, 2)
            .map((part) => part[0]?.toUpperCase())
            .join('')
    }, [t, user])

    const handleOpen = (event) => {
        setAnchorEl(event.currentTarget)
    }

    const handleClose = () => {
        setAnchorEl(null)
    }

    const handleProfile = () => {
        handleClose()
        onProfileClick?.()
    }

    const languageCountText = t('{count} languages enabled', {
        count: allowedLanguages.length,
    })

    const isDark = theme === 'Dark'

    return (
        <>
            <Box
                sx={{
                    position: 'relative',
                    zIndex: 1,
                    display: 'flex',
                    alignItems: 'center',
                }}
            >
                <Box
                    onClick={handleOpen}
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1.25,
                        pl: 0.65,
                        pr: 0.85,
                        py: 0.55,
                        borderRadius: '16px',
                        cursor: 'pointer',
                        border: '1px solid rgba(255,255,255,0.18)',
                        backgroundColor: 'rgba(255,255,255,0.10)',
                        backdropFilter: 'blur(10px)',
                        transition: 'all 0.18s ease',
                        '&:hover': {
                            backgroundColor: 'rgba(255,255,255,0.16)',
                            borderColor: 'rgba(255,255,255,0.28)',
                        },
                    }}
                >
                    <Avatar
                        sx={{
                            width: 36,
                            height: 36,
                            backgroundColor: 'rgba(255,255,255,0.18)',
                            border: '2px solid rgba(255,255,255,0.45)',
                            fontSize: '0.82rem',
                            fontWeight: 800,
                            color: '#ffffff',
                            boxShadow: '0 2px 8px rgba(0,0,0,0.12)',
                        }}
                    >
                        {initials || 'U'}
                    </Avatar>

                    <Box
                        sx={{
                            display: {xs: 'none', sm: 'block'},
                            minWidth: 0,
                        }}
                    >
                        <Typography
                            sx={{
                                fontSize: '0.82rem',
                                fontWeight: 700,
                                color: '#ffffff',
                                lineHeight: 1.15,
                                maxWidth: 180,
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                            }}
                        >
                            {user?.fullName || user?.username}
                        </Typography>

                        <Typography
                            sx={{
                                fontSize: '0.71rem',
                                color: 'rgba(255,255,255,0.82)',
                                lineHeight: 1.15,
                                maxWidth: 180,
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                            }}
                        >
                            {user?.roles?.[0]?.name || t('User')}
                        </Typography>
                    </Box>

                    <IconButton
                        size="small"
                        sx={{
                            ml: {xs: 0, sm: 0.1},
                            color: '#ffffff',
                            p: 0.2,
                        }}
                    >
                        <KeyboardArrowDownRoundedIcon sx={{fontSize: 18}}/>
                    </IconButton>
                </Box>
            </Box>

            <Menu
                anchorEl={anchorEl}
                open={open}
                onClose={handleClose}
                transformOrigin={{horizontal: 'right', vertical: 'top'}}
                anchorOrigin={{horizontal: 'right', vertical: 'bottom'}}
                slotProps={{
                    paper: {
                        elevation: 0,
                        sx: {
                            mt: 1.2,
                            width: 340,
                            overflow: 'hidden',
                            borderRadius: '20px',
                            border: isDark
                                ? '1px solid #334155'
                                : '1px solid #E2E8F0',
                            boxShadow: '0 18px 48px rgba(15, 23, 42, 0.16)',
                            background: isDark
                                ? 'linear-gradient(180deg, #111827 0%, #0F172A 100%)'
                                : 'linear-gradient(180deg, #FFFFFF 0%, #FCFDFE 100%)',
                        },
                    },
                }}
            >
                <Box
                    sx={{
                        px: 2,
                        pt: 2,
                        pb: 1.6,
                        background: isDark
                            ? 'linear-gradient(180deg, rgba(37,99,235,0.14) 0%, rgba(255,255,255,0) 100%)'
                            : 'linear-gradient(180deg, rgba(15,124,134,0.08) 0%, rgba(255,255,255,0) 100%)',
                    }}
                >
                    <Box
                        sx={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 1.4,
                        }}
                    >
                        <Avatar
                            sx={{
                                width: 46,
                                height: 46,
                                background:
                                    'linear-gradient(135deg, #0F7C86 0%, #2563EB 100%)',
                                fontSize: '0.95rem',
                                fontWeight: 800,
                                color: '#ffffff',
                                boxShadow: '0 8px 20px rgba(37,99,235,0.18)',
                            }}
                        >
                            {initials || 'U'}
                        </Avatar>

                        <Box sx={{minWidth: 0, flex: 1}}>
                            <Typography
                                sx={{
                                    fontSize: '0.92rem',
                                    fontWeight: 800,
                                    color: isDark ? '#F8FAFC' : '#0F172A',
                                    lineHeight: 1.15,
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                }}
                            >
                                {user?.fullName || user?.username}
                            </Typography>

                            <Typography
                                sx={{
                                    mt: 0.2,
                                    fontSize: '0.78rem',
                                    color: isDark ? '#94A3B8' : '#64748B',
                                    lineHeight: 1.2,
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                }}
                            >
                                {user?.email || t('No email')}
                            </Typography>

                            <Box
                                sx={{
                                    display: 'flex',
                                    gap: 0.75,
                                    mt: 0.9,
                                    flexWrap: 'wrap',
                                }}
                            >
                                <Chip
                                    size="small"
                                    label={user?.roles?.[0]?.name || t('User')}
                                    sx={{
                                        height: 22,
                                        backgroundColor: '#EFF6FF',
                                        color: '#2563EB',
                                        fontWeight: 700,
                                        fontSize: '0.68rem',
                                    }}
                                />
                                <Chip
                                    size="small"
                                    icon={
                                        <ShieldRoundedIcon
                                            sx={{
                                                fontSize:
                                                    '0.85rem !important',
                                            }}
                                        />
                                    }
                                    label={t('Secure Access')}
                                    sx={{
                                        height: 22,
                                        backgroundColor: '#ECFEFF',
                                        color: '#0F7C86',
                                        fontWeight: 700,
                                        fontSize: '0.68rem',
                                    }}
                                />
                            </Box>
                        </Box>
                    </Box>
                </Box>

                <Divider sx={{borderColor: isDark ? '#1E293B' : '#F1F5F9'}}/>

                <Box sx={{py: 0.8}}>
                    <MenuItem
                        onClick={handleProfile}
                        sx={{
                            mx: 1,
                            mb: 0.35,
                            minHeight: 44,
                            borderRadius: 2.5,
                            '&:hover': {
                                backgroundColor: isDark
                                    ? '#1E293B'
                                    : '#F8FAFC',
                            },
                        }}
                    >
                        <ListItemIcon
                            sx={{
                                minWidth: 34,
                                color: isDark ? '#94A3B8' : '#64748B',
                            }}
                        >
                            <PersonOutlineRoundedIcon fontSize="small"/>
                        </ListItemIcon>
                        <Typography
                            sx={{
                                fontSize: '0.86rem',
                                fontWeight: 600,
                                color: isDark ? '#F8FAFC' : '#0F172A',
                            }}
                        >
                            {t('My Profile')}
                        </Typography>
                    </MenuItem>
                </Box>

                <Divider sx={{borderColor: isDark ? '#1E293B' : '#F1F5F9'}}/>

                <Box sx={{px: 1.25, pt: 1.1, pb: 0.5}}>
                    <Typography
                        sx={{
                            fontSize: '0.7rem',
                            fontWeight: 800,
                            letterSpacing: '0.5px',
                            color: isDark ? '#64748B' : '#94A3B8',
                            textTransform: 'uppercase',
                            px: 0.75,
                            mb: 0.8,
                        }}
                    >
                        {t('Preferences')}
                    </Typography>

                    <Box
                        sx={{
                            px: 0.75,
                            py: 0.8,
                            borderRadius: 2.5,
                            border: isDark
                                ? '1px solid #334155'
                                : '1px solid #E2E8F0',
                            backgroundColor: isDark ? '#111827' : '#F8FAFC',
                            mb: 0.9,
                        }}
                    >
                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                gap: 1,
                                mb: 0.75,
                            }}
                        >
                            <Box
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 1,
                                }}
                            >
                                <LanguageRoundedIcon
                                    sx={{
                                        fontSize: 18,
                                        color: isDark
                                            ? '#94A3B8'
                                            : '#64748B',
                                    }}
                                />
                                <Typography
                                    sx={{
                                        fontSize: '0.82rem',
                                        fontWeight: 700,
                                        color: isDark
                                            ? '#F8FAFC'
                                            : '#0F172A',
                                    }}
                                >
                                    {t('Language')}
                                </Typography>
                            </Box>

                            <Chip
                                size="small"
                                label={language}
                                sx={{
                                    height: 22,
                                    backgroundColor: isDark
                                        ? '#0F172A'
                                        : '#FFFFFF',
                                    color: '#2563EB',
                                    fontWeight: 700,
                                    fontSize: '0.68rem',
                                    border: '1px solid #DBEAFE',
                                }}
                            />
                        </Box>

                        <Box sx={{display: 'flex', gap: 0.75, flexWrap: 'wrap'}}>
                            <Chip
                                label={languageCountText}
                                size="small"
                                sx={{
                                    height: 26,
                                    fontWeight: 700,
                                    fontSize: '0.7rem',
                                    backgroundColor: isDark
                                        ? '#0F172A'
                                        : '#FFFFFF',
                                    color: isDark ? '#CBD5E1' : '#475569',
                                    border: isDark
                                        ? '1px solid #334155'
                                        : '1px solid #E2E8F0',
                                }}
                            />
                            <Chip
                                label={t('Change Language')}
                                onClick={() => setLanguageDialogOpen(true)}
                                icon={<CheckRoundedIcon/>}
                                size="small"
                                sx={{
                                    height: 26,
                                    fontWeight: 700,
                                    fontSize: '0.7rem',
                                    backgroundColor: '#EFF6FF',
                                    color: '#2563EB',
                                    border: '1px solid #BFDBFE',
                                }}
                            />
                        </Box>
                    </Box>

                    <Box
                        sx={{
                            px: 0.75,
                            py: 0.8,
                            borderRadius: 2.5,
                            border: isDark
                                ? '1px solid #334155'
                                : '1px solid #E2E8F0',
                            backgroundColor: isDark ? '#111827' : '#F8FAFC',
                            mb: 1,
                        }}
                    >
                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                gap: 1,
                                mb: 0.75,
                            }}
                        >
                            <Box
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 1,
                                }}
                            >
                                {theme === 'Light' ? (
                                    <LightModeRoundedIcon
                                        sx={{
                                            fontSize: 18,
                                            color: isDark
                                                ? '#94A3B8'
                                                : '#64748B',
                                        }}
                                    />
                                ) : (
                                    <DarkModeRoundedIcon
                                        sx={{
                                            fontSize: 18,
                                            color: isDark
                                                ? '#94A3B8'
                                                : '#64748B',
                                        }}
                                    />
                                )}

                                <Typography
                                    sx={{
                                        fontSize: '0.82rem',
                                        fontWeight: 700,
                                        color: isDark
                                            ? '#F8FAFC'
                                            : '#0F172A',
                                    }}
                                >
                                    {t('Theme')}
                                </Typography>
                            </Box>

                            <Chip
                                size="small"
                                label={t(theme)}
                                sx={{
                                    height: 22,
                                    backgroundColor: isDark
                                        ? '#0F172A'
                                        : '#FFFFFF',
                                    color: '#0F7C86',
                                    fontWeight: 700,
                                    fontSize: '0.68rem',
                                    border: '1px solid #CCFBF1',
                                }}
                            />
                        </Box>

                        <Box sx={{display: 'flex', gap: 0.75}}>
                            {['Light', 'Dark'].map((item) => {
                                const selected = theme === item
                                return (
                                    <Chip
                                        key={item}
                                        label={t(item)}
                                        onClick={() => onThemeChange?.(item)}
                                        icon={
                                            selected ? (
                                                <CheckRoundedIcon/>
                                            ) : undefined
                                        }
                                        size="small"
                                        sx={{
                                            height: 26,
                                            fontWeight: 700,
                                            fontSize: '0.7rem',
                                            backgroundColor: selected
                                                ? '#ECFEFF'
                                                : isDark
                                                    ? '#0F172A'
                                                    : '#FFFFFF',
                                            color: selected
                                                ? '#0F7C86'
                                                : isDark
                                                    ? '#CBD5E1'
                                                    : '#475569',
                                            border: selected
                                                ? '1px solid #99F6E4'
                                                : isDark
                                                    ? '1px solid #334155'
                                                    : '1px solid #E2E8F0',
                                        }}
                                    />
                                )
                            })}
                        </Box>
                    </Box>
                </Box>

                <Divider sx={{borderColor: isDark ? '#1E293B' : '#F1F5F9'}}/>

                <Box sx={{p: 1}}>
                    <MenuItem
                        onClick={logout}
                        sx={{
                            minHeight: 46,
                            borderRadius: 2.75,
                            color: '#DC2626',
                            '&:hover': {
                                backgroundColor: '#FEF2F2',
                            },
                        }}
                    >
                        <ListItemIcon sx={{minWidth: 34, color: '#DC2626'}}>
                            <LogoutRoundedIcon fontSize="small"/>
                        </ListItemIcon>
                        <Typography
                            sx={{fontSize: '0.86rem', fontWeight: 700}}
                        >
                            {t('Sign out')}
                        </Typography>
                    </MenuItem>
                </Box>
            </Menu>

            <LanguageSelectionDialog
                open={languageDialogOpen}
                onClose={() => setLanguageDialogOpen(false)}
                value={languageCode}
                onChange={onLanguageChange}
                allowedCodes={allowedLanguages}
                title={t('Change Language')}
                subtitle={t('Your quick switcher only shows assigned languages.')}
            />
        </>
    )
}
