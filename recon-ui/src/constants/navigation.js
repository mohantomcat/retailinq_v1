export const RECONCILIATION_TAB_IDS = [
    'xstore-sim',
    'xstore-siocs',
    'xstore-xocs',
    'sim-rms',
]

export const SECURITY_TAB_IDS = [
    'manage-users',
    'manage-roles',
    'manage-perms',
]

export const ALERT_TAB_IDS = [
    'alerts',
]

export const OPERATIONS_TAB_IDS = [
    'operations',
]

export const ACTIVITY_TAB_IDS = [
    'audit-activity',
]

export const CONFIGURATION_TAB_IDS = [
    'module-configs',
    'system-configs',
]

export function getTabLabel(t, id) {
    switch (id) {
        case 'xstore-sim':
            return t('Xstore vs SIM')
        case 'xstore-siocs':
            return t('Xstore vs SIOCS')
        case 'xstore-xocs':
            return t('Xstore vs XOCS')
        case 'sim-rms':
            return t('SIM vs RMS')
        case 'manage-users':
            return t('Manage Users')
        case 'manage-roles':
            return t('Manage Roles')
        case 'manage-perms':
            return t('Manage Permissions')
        case 'alerts':
            return t('Alerts')
        case 'operations':
            return t('Operations')
        case 'audit-activity':
            return t('Audit & Activity')
        case 'module-configs':
            return t('Module Configurations')
        case 'system-configs':
            return t('System Configurations')
        default:
            return id
    }
}
