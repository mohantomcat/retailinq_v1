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
import NotificationsActiveRoundedIcon from '@mui/icons-material/NotificationsActiveRounded'
import AutorenewRoundedIcon from '@mui/icons-material/AutorenewRounded'
import BuildCircleRoundedIcon from '@mui/icons-material/BuildCircleRounded'
import SettingsSuggestIcon from '@mui/icons-material/SettingsSuggest'
import DnsRoundedIcon from '@mui/icons-material/DnsRounded'
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import PaletteOutlinedIcon from '@mui/icons-material/PaletteOutlined'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'
import {ACTIVITY_TAB_IDS, ALERT_TAB_IDS, CONFIGURATION_TAB_IDS, EXCEPTION_TAB_IDS, getTabLabel, INTEGRATION_TAB_IDS, OPERATIONS_TAB_IDS, REPORT_TAB_IDS, SECURITY_TAB_IDS, SLA_TAB_IDS} from '../constants/navigation'

const ITEM_PERMISSIONS = {
    reports: 'REPORTS_VIEW',
    'executive-scorecards': 'REPORTS_VIEW',
    'operations-command-center': 'REPORTS_VIEW',
    'store-scorecards': 'REPORTS_VIEW',
    'root-cause-analytics': 'REPORTS_VIEW',
    'recurrence-analytics': 'REPORTS_VIEW',
    settings: 'SETTINGS_VIEW',
    alerts: 'ALERT_VIEW',
    'exception-queues': 'EXCEPTION_QUEUE_VIEW',
    'store-manager-lite': 'EXCEPTION_QUEUE_VIEW',
    'regional-incident-board': 'EXCEPTION_QUEUE_VIEW',
    'noise-suppression': 'EXCEPTION_AUTOMATION_VIEW',
    'known-issues': 'EXCEPTION_QUEUE_VIEW',
    'ticketing-comms': 'EXCEPTION_QUEUE_VIEW',
    'approval-center': 'EXCEPTION_APPROVAL_VIEW',
    'routing-playbooks': 'EXCEPTION_AUTOMATION_VIEW',
    'sla-management': 'SLA_VIEW',
    operations: 'OPS_VIEW',
    'recon-jobs': 'OPS_VIEW',
    'audit-activity': 'AUDIT_VIEW',
    'manage-users': 'ADMIN_USERS',
    'manage-roles': 'ADMIN_ROLES',
    'manage-perms': 'ADMIN_PERMISSIONS',
    'org-hierarchy': 'ADMIN_ORG',
    'tenant-access': 'TENANT_ACCESS_MANAGE',
    'branding-center': 'BRANDING_MANAGE',
    'integration-hub': 'INTEGRATION_HUB_VIEW',
    'module-configs': 'CONFIG_MODULE_VIEW',
    'system-configs': 'CONFIG_SYSTEM_VIEW',
}

const SECTION_PERMISSIONS = {
    reports: 'REPORTS_VIEW',
    settings: 'SETTINGS_VIEW',
    alerts: 'ALERT_VIEW',
    exceptions: 'EXCEPTION_QUEUE_VIEW',
    sla: 'SLA_VIEW',
    integration: 'INTEGRATION_HUB_VIEW',
    operations: 'OPS_VIEW',
    activity: 'AUDIT_VIEW',
    configurations: ['CONFIG_MODULE_VIEW', 'CONFIG_SYSTEM_VIEW'],
    security: ['ADMIN_USERS', 'ADMIN_ROLES', 'ADMIN_PERMISSIONS', 'ADMIN_ORG', 'TENANT_ACCESS_MANAGE', 'API_ACCESS_MANAGE', 'BRANDING_MANAGE'],
}

const SECTION_ICONS = {
    reconciliation: <CompareArrowsIcon sx={{fontSize: 18}}/>,
    reports: <AssessmentIcon sx={{fontSize: 18}}/>,
    settings: <SettingsIcon sx={{fontSize: 18}}/>,
    alerts: <NotificationsActiveRoundedIcon sx={{fontSize: 18}}/>,
    exceptions: <FactCheckOutlinedIcon sx={{fontSize: 18}}/>,
    sla: <TimerOutlinedIcon sx={{fontSize: 18}}/>,
    integration: <HubOutlinedIcon sx={{fontSize: 18}}/>,
    operations: <BuildCircleRoundedIcon sx={{fontSize: 18}}/>,
    activity: <AssessmentIcon sx={{fontSize: 18}}/>,
    configurations: <SettingsSuggestIcon sx={{fontSize: 18}}/>,
    security: <AdminPanelSettingsIcon sx={{fontSize: 18}}/>,
}

const SECTION_COLORS = {
    reconciliation: {
        icon: 'var(--brand-primary)',
        bg: 'var(--brand-primary-surface)',
    },
    reports: {
        icon: 'var(--brand-primary)',
        bg: 'var(--brand-primary-surface)',
    },
    settings: {
        icon: '#D97706',
        bg: '#FFF7ED',
    },
    alerts: {
        icon: '#DC2626',
        bg: '#FEF2F2',
    },
    exceptions: {
        icon: 'var(--brand-primary)',
        bg: 'var(--brand-primary-surface)',
    },
    sla: {
        icon: 'var(--brand-primary)',
        bg: 'var(--brand-primary-surface)',
    },
    integration: {
        icon: 'var(--brand-secondary)',
        bg: 'var(--brand-secondary-surface)',
    },
    operations: {
        icon: 'var(--brand-secondary)',
        bg: 'var(--brand-secondary-surface)',
    },
    activity: {
        icon: 'var(--brand-primary)',
        bg: 'var(--brand-primary-surface)',
    },
    configurations: {
        icon: 'var(--brand-primary)',
        bg: 'var(--brand-primary-surface)',
    },
    security: {
        icon: 'var(--brand-primary)',
        bg: 'var(--brand-primary-surface)',
    },
}

const ITEM_ICONS = {
    'xstore-sim': <SyncAltIcon sx={{fontSize: 17}}/>,
    'xstore-siocs': <SyncAltIcon sx={{fontSize: 17}}/>,
    'xstore-xocs': <StorefrontIcon sx={{fontSize: 17}}/>,
    'siocs-mfcs': <InventoryIcon sx={{fontSize: 17}}/>,
    'sim-rms': <InventoryIcon sx={{fontSize: 17}}/>,
    'sim-mfcs': <InventoryIcon sx={{fontSize: 17}}/>,
    'siocs-rms': <InventoryIcon sx={{fontSize: 17}}/>,
    reports: <BarChartIcon sx={{fontSize: 17}}/>,
    'executive-scorecards': <AssessmentIcon sx={{fontSize: 17}}/>,
    'operations-command-center': <BuildCircleRoundedIcon sx={{fontSize: 17}}/>,
    'store-scorecards': <BarChartIcon sx={{fontSize: 17}}/>,
    'root-cause-analytics': <FactCheckOutlinedIcon sx={{fontSize: 17}}/>,
    'recurrence-analytics': <AutorenewRoundedIcon sx={{fontSize: 17}}/>,
    settings: <TuneIcon sx={{fontSize: 17}}/>,
    alerts: <NotificationsActiveRoundedIcon sx={{fontSize: 17}}/>,
    'exception-queues': <FactCheckOutlinedIcon sx={{fontSize: 17}}/>,
    'store-manager-lite': <StorefrontIcon sx={{fontSize: 17}}/>,
    'regional-incident-board': <BarChartIcon sx={{fontSize: 17}}/>,
    'noise-suppression': <AutorenewRoundedIcon sx={{fontSize: 17}}/>,
    'known-issues': <FactCheckOutlinedIcon sx={{fontSize: 17}}/>,
    'ticketing-comms': <BuildCircleRoundedIcon sx={{fontSize: 17}}/>,
    'approval-center': <FactCheckOutlinedIcon sx={{fontSize: 17}}/>,
    'routing-playbooks': <FactCheckOutlinedIcon sx={{fontSize: 17}}/>,
    'sla-management': <TimerOutlinedIcon sx={{fontSize: 17}}/>,
    'integration-hub': <HubOutlinedIcon sx={{fontSize: 17}}/>,
    operations: <BuildCircleRoundedIcon sx={{fontSize: 17}}/>,
    'recon-jobs': <AutorenewRoundedIcon sx={{fontSize: 17}}/>,
    'audit-activity': <AssessmentIcon sx={{fontSize: 17}}/>,
    'module-configs': <TuneIcon sx={{fontSize: 17}}/>,
    'system-configs': <DnsRoundedIcon sx={{fontSize: 17}}/>,
    'manage-users': <PeopleIcon sx={{fontSize: 17}}/>,
    'manage-roles': <ManageAccountsIcon sx={{fontSize: 17}}/>,
    'manage-perms': <SecurityIcon sx={{fontSize: 17}}/>,
    'org-hierarchy': <StorefrontIcon sx={{fontSize: 17}}/>,
    'tenant-access': <AdminPanelSettingsIcon sx={{fontSize: 17}}/>,
    'branding-center': <PaletteOutlinedIcon sx={{fontSize: 17}}/>,
}

export default function Sidebar({
                                    width = 240,
                                    collapsed,
                                    onCollapse,
                                    modules = [],
                                    alerts = [],
                                    exceptionItems = [],
                                    slaItems = [],
                                    integration = [],
                                    operations = [],
                                    activity = [],
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
    const alertItems = ALERT_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const exceptionTabs = EXCEPTION_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const integrationItems = INTEGRATION_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const operationItems = OPERATIONS_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const slaTabs = SLA_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const activityItems = ACTIVITY_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const configurationItems = CONFIGURATION_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))
    const reportItems = REPORT_TAB_IDS.map((id) => ({
        id,
        label: getTabLabel(t, id),
    }))

    const [openGroups, setOpenGroups] = useState({
        reconciliation: true,
        alerts: false,
        exceptions: false,
        sla: false,
        integration: false,
        operations: false,
        activity: false,
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
                                const icon = ITEM_ICONS[mod.id] || <CompareArrowsIcon sx={{fontSize: 17}}/>

                                return (
                                    <ListItemButton
                                        key={mod.id}
                                        data-testid={`sidebar-item-${mod.id}`}
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
                                                backgroundColor: isDark ? '#0F172A' : '#FFFFFF',
                                                border: isDark
                                                    ? '1px solid var(--brand-primary)'
                                                    : '1px solid var(--brand-primary-border)',
                                                boxShadow: isDark
                                                    ? '0 4px 12px rgba(0, 0, 0, 0.18)'
                                                    : '0 10px 22px rgba(34,49,77,0.08)',
                                                '&:hover': {
                                                    backgroundColor: isDark ? '#0B1220' : '#FFFFFF',
                                                },
                                            },
                                            '&:hover:not(.Mui-selected)': {
                                                backgroundColor: isDark ? '#111827' : '#F7F9FD',
                                            },
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                color: isActive ? 'var(--brand-primary)' : (isDark ? '#64748B' : '#8A97AE'),
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
                                                    ? 'var(--brand-primary)'
                                                    : isDark
                                                        ? '#CBD5E1'
                                                        : '#4A566E',
                                                letterSpacing: '0.1px',
                                            }}
                                        />

                                        {isActive && (
                                            <Box
                                                sx={{
                                                    width: 7,
                                                    height: 7,
                                                    borderRadius: '50%',
                                                    backgroundColor: 'var(--brand-primary)',
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
                            const icon = ITEM_ICONS[mod.id] || <CompareArrowsIcon sx={{fontSize: 17}}/>

                            return (
                                <Tooltip
                                    key={mod.id}
                                    title={mod.label || getTabLabel(t, mod.id)}
                                    placement="right"
                                    arrow
                                >
                                    <ListItemButton
                                        data-testid={`sidebar-item-${mod.id}`}
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
                                                backgroundColor: isDark ? '#0F172A' : '#FFFFFF',
                                                border: isDark
                                                    ? '1px solid var(--brand-primary)'
                                                    : '1px solid var(--brand-primary-border)',
                                            },
                                            '&:hover:not(.Mui-selected)': {
                                                backgroundColor: isDark ? '#111827' : '#F7F9FD',
                                            },
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                color: isActive ? 'var(--brand-primary)' : (isDark ? '#64748B' : '#8A97AE'),
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
                    backgroundColor: isDark ? '#0F172A' : '#FBFCFF',
                    backgroundImage: 'none',
                    borderRight: isDark
                        ? '1px solid #1E293B'
                        : '1px solid #DCE3EF',
                    borderTop: 'none',
                    boxShadow: isDark ? 'none' : '8px 0 28px rgba(34,49,77,0.06)',
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
                        : '1px solid #E8EDF5',
                    backgroundColor: isDark ? '#111827' : '#F8FAFD',
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
                            color: isDark ? '#94A3B8' : '#7B88A2',
                            backgroundColor: isDark ? '#0F172A' : '#FFFFFF',
                            border: isDark
                                ? '1px solid #334155'
                                : '1px solid #D9E2EF',
                            '&:hover': {
                                backgroundColor: isDark
                                    ? 'rgba(var(--brand-primary-rgb), 0.18)'
                                    : 'var(--brand-primary-surface)',
                                color: 'var(--brand-primary)',
                                borderColor: 'var(--brand-primary)',
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
                {renderGroup(t('Alerts'), alerts.length ? alerts : alertItems, 'alerts')}
                {renderGroup(t('Exceptions'), exceptionItems.length ? exceptionItems : exceptionTabs, 'exceptions')}
                {renderGroup(t('SLA & Aging'), slaItems.length ? slaItems : slaTabs, 'sla')}
                {renderGroup(t('Integration Hub'), integration.length ? integration : integrationItems, 'integration')}
                {renderGroup(t('Operations'), operations.length ? operations : operationItems, 'operations')}
                {renderGroup(t('Audit & Activity'), activity.length ? activity : activityItems, 'activity')}
                {renderGroup(t('Configurations'), configurations.length ? configurations : configurationItems, 'configurations')}
                {renderGroup(t('Reports'), reports.length ? reports : reportItems, 'reports')}
                {renderGroup(t('Settings'), settings, 'settings')}
                {renderGroup(t('Security'), securityItems, 'security')}
            </List>
        </Drawer>
    )
}
