import {useState} from 'react'
import {
    Box,
    Collapse,
    Divider,
    Drawer,
    IconButton,
    List,
    ListItemButton,
    ListItemText,
    Tooltip,
    Typography,
} from '@mui/material'
import ExpandLess from '@mui/icons-material/ExpandLess'
import ExpandMore from '@mui/icons-material/ExpandMore'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import CompareArrowsIcon from '@mui/icons-material/CompareArrows'
import AssessmentIcon from '@mui/icons-material/Assessment'
import SettingsIcon from '@mui/icons-material/Settings'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings'
import SyncAltIcon from '@mui/icons-material/SyncAlt'
import StorefrontIcon from '@mui/icons-material/Storefront'
import InventoryIcon from '@mui/icons-material/Inventory'
import BarChartIcon from '@mui/icons-material/BarChart'
import TuneIcon from '@mui/icons-material/Tune'
import PeopleIcon from '@mui/icons-material/People'
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts'
import SecurityIcon from '@mui/icons-material/Security'
import SettingsSuggestIcon from '@mui/icons-material/SettingsSuggest'
import DnsRoundedIcon from '@mui/icons-material/DnsRounded'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'
import {CONFIGURATION_TAB_IDS, getTabLabel, SECURITY_TAB_IDS} from '../constants/navigation'

const ITEM_PERMISSIONS = {
    'xstore-sim': 'RECON_XSTORE_SIM',
    'xstore-siocs': 'RECON_XSTORE_SIOCS',
    'xstore-xocs': 'RECON_XSTORE_XOCS',
    'sim-rms': 'RECON_SIM_RMS',
    reports: 'REPORTS_VIEW',
    settings: 'SETTINGS_VIEW',
    'manage-users': 'ADMIN_USERS',
    'manage-roles': 'ADMIN_ROLES',
    'manage-perms': 'ADMIN_PERMISSIONS',
    'module-configs': 'CONFIG_MODULE_VIEW',
    'system-configs': 'CONFIG_SYSTEM_VIEW',
}

const SECTION_PERMISSIONS = {
    reconciliation: 'RECON_VIEW',
    reports: 'REPORTS_VIEW',
    settings: 'SETTINGS_VIEW',
    configurations: ['CONFIG_MODULE_VIEW', 'CONFIG_SYSTEM_VIEW'],
    security: 'ADMIN_USERS',
}

const SECTION_ICONS = {
    reconciliation: <CompareArrowsIcon sx={{fontSize: 18}}/>,
    reports: <AssessmentIcon sx={{fontSize: 18}}/>,
    settings: <SettingsIcon sx={{fontSize: 18}}/>,
    configurations: <SettingsSuggestIcon sx={{fontSize: 18}}/>,
    security: <AdminPanelSettingsIcon sx={{fontSize: 18}}/>,
}

const SECTION_COLORS = {
    reconciliation: {
        icon: '#0F7C86',
        bg: '#ECFEFF',
    },
    reports: {
        icon: '#2563EB',
        bg: '#EFF6FF',
    },
    settings: {
        icon: '#D97706',
        bg: '#FFF7ED',
    },
    configurations: {
        icon: '#2563EB',
        bg: '#EFF6FF',
    },
    security: {
        icon: '#7C3AED',
        bg: '#F5F3FF',
    },
}

const ITEM_ICONS = {
    'xstore-sim': <SyncAltIcon sx={{fontSize: 17}}/>,
    'xstore-siocs': <SyncAltIcon sx={{fontSize: 17}}/>,
    'xstore-xocs': <StorefrontIcon sx={{fontSize: 17}}/>,
    'sim-rms': <InventoryIcon sx={{fontSize: 17}}/>,
    reports: <BarChartIcon sx={{fontSize: 17}}/>,
    settings: <TuneIcon sx={{fontSize: 17}}/>,
    'module-configs': <TuneIcon sx={{fontSize: 17}}/>,
    'system-configs': <DnsRoundedIcon sx={{fontSize: 17}}/>,
    'manage-users': <PeopleIcon sx={{fontSize: 17}}/>,
    'manage-roles': <ManageAccountsIcon sx={{fontSize: 17}}/>,
    'manage-perms': <SecurityIcon sx={{fontSize: 17}}/>,
}

export default function Sidebar({
                                    width = 240,
                                    collapsed,
                                    onCollapse,
                                    modules = [],
                                    configurations = [],
                                    reports = [],
                                    settings = [],
                                    activeTab,
                                    onSelect,
                                    themeMode = 'Light',
                                }) {
    const {hasAnyPermission, hasPermission} = useAuth()
    const {t} = useI18n()
    const isDark = themeMode === 'Dark'
    const securityItems = SECURITY_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const configurationItems = CONFIGURATION_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))

    const [openGroups, setOpenGroups] = useState({
        reconciliation: true,
        reports: false,
        settings: false,
        configurations: false,
        security: false,
    })

    const toggleGroup = (group) => {
        if (!collapsed) {
            setOpenGroups((prev) => ({
                ...prev,
                [group]: !prev[group],
            }))
        }
    }

    const handleItemClick = (id) => {
        onSelect(id)
    }

    const isItemActive = (id) => activeTab === id

    const renderGroup = (title, items, groupKey) => {
        const sectionIcon = SECTION_ICONS[groupKey]
        const sectionPerm = SECTION_PERMISSIONS[groupKey]
        const isOpen = openGroups[groupKey]
        const sectionColor = SECTION_COLORS[groupKey] || {
            icon: '#0F7C86',
            bg: '#F8FAFC',
        }

        if (Array.isArray(sectionPerm) && !hasAnyPermission(sectionPerm)) return null
        if (!Array.isArray(sectionPerm) && sectionPerm && !hasPermission(sectionPerm)) return null

        const visibleItems = items.filter((mod) => {
            const perm = ITEM_PERMISSIONS[mod.id]
            if (Array.isArray(perm)) return hasAnyPermission(perm)
            return !perm || hasPermission(perm)
        })

        if (visibleItems.length === 0) return null

        return (
            <Box key={groupKey}>
                <Tooltip
                    title={collapsed ? title : ''}
                    placement="right"
                    arrow
                >
                    <ListItemButton
                        onClick={() => toggleGroup(groupKey)}
                        sx={{
                            px: collapsed ? 0 : 1.75,
                            py: 1.1,
                            mx: collapsed ? 0.75 : 1.25,
                            mt: 0.4,
                            mb: 0.35,
                            minHeight: 42,
                            borderRadius: 2.5,
                            justifyContent: collapsed
                                ? 'center'
                                : 'flex-start',
                            backgroundColor: isOpen && !collapsed
                                ? (isDark ? '#111827' : sectionColor.bg)
                                : 'transparent',
                            transition: 'all 0.18s ease',
                            '&:hover': {
                                backgroundColor: isDark ? '#111827' : sectionColor.bg,
                            },
                        }}
                    >
                        <Box
                            sx={{
                                color: sectionColor.icon,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                minWidth: collapsed ? 'auto' : 28,
                                width: collapsed ? 28 : 'auto',
                                height: 28,
                                borderRadius: 2,
                                backgroundColor: collapsed
                                    ? (isDark ? '#111827' : sectionColor.bg)
                                    : 'transparent',
                                flexShrink: 0,
                            }}
                        >
                            {sectionIcon}
                        </Box>

                        {!collapsed && (
                            <>
                                <Typography
                                    sx={{
                                        fontWeight: 700,
                                        fontSize: '0.88rem',
                                        color: isDark ? '#F8FAFC' : '#0F172A',
                                        letterSpacing: '0.1px',
                                        flexGrow: 1,
                                    }}
                                >
                                    {title}
                                </Typography>

                                {isOpen ? (
                                    <ExpandLess
                                        sx={{
                                            color: isDark ? '#64748B' : '#94A3B8',
                                            fontSize: 18,
                                        }}
                                    />
                                ) : (
                                    <ExpandMore
                                        sx={{
                                            color: isDark ? '#64748B' : '#94A3B8',
                                            fontSize: 18,
                                        }}
                                    />
                                )}
                            </>
                        )}
                    </ListItemButton>
                </Tooltip>

                {!collapsed && (
                    <Collapse in={isOpen} timeout="auto" unmountOnExit>
                        <List component="div" disablePadding sx={{pt: 0.15}}>
                            {visibleItems.map((mod) => {
                                const isActive = isItemActive(mod.id)
                                const icon = ITEM_ICONS[mod.id]

                                return (
                                    <ListItemButton
                                        key={mod.id}
                                        selected={isActive}
                                        onClick={() => handleItemClick(mod.id)}
                                        sx={{
                                            pl: 2.25,
                                            pr: 1.5,
                                            py: 0.95,
                                            mx: 1.25,
                                            mb: 0.4,
                                            borderRadius: 2.5,
                                            minHeight: 40,
                                            transition: 'all 0.18s ease',
                                            '&.Mui-selected': {
                                                backgroundColor: isDark ? '#0F172A' : '#EFF6FF',
                                                border: isDark
                                                    ? '1px solid #1D4ED8'
                                                    : '1px solid #BFDBFE',
                                                boxShadow: isDark
                                                    ? '0 4px 12px rgba(0, 0, 0, 0.18)'
                                                    : '0 4px 12px rgba(37, 99, 235, 0.08)',
                                                '&:hover': {
                                                    backgroundColor: isDark ? '#0B1220' : '#DBEAFE',
                                                },
                                            },
                                            '&:hover:not(.Mui-selected)': {
                                                backgroundColor: isDark ? '#111827' : '#F8FAFC',
                                            },
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                color: isActive ? '#2563EB' : (isDark ? '#64748B' : '#94A3B8'),
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                mr: 1.5,
                                                width: 20,
                                                flexShrink: 0,
                                                transition: 'color 0.15s ease',
                                            }}
                                        >
                                            {icon}
                                        </Box>

                                        <ListItemText
                                            primary={
                                                mod.label ||
                                                getTabLabel(t, mod.id)
                                            }
                                            primaryTypographyProps={{
                                                fontSize: '0.86rem',
                                                fontWeight: isActive ? 700 : 500,
                                                color: isActive
                                                    ? '#2563EB'
                                                    : isDark
                                                        ? '#CBD5E1'
                                                        : '#334155',
                                                letterSpacing: '0.1px',
                                            }}
                                        />

                                        {isActive && (
                                            <Box
                                                sx={{
                                                    width: 7,
                                                    height: 7,
                                                    borderRadius: '50%',
                                                    backgroundColor: '#2563EB',
                                                    flexShrink: 0,
                                                    ml: 0.75,
                                                }}
                                            />
                                        )}
                                    </ListItemButton>
                                )
                            })}
                        </List>
                    </Collapse>
                )}

                {collapsed && (
                    <List component="div" disablePadding sx={{pt: 0.2}}>
                        {visibleItems.map((mod) => {
                            const isActive = isItemActive(mod.id)
                            const icon = ITEM_ICONS[mod.id]

                            return (
                                <Tooltip
                                    key={mod.id}
                                    title={mod.label || getTabLabel(t, mod.id)}
                                    placement="right"
                                    arrow
                                >
                                    <ListItemButton
                                        selected={isActive}
                                        onClick={() => handleItemClick(mod.id)}
                                        sx={{
                                            justifyContent: 'center',
                                            py: 1,
                                            mx: 0.75,
                                            mb: 0.35,
                                            minHeight: 40,
                                            borderRadius: 2.5,
                                            '&.Mui-selected': {
                                                backgroundColor: isDark ? '#0F172A' : '#EFF6FF',
                                                border: isDark
                                                    ? '1px solid #1D4ED8'
                                                    : '1px solid #BFDBFE',
                                            },
                                            '&:hover:not(.Mui-selected)': {
                                                backgroundColor: isDark ? '#111827' : '#F8FAFC',
                                            },
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                color: isActive ? '#2563EB' : (isDark ? '#64748B' : '#94A3B8'),
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                            }}
                                        >
                                            {icon}
                                        </Box>
                                    </ListItemButton>
                                </Tooltip>
                            )
                        })}
                    </List>
                )}

                <Divider
                    sx={{
                        mx: collapsed ? 1 : 1.75,
                        my: 0.65,
                        borderColor: isDark ? '#1E293B' : '#F1F5F9',
                    }}
                />
            </Box>
        )
    }

    return (
        <Drawer
            variant="permanent"
            sx={{
                width,
                flexShrink: 0,
                transition: 'width 0.2s ease',
                marginTop: '72px',
                height: 'calc(100% - 72px)',
                '& .MuiDrawer-paper': {
                    width,
                    transition: 'width 0.2s ease',
                    overflowX: 'hidden',
                    backgroundColor: isDark ? '#0F172A' : '#FFFFFF',
                    backgroundImage: 'none',
                    borderRight: isDark
                        ? '1px solid #1E293B'
                        : '1px solid #E2E8F0',
                    borderTop: 'none',
                    boxShadow: 'none',
                    display: 'flex',
                    flexDirection: 'column',
                    top: '72px',
                    height: 'calc(100% - 72px)',
                    clipPath: 'inset(0 0 0 0)',
                },
            }}
        >
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: collapsed ? 'center' : 'flex-end',
                    px: collapsed ? 0 : 1.25,
                    py: 0.9,
                    borderBottom: isDark
                        ? '1px solid #1E293B'
                        : '1px solid #F1F5F9',
                    backgroundColor: isDark ? '#111827' : '#FCFDFE',
                }}
            >
                <Tooltip
                    title={collapsed ? t('Expand sidebar') : t('Collapse sidebar')}
                    placement="right"
                    arrow
                >
                    <IconButton
                        size="small"
                        onClick={() => onCollapse((p) => !p)}
                        sx={{
                            width: 28,
                            height: 28,
                            color: isDark ? '#94A3B8' : '#94A3B8',
                            backgroundColor: isDark ? '#0F172A' : '#F8FAFC',
                            border: isDark
                                ? '1px solid #334155'
                                : '1px solid #E2E8F0',
                            '&:hover': {
                                backgroundColor: isDark ? '#1E293B' : '#EFF6FF',
                                color: '#2563EB',
                                borderColor: '#2563EB',
                            },
                        }}
                    >
                        {collapsed ? (
                            <ChevronRightIcon sx={{fontSize: 17}}/>
                        ) : (
                            <ChevronLeftIcon sx={{fontSize: 17}}/>
                        )}
                    </IconButton>
                </Tooltip>
            </Box>

            <List sx={{pt: 0.75, flexGrow: 1}}>
                {renderGroup(t('Reconciliation'), modules, 'reconciliation')}
                {renderGroup(t('Configurations'), configurations.length ? configurations : configurationItems, 'configurations')}
                {renderGroup(t('Reports'), reports, 'reports')}
                {renderGroup(t('Settings'), settings, 'settings')}
                {renderGroup(t('Security'), securityItems, 'security')}
            </List>
        </Drawer>
    )
}
