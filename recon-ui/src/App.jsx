import {useEffect, useMemo, useState} from 'react'
import {Navigate, Route, Routes} from 'react-router-dom'
import {createTheme, CssBaseline, ThemeProvider} from '@mui/material'
import {useAuth} from './context/AuthContext'
import {BrandingProvider, useBranding} from './context/BrandingContext'
import {I18nProvider, useI18n} from './context/I18nContext'
import DashboardWrapper from './pages/DashboardWrapper'
import Dashboard from './pages/Dashboard'
import KpiMockup from './pages/KpiMockup'
import Login from './pages/Login'
import Unauthorized from './pages/Unauthorized'
import ProtectedRoute from './components/ProtectedRoute'
import {ACTIVITY_TAB_IDS, ALERT_TAB_IDS, CONFIGURATION_TAB_IDS, EXCEPTION_TAB_IDS, OPERATIONS_TAB_IDS, RECONCILIATION_TAB_IDS, REPORT_TAB_IDS, SLA_TAB_IDS, getTabLabel} from './constants/navigation'
import {getBrandTokens} from './branding/brandingUtils'

const THEME_KEY = 'recon_ui_theme'

function getThemeMode() {
    if (typeof document !== 'undefined') {
        const attrMode = document.documentElement.getAttribute('data-recon-theme')
        if (attrMode === 'dark') {
            return 'Dark'
        }
        if (attrMode === 'light') {
            return 'Light'
        }
    }

    if (typeof window !== 'undefined') {
        const stored = window.localStorage.getItem(THEME_KEY)
        if (stored === 'Dark' || stored === 'Light') {
            return stored
        }
    }

    return 'Light'
}

function buildTheme(branding, themeMode) {
    const brandTokens = getBrandTokens(branding)
    const isDark = themeMode === 'Dark'
    const surface = isDark ? '#0F172A' : '#FFFFFF'
    const surfaceAlt = isDark ? '#111827' : '#F8FAFC'
    const bodyBg = isDark ? '#020617' : '#F4F6FB'
    const textPrimary = isDark ? '#E2E8F0' : '#22314D'
    const textSecondary = isDark ? '#94A3B8' : '#6C7A92'
    const divider = isDark ? '#233245' : '#D8E0ED'

    return createTheme({
        direction: 'ltr',
        palette: {
            mode: isDark ? 'dark' : 'light',
            primary: {main: brandTokens.primary},
            secondary: {main: brandTokens.secondary},
            background: {
                default: bodyBg,
                paper: surface,
            },
            text: {
                primary: textPrimary,
                secondary: textSecondary,
            },
            divider,
            success: {main: isDark ? '#22C55E' : '#1F8A62'},
            error: {main: isDark ? '#F87171' : '#D84F5F'},
            warning: {main: isDark ? '#FBBF24' : '#D38A32'},
            info: {main: brandTokens.primary},
        },
        shape: {borderRadius: 18},
        typography: {
            fontFamily: 'Manrope, "Segoe UI Variable", "Segoe UI", sans-serif',
            h4: {
                fontWeight: 800,
                color: textPrimary,
                letterSpacing: '-0.03em',
            },
            h5: {
                fontWeight: 800,
                color: textPrimary,
                letterSpacing: '-0.02em',
            },
            h6: {
                fontWeight: 700,
                color: textPrimary,
            },
            subtitle1: {
                fontWeight: 600,
            },
            subtitle2: {
                fontWeight: 700,
            },
            button: {
                textTransform: 'none',
                fontWeight: 700,
                letterSpacing: '-0.01em',
            },
        },
        components: {
            MuiCssBaseline: {
                styleOverrides: {
                    body: {
                        backgroundColor: bodyBg,
                        backgroundImage: isDark
                            ? 'radial-gradient(circle at top left, rgba(var(--brand-primary-rgb), 0.08), transparent 32%), radial-gradient(circle at bottom right, rgba(var(--brand-secondary-rgb), 0.10), transparent 28%)'
                            : `radial-gradient(circle at top left, ${brandTokens.primarySurface}, transparent 36%), radial-gradient(circle at bottom right, ${brandTokens.secondarySurface}, transparent 26%)`,
                        color: textPrimary,
                    },
                    '::selection': {
                        backgroundColor: brandTokens.primaryGlow,
                    },
                },
            },
            MuiButton: {
                defaultProps: {
                    disableElevation: true,
                },
                styleOverrides: {
                    root: {
                        minHeight: 40,
                        borderRadius: 14,
                        paddingInline: 16,
                        borderWidth: 1,
                        borderStyle: 'solid',
                        borderColor: 'transparent',
                        boxShadow: 'none',
                        transition:
                            'background-color 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, color 0.18s ease, transform 0.18s ease',
                        '&:hover': {
                            boxShadow: `0 10px 22px ${brandTokens.primaryGlow}`,
                            transform: 'translateY(-1px)',
                        },
                        '&.Mui-disabled': {
                            transform: 'none',
                            boxShadow: 'none',
                        },
                    },
                    sizeSmall: {
                        minHeight: 34,
                        borderRadius: 12,
                        paddingInline: 13,
                    },
                    contained: {
                        background: brandTokens.buttonGradient,
                        color: '#FFFFFF',
                        borderColor: brandTokens.primaryBorder,
                        boxShadow: `0 12px 24px ${brandTokens.primaryGlow}`,
                        '&:hover': {
                            background: brandTokens.buttonGradientHover,
                            borderColor: brandTokens.secondaryBorder,
                        },
                    },
                    outlined: {
                        backgroundColor: isDark
                            ? 'rgba(15, 23, 42, 0.92)'
                            : '#FFFFFF',
                        color: isDark ? textPrimary : brandTokens.primary,
                        borderColor: isDark ? divider : '#D7DFEC',
                        boxShadow: isDark
                            ? '0 10px 20px rgba(0, 0, 0, 0.18)'
                            : '0 8px 18px rgba(34, 49, 77, 0.06)',
                        '&:hover': {
                            backgroundColor: isDark
                                ? 'rgba(var(--brand-primary-rgb), 0.14)'
                                : '#F8FAFF',
                            borderColor: brandTokens.primaryBorder,
                            boxShadow: `0 12px 24px ${brandTokens.primaryGlow}`,
                        },
                    },
                    outlinedError: {
                        color: '#C24148',
                        borderColor: '#F0C9CD',
                        '&:hover': {
                            backgroundColor: '#FFF5F6',
                            borderColor: '#E69AA3',
                        },
                    },
                    text: {
                        color: isDark ? textPrimary : brandTokens.primary,
                        backgroundColor: isDark
                            ? 'rgba(15,23,42,0.64)'
                            : 'rgba(255,255,255,0.72)',
                        borderColor: isDark ? divider : '#E0E7F1',
                        '&:hover': {
                            backgroundColor: isDark
                                ? 'rgba(var(--brand-primary-rgb), 0.12)'
                                : '#FFFFFF',
                            borderColor: isDark ? brandTokens.primaryBorder : '#CBD7E7',
                        },
                    },
                    textError: {
                        color: '#C24148',
                        borderColor: '#F3D3D7',
                        '&:hover': {
                            backgroundColor: '#FFF6F7',
                            borderColor: '#E8B2BA',
                        },
                    },
                },
            },
            MuiChip: {
                styleOverrides: {
                    root: {
                        borderRadius: 999,
                        fontWeight: 700,
                    },
                },
            },
            MuiCard: {
                styleOverrides: {
                    root: {
                        borderRadius: 24,
                        boxShadow: isDark
                            ? '0 18px 40px rgba(0,0,0,0.28)'
                            : '0 16px 36px rgba(31, 45, 84, 0.08)',
                    },
                },
            },
            MuiPaper: {
                styleOverrides: {
                    root: {
                        backgroundImage: 'none',
                        backgroundColor: surface,
                        color: textPrimary,
                    },
                    rounded: {
                        borderRadius: 22,
                    },
                },
            },
            MuiDrawer: {
                styleOverrides: {
                    paper: {
                        backgroundImage: 'none',
                    },
                },
            },
            MuiDialog: {
                styleOverrides: {
                    paper: {
                        borderRadius: 28,
                        backgroundColor: surface,
                        color: textPrimary,
                    },
                },
            },
            MuiTextField: {
                defaultProps: {
                    size: 'small',
                },
            },
            MuiOutlinedInput: {
                styleOverrides: {
                    root: {
                        borderRadius: 16,
                        backgroundColor: isDark ? surfaceAlt : '#FFFFFF',
                        color: textPrimary,
                        '& .MuiOutlinedInput-notchedOutline': {
                            borderColor: divider,
                        },
                        '&:hover .MuiOutlinedInput-notchedOutline': {
                            borderColor: isDark ? '#475569' : '#B7C4D9',
                        },
                        '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                            borderColor: brandTokens.primary,
                        },
                        '& .MuiSvgIcon-root': {
                            color: textSecondary,
                        },
                    },
                },
            },
            MuiInputLabel: {
                styleOverrides: {
                    root: {
                        color: textSecondary,
                        '&.Mui-focused': {
                            color: brandTokens.primary,
                        },
                    },
                },
            },
            MuiFormHelperText: {
                styleOverrides: {
                    root: {
                        color: textSecondary,
                    },
                },
            },
            MuiMenu: {
                styleOverrides: {
                    paper: {
                        backgroundColor: surface,
                        color: textPrimary,
                        border: `1px solid ${divider}`,
                    },
                },
            },
            MuiTableCell: {
                styleOverrides: {
                    root: {
                        borderColor: isDark ? divider : '#E7ECF4',
                        color: textPrimary,
                    },
                    head: {
                        color: isDark ? '#F8FAFC' : '#42506A',
                        fontWeight: 800,
                        backgroundColor: isDark ? '#111827' : '#FFFFFF',
                    },
                },
            },
        },
    })
}

function AppRoutes() {
    const [openTabs, setOpenTabs] = useState([])
    const [activeTab, setActiveTab] = useState(null)
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false)

    const {isAuthenticated, user} = useAuth()
    const {t} = useI18n()
    const drawerWidth = sidebarCollapsed ? 64 : 240

    const reconciliationPairs = RECONCILIATION_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const alertPairs = ALERT_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const exceptionPairs = EXCEPTION_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const operationsPairs = OPERATIONS_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const slaPairs = SLA_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const activityPairs = ACTIVITY_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const reportPairs = REPORT_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const configurationPairs = CONFIGURATION_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))

    useEffect(() => {
        setOpenTabs([])
        setActiveTab(null)
    }, [user?.username])

    const handleSidebarClick = (id) => {
        if (openTabs.includes(id)) {
            setActiveTab(id)
            return
        }
        setOpenTabs((prev) => [...prev, id])
        setActiveTab(id)
    }

    const handleCloseTab = (tabId) => {
        const idx = openTabs.indexOf(tabId)
        const updated = openTabs.filter((id) => id !== tabId)
        setOpenTabs(updated)

        if (activeTab !== tabId) return

        setActiveTab(
            updated.length === 0
                ? null
                : updated[idx - 1] ?? updated[idx] ?? null
        )
    }

    const wrapper = (child) => (
            <DashboardWrapper
                drawerWidth={drawerWidth}
                modules={reconciliationPairs}
                alerts={alertPairs}
                exceptionItems={exceptionPairs}
                slaItems={slaPairs}
                operations={operationsPairs}
                activity={activityPairs}
                configurations={configurationPairs}
                reports={reportPairs}
                activeTab={activeTab}
            onSelectTab={handleSidebarClick}
            sidebarCollapsed={sidebarCollapsed}
            onSidebarCollapse={setSidebarCollapsed}
        >
            {child}
        </DashboardWrapper>
    )

    return (
        <Routes>
            <Route
                path="/login"
                element={
                    isAuthenticated ? (
                        <Navigate to="/" replace/>
                    ) : (
                        <Login/>
                    )
                }
            />

            <Route path="/unauthorized" element={<Unauthorized/>}/>

            <Route
                path="/"
                element={
                    <ProtectedRoute>
                        {wrapper(
                            <Dashboard
                                openTabs={openTabs}
                                activeTab={activeTab}
                                setActiveTab={setActiveTab}
                                handleCloseTab={handleCloseTab}
                            />
                        )}
                    </ProtectedRoute>
                }
            />

            <Route
                path="/kpi-mockup"
                element={
                    <ProtectedRoute>
                        <KpiMockup/>
                    </ProtectedRoute>
                }
            />

            <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
    )
}

function AppShell() {
    const {branding} = useBranding()
    const [themeMode, setThemeMode] = useState(() => getThemeMode())

    useEffect(() => {
        const next = themeMode.toLowerCase()
        document.documentElement.setAttribute('data-recon-theme', next)
        window.localStorage.setItem(THEME_KEY, themeMode)
    }, [themeMode])

    useEffect(() => {
        const syncTheme = () => {
            setThemeMode(getThemeMode())
        }

        syncTheme()

        const observer = new MutationObserver(syncTheme)
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-recon-theme'],
        })

        window.addEventListener('storage', syncTheme)

        return () => {
            observer.disconnect()
            window.removeEventListener('storage', syncTheme)
        }
    }, [])

    const theme = useMemo(
        () => buildTheme(branding, themeMode),
        [branding, themeMode]
    )

    return (
        <ThemeProvider theme={theme}>
            <CssBaseline/>
            <AppRoutes/>
        </ThemeProvider>
    )
}

export default function App() {
    return (
        <I18nProvider>
            <BrandingProvider>
                <AppShell/>
            </BrandingProvider>
        </I18nProvider>
    )
}
