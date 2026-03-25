import {useEffect, useMemo, useState} from 'react'
import {Box, Typography} from '@mui/material'
import BrandLockup from '../components/BrandLockup'
import Sidebar from '../components/Sidebar'
import UserMenu from '../components/UserMenu'
import MyProfileDialog from '../components/MyProfileDialog'
import ChangePasswordDialog from '../components/ChangePasswordDialog'
import {useAuth} from '../context/AuthContext'
import {useBranding} from '../context/BrandingContext'
import {useI18n} from '../context/I18nContext'
import {PRODUCT_NAME, PRODUCT_TAGLINE, getBrandTokens} from '../branding/brandingUtils'

const THEME_KEY = 'recon_ui_theme'

export default function DashboardWrapper({
    children,
    modules,
    alerts,
    exceptionItems,
    slaItems,
    integration,
    operations,
    activity,
    configurations,
    reports,
    activeTab,
    onSelectTab,
    drawerWidth = 240,
    sidebarCollapsed,
    onSidebarCollapse,
}) {
    const {user} = useAuth()
    const {branding} = useBranding()
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
    const brandTokens = useMemo(() => getBrandTokens(branding), [branding])

    const shellColors = useMemo(() => {
        if (isDark) {
            return {
                appBg: '#020617',
                mainBg: '#0B1220',
                mainText: '#E2E8F0',
                headerBg: 'linear-gradient(90deg, #0F172A, #1E293B)',
                headerBorder: 'rgba(255,255,255,0.08)',
                headerSubText: 'rgba(255,255,255,0.76)',
                mainOverlay: 'none',
            }
        }

        return {
            appBg: '#F4F6FB',
            mainBg: '#F4F6FB',
            mainText: '#22314D',
            headerBg: `linear-gradient(180deg, #FFFFFF 0%, rgba(${brandTokens.primaryRgb}, 0.04) 100%)`,
            headerBorder: '#DCE3EF',
            headerSubText: '#6F7C93',
            mainOverlay: `radial-gradient(circle at top left, rgba(${brandTokens.primaryRgb}, 0.08), transparent 28%), radial-gradient(circle at top right, rgba(${brandTokens.secondaryRgb}, 0.10), transparent 24%)`,
        }
    }, [brandTokens.primaryRgb, brandTokens.secondaryRgb, isDark])

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
                        : '0 8px 28px rgba(34,49,77,0.10)',
                    borderBottom: `1px solid ${shellColors.headerBorder}`,
                    overflow: 'hidden',
                    transition: 'background 0.2s ease',
                    '&::before': {
                        content: '""',
                        position: 'absolute',
                        inset: 0,
                        pointerEvents: 'none',
                        opacity: isDark ? 0.16 : 1,
                        backgroundImage: isDark
                            ? `
                                radial-gradient(circle at 12% 28%, rgba(255,255,255,0.55) 2px, transparent 3px),
                                radial-gradient(circle at 22% 52%, rgba(255,255,255,0.45) 2px, transparent 3px),
                                radial-gradient(circle at 34% 32%, rgba(255,255,255,0.42) 2px, transparent 3px),
                                radial-gradient(circle at 58% 38%, rgba(255,255,255,0.35) 2px, transparent 3px),
                                radial-gradient(circle at 74% 24%, rgba(255,255,255,0.35) 2px, transparent 3px),
                                radial-gradient(circle at 84% 58%, rgba(255,255,255,0.40) 2px, transparent 3px),
                                linear-gradient(rgba(255,255,255,0.12), rgba(255,255,255,0.12)),
                                linear-gradient(rgba(255,255,255,0.10), rgba(255,255,255,0.10)),
                                linear-gradient(rgba(255,255,255,0.08), rgba(255,255,255,0.08))
                              `
                            : `linear-gradient(90deg, rgba(${brandTokens.primaryRgb}, 0.08), transparent 38%, rgba(${brandTokens.secondaryRgb}, 0.10))`,
                        backgroundSize: isDark
                            ? `
                                220px 72px,
                                220px 72px,
                                220px 72px,
                                220px 72px,
                                220px 72px,
                                220px 72px,
                                90px 1px,
                                120px 1px,
                                80px 1px
                              `
                            : '100% 100%',
                        backgroundPosition: isDark
                            ? `
                                0 0,
                                0 0,
                                0 0,
                                0 0,
                                0 0,
                                0 0,
                                12% 28%,
                                22% 52%,
                                34% 32%
                              `
                            : '0 0',
                        backgroundRepeat: isDark ? 'repeat-x' : 'no-repeat',
                    },
                    '&::after': {
                        content: '""',
                        position: 'absolute',
                        inset: 0,
                        pointerEvents: 'none',
                        background: isDark
                            ? 'linear-gradient(180deg, rgba(255,255,255,0.05), rgba(255,255,255,0))'
                            : 'linear-gradient(180deg, rgba(255,255,255,0.85), rgba(255,255,255,0))',
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
                        flexShrink: 0,
                    }}
                >
                    <BrandLockup
                        branding={branding}
                        mode={isDark ? 'dark' : 'light'}
                        appNameColor={isDark ? '#FFFFFF' : '#22314D'}
                        productName={PRODUCT_NAME}
                        subtitle={t(PRODUCT_TAGLINE)}
                        subtitleColor={shellColors.headerSubText}
                        logoWidth={{xs: 86, md: 112}}
                        logoHeight={{xs: 38, md: 44}}
                        defaultLogoWidth={{xs: 176, md: 254}}
                        defaultLogoHeight={{xs: 46, md: 58}}
                        nameFontSize={{xs: '1.14rem', md: '1.34rem'}}
                        nameFontWeight={800}
                        gap={1.1}
                        maxTextWidth={{xs: 160, md: 280}}
                        subtitleWrap
                        hideProductNameWhenDefaultLogo
                    />
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
                alerts={alerts}
                exceptionItems={exceptionItems}
                slaItems={slaItems}
                integration={integration}
                operations={operations}
                activity={activity}
                configurations={configurations}
                reports={reports}
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
                    backgroundImage: shellColors.mainOverlay,
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
