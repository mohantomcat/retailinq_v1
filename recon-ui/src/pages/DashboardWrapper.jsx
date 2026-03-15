import {useEffect, useMemo, useState} from 'react'
import {Box, Typography} from '@mui/material'
import Sidebar from '../components/Sidebar'
import UserMenu from '../components/UserMenu'
import MyProfileDialog from '../components/MyProfileDialog'
import ChangePasswordDialog from '../components/ChangePasswordDialog'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'

const THEME_KEY = 'recon_ui_theme'

export default function DashboardWrapper({
    children,
    modules,
    configurations,
    activeTab,
    onSelectTab,
    drawerWidth = 240,
    sidebarCollapsed,
    onSidebarCollapse,
}) {
    const {user} = useAuth()
    const {language, languageLabel, setLanguage, t, direction, isRTL} =
        useI18n()
    const [profileOpen, setProfileOpen] = useState(false)
    const [passwordOpen, setPasswordOpen] = useState(false)
    const [theme, setTheme] = useState(() =>
        localStorage.getItem(THEME_KEY) || 'Light'
    )

    useEffect(() => {
        localStorage.setItem(THEME_KEY, theme)
    }, [theme])

    useEffect(() => {
        document.documentElement.setAttribute(
            'data-recon-theme',
            theme.toLowerCase()
        )
        document.documentElement.setAttribute(
            'data-recon-language',
            language
        )
    }, [theme, language])

    const isDark = theme === 'Dark'

    const shellColors = useMemo(() => {
        if (isDark) {
            return {
                appBg: '#020617',
                mainBg: '#0B1220',
                mainText: '#E2E8F0',
                headerBg: 'linear-gradient(90deg, #0F172A, #1E293B)',
                headerSubText: 'rgba(255,255,255,0.76)',
                brandInq: '#60A5FA',
            }
        }

        return {
            appBg: '#F8FAFC',
            mainBg: '#F8FAFC',
            mainText: '#0F172A',
            headerBg: 'linear-gradient(90deg, #0F7C86, #157F8B)',
            headerSubText: 'rgba(255,255,255,0.86)',
            brandInq: '#93C5FD',
        }
    }, [isDark])

    return (
        <Box
            sx={{
                display: 'flex',
                minHeight: '100vh',
                bgcolor: shellColors.appBg,
                color: shellColors.mainText,
                direction: 'ltr',
                transition: 'background-color 0.2s ease, color 0.2s ease',
            }}
        >
            <Box
                component="header"
                sx={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    width: '100vw',
                    height: 72,
                    zIndex: 1300,
                    display: 'flex',
                    alignItems: 'center',
                    px: {xs: 2, md: 3},
                    background: shellColors.headerBg,
                    boxShadow: isDark
                        ? '0 3px 12px rgba(0,0,0,0.34)'
                        : '0 3px 10px rgba(0,0,0,0.18)',
                    overflow: 'hidden',
                    transition: 'background 0.2s ease',
                    '&::before': {
                        content: '""',
                        position: 'absolute',
                        inset: 0,
                        pointerEvents: 'none',
                        opacity: 0.16,
                        backgroundImage: `
                            radial-gradient(circle at 12% 28%, rgba(255,255,255,0.55) 2px, transparent 3px),
                            radial-gradient(circle at 22% 52%, rgba(255,255,255,0.45) 2px, transparent 3px),
                            radial-gradient(circle at 34% 32%, rgba(255,255,255,0.42) 2px, transparent 3px),
                            radial-gradient(circle at 58% 38%, rgba(255,255,255,0.35) 2px, transparent 3px),
                            radial-gradient(circle at 74% 24%, rgba(255,255,255,0.35) 2px, transparent 3px),
                            radial-gradient(circle at 84% 58%, rgba(255,255,255,0.40) 2px, transparent 3px),
                            linear-gradient(rgba(255,255,255,0.12), rgba(255,255,255,0.12)),
                            linear-gradient(rgba(255,255,255,0.10), rgba(255,255,255,0.10)),
                            linear-gradient(rgba(255,255,255,0.08), rgba(255,255,255,0.08))
                        `,
                        backgroundSize: `
                            220px 72px,
                            220px 72px,
                            220px 72px,
                            220px 72px,
                            220px 72px,
                            220px 72px,
                            90px 1px,
                            120px 1px,
                            80px 1px
                        `,
                        backgroundPosition: `
                            0 0,
                            0 0,
                            0 0,
                            0 0,
                            0 0,
                            0 0,
                            12% 28%,
                            22% 52%,
                            34% 32%
                        `,
                        backgroundRepeat: 'repeat-x',
                    },
                    '&::after': {
                        content: '""',
                        position: 'absolute',
                        inset: 0,
                        pointerEvents: 'none',
                        background:
                            'linear-gradient(180deg, rgba(255,255,255,0.05), rgba(255,255,255,0))',
                    },
                }}
            >
                <Box
                    sx={{
                        position: 'relative',
                        zIndex: 1,
                        display: 'flex',
                        alignItems: 'center',
                        minWidth: 0,
                        direction: 'ltr',
                    }}
                >
                    <Box
                        sx={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: 48,
                            height: 48,
                            mr: 1.5,
                            flexShrink: 0,
                        }}
                    >
                        <img
                            src="/logo9.svg"
                            alt="RetailINQ Logo"
                            style={{
                                width: '100%',
                                height: '100%',
                                objectFit: 'contain',
                                display: 'block',
                            }}
                        />
                    </Box>

                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'center',
                            minWidth: 0,
                            mr: 3,
                        }}
                    >
                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: 'baseline',
                                lineHeight: 1,
                            }}
                        >
                            <Typography
                                sx={{
                                    fontSize: {xs: '1.35rem', md: '1.5rem'},
                                    fontWeight: 600,
                                    color: '#ffffff',
                                    letterSpacing: '-0.4px',
                                    lineHeight: 1,
                                }}
                            >
                                Retail
                            </Typography>
                            <Typography
                                sx={{
                                    ml: 0.15,
                                    fontSize: {xs: '1.35rem', md: '1.5rem'},
                                    fontWeight: 800,
                                    letterSpacing: '-0.4px',
                                    lineHeight: 1,
                                    color: shellColors.brandInq,
                                    textShadow:
                                        '0 1px 4px rgba(37,99,235,0.18)',
                                }}
                            >
                                INQ
                            </Typography>
                        </Box>

                        <Typography
                            sx={{
                                mt: 0.4,
                                fontSize: '0.78rem',
                                fontWeight: 400,
                                color: shellColors.headerSubText,
                                letterSpacing: '0.25px',
                                lineHeight: 1.2,
                                display: {xs: 'none', md: 'block'},
                                whiteSpace: 'nowrap',
                            }}
                        >
                            {t(
                                'Retail Reconciliation • Analytics • Integrations'
                            )}
                        </Typography>
                    </Box>
                </Box>

                <Box sx={{flexGrow: 1}}/>

                <UserMenu
                    onProfileClick={() => setProfileOpen(true)}
                    language={languageLabel}
                    languageCode={language}
                    theme={theme}
                    onLanguageChange={setLanguage}
                    onThemeChange={setTheme}
                />
            </Box>

            <Sidebar
                width={drawerWidth}
                collapsed={sidebarCollapsed}
                onCollapse={onSidebarCollapse}
                modules={modules}
                configurations={configurations}
                activeTab={activeTab}
                onSelect={onSelectTab}
                themeMode={theme}
            />

            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    width: `calc(100% - ${drawerWidth}px)`,
                    mt: '72px',
                    px: 3,
                    pb: 4,
                    transition:
                        'width 0.2s ease, margin-left 0.2s ease, background-color 0.2s ease',
                    backgroundColor: shellColors.mainBg,
                    color: shellColors.mainText,
                    minHeight: 'calc(100vh - 72px)',
                    direction,
                    textAlign: isRTL ? 'right' : 'left',
                }}
            >
                {children}
            </Box>

            <MyProfileDialog
                open={profileOpen}
                onClose={() => setProfileOpen(false)}
                user={user}
                onChangePassword={() => {
                    setProfileOpen(false)
                    setPasswordOpen(true)
                }}
            />

            <ChangePasswordDialog
                open={passwordOpen}
                onClose={() => setPasswordOpen(false)}
            />
        </Box>
    )
}
