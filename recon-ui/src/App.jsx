import {useEffect, useMemo, useState} from 'react'
import {Navigate, Route, Routes} from 'react-router-dom'
import {createTheme, CssBaseline, ThemeProvider} from '@mui/material'
import {useAuth} from './context/AuthContext'
import {I18nProvider, useI18n} from './context/I18nContext'
import DashboardWrapper from './pages/DashboardWrapper'
import Dashboard from './pages/Dashboard'
import KpiMockup from './pages/KpiMockup'
import Login from './pages/Login'
import Unauthorized from './pages/Unauthorized'
import ProtectedRoute from './components/ProtectedRoute'
import {ACTIVITY_TAB_IDS, ALERT_TAB_IDS, CONFIGURATION_TAB_IDS, EXCEPTION_TAB_IDS, OPERATIONS_TAB_IDS, RECONCILIATION_TAB_IDS, REPORT_TAB_IDS, SLA_TAB_IDS, getTabLabel} from './constants/navigation'

function buildTheme() {
    return createTheme({
        direction: 'ltr',
    palette: {
        mode: 'light',
        primary: {main: '#2563EB'},
        secondary: {main: '#06B6D4'},
        background: {
            default: '#F8FAFC',
            paper: '#FFFFFF',
        },
        text: {
            primary: '#0F172A',
            secondary: '#64748B',
        },
        divider: '#E2E8F0',
        success: {main: '#16A34A'},
        error: {main: '#EF4444'},
        warning: {main: '#D97706'},
        info: {main: '#0F7C86'},
    },
    shape: {borderRadius: 16},
    typography: {
        fontFamily: 'Inter, Roboto, sans-serif',
        h4: {
            fontWeight: 700,
            color: '#0F172A',
        },
        h5: {
            fontWeight: 700,
            color: '#0F172A',
        },
        h6: {
            fontWeight: 600,
            color: '#0F172A',
        },
        subtitle1: {
            fontWeight: 600,
        },
        subtitle2: {
            fontWeight: 600,
        },
        button: {
            textTransform: 'none',
            fontWeight: 600,
        },
    },
    components: {
        MuiCssBaseline: {
            styleOverrides: {
                body: {
                    backgroundColor: '#F8FAFC',
                    color: '#0F172A',
                },
            },
        },
        MuiButton: {
            defaultProps: {
                disableElevation: true,
            },
            styleOverrides: {
                root: {
                    borderRadius: 12,
                },
                containedPrimary: {
                    backgroundColor: '#2563EB',
                    '&:hover': {
                        backgroundColor: '#1D4ED8',
                    },
                },
            },
        },
        MuiChip: {
            styleOverrides: {
                root: {
                    borderRadius: 999,
                    fontWeight: 600,
                },
            },
        },
        MuiCard: {
            styleOverrides: {
                root: {
                    borderRadius: 20,
                    boxShadow: '0 8px 24px rgba(15, 23, 42, 0.06)',
                },
            },
        },
        MuiPaper: {
            styleOverrides: {
                root: {
                    backgroundImage: 'none',
                },
                rounded: {
                    borderRadius: 20,
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
                    borderRadius: 24,
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
                    borderRadius: 14,
                    backgroundColor: '#FFFFFF',
                },
            },
        },
        MuiTableCell: {
            styleOverrides: {
                root: {
                    borderColor: '#F1F5F9',
                },
                head: {
                    color: '#334155',
                    fontWeight: 700,
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
    const theme = useMemo(() => buildTheme(), [])

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
            <AppShell/>
        </I18nProvider>
    )
}
